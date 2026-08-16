package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.github.tvbox.osc.bean.EpgData;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.bean.EpgProgram;
import com.github.tvbox.osc.bean.XmlTv;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EPG 数据管理器
 * 支持：
 * 1. 大文件 EPG 分批解析入库（避免 OOM）
 * 2. 数据库缓存查询
 * 3. XMLTV / JSON 格式解析
 * 4. epg_data.json 频道映射
 */
public class EpgDataManager {
    private static final String TAG = "EpgDataManager";
    private static EpgDataManager instance;
    private EpgDatabaseHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private EpgDataManager(Context context) {
        dbHelper = new EpgDatabaseHelper(context);
    }

    public static synchronized EpgDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new EpgDataManager(context.getApplicationContext());
        }
        return instance;
    }

    public interface EpgDataCallback {
        void onSuccess(List<Epginfo> epgList);
        void onError(String msg);
    }

    /**
     * 从数据库获取 EPG 列表
     */
    public List<Epginfo> getEpgList(String channelName) {
        if (TextUtils.isEmpty(channelName)) return new ArrayList<>();
        try {
            // 先用精确匹配
            List<Epginfo> list = dbHelper.getEpgByChannel(channelName);
            if (list != null && !list.isEmpty()) return list;

            // 再用模糊匹配（通过 epg_data.json 映射）
            String mappedName = EpgNameFuzzyMatch.getInstance().getMappedName(channelName);
            if (!TextUtils.isEmpty(mappedName) && !mappedName.equals(channelName)) {
                list = dbHelper.getEpgByChannel(mappedName);
                if (list != null && !list.isEmpty()) return list;
            }

            return new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "getEpgList error", e);
            return new ArrayList<>();
        }
    }

    /**
     * 加载 EPG 数据（支持大文件分批处理）
     */
    public void loadEpgData(String epgUrl, EpgDataCallback callback) {
        executor.execute(() -> {
            try {
                if (TextUtils.isEmpty(epgUrl) || "默认".equals(epgUrl)) {
                    mainHandler.post(() -> callback.onError("EPG URL 为空"));
                    return;
                }

                // 检查缓存
                String cacheKey = "epg_cache_" + Math.abs(epgUrl.hashCode());
                long lastUpdate = Hawk.get(cacheKey + "_time", 0L);
                long now = System.currentTimeMillis();
                // 缓存 2 小时
                if (now - lastUpdate < 2 * 60 * 60 * 1000) {
                    List<Epginfo> cached = dbHelper.getAllEpg();
                    if (cached != null && !cached.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(cached));
                        return;
                    }
                }

                // 下载并解析
                String content = downloadEpgContent(epgUrl);
                if (TextUtils.isEmpty(content)) {
                    mainHandler.post(() -> callback.onError("下载失败"));
                    return;
                }

                // 判断格式并解析
                if (content.trim().startsWith("<") || content.trim().startsWith("<?xml")) {
                    parseXmlEpg(content);
                } else if (content.trim().startsWith("[") || content.trim().startsWith("{")) {
                    parseJsonEpg(content);
                }

                Hawk.put(cacheKey + "_time", now);
                List<Epginfo> result = dbHelper.getAllEpg();
                mainHandler.post(() -> callback.onSuccess(result));

            } catch (Exception e) {
                Log.e(TAG, "loadEpgData error", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * 下载 EPG 内容（支持大文件流式下载）
     */
    private String downloadEpgContent(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) return null;

            // 大文件（>10MB）使用流式解析，不全部读入内存
            long contentLength = conn.getContentLength();
            if (contentLength > 10 * 1024 * 1024) {
                // 大文件：流式解析直接入库
                streamParseEpg(conn.getInputStream(), urlStr.endsWith(".json"));
                return "STREAM_PARSED"; // 标记已流式解析
            }

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "downloadEpgContent error", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 流式解析大文件 EPG（避免 OOM）
     */
    private void streamParseEpg(InputStream inputStream, boolean isJson) {
        try {
            dbHelper.clearAllEpg();
            if (isJson) {
                streamParseJsonEpg(inputStream);
            } else {
                streamParseXmlEpg(inputStream);
            }
        } catch (Exception e) {
            Log.e(TAG, "streamParseEpg error", e);
        }
    }

    /**
     * 流式解析 JSON EPG（使用 Gson 流式 API）
     */
    private void streamParseJsonEpg(InputStream inputStream) {
        try {
            com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            reader.beginArray();
            List<Epginfo> batch = new ArrayList<>();
            int batchSize = 500;

            while (reader.hasNext()) {
                EpgData epgData = gson.fromJson(reader, EpgData.class);
                if (epgData != null && epgData.getName() != null) {
                    Epginfo epginfo = new Epginfo();
                    epginfo.setName(epgData.getName());
                    if (epgData.getProgram() != null) {
                        epginfo.setProgram(epgData.getProgram());
                    }
                    batch.add(epginfo);

                    if (batch.size() >= batchSize) {
                        dbHelper.insertEpgBatch(batch);
                        batch.clear();
                    }
                }
            }
            reader.endArray();
            reader.close();

            if (!batch.isEmpty()) {
                dbHelper.insertEpgBatch(batch);
            }
        } catch (Exception e) {
            Log.e(TAG, "streamParseJsonEpg error", e);
        }
    }

    /**
     * 流式解析 XML EPG
     */
    private void streamParseXmlEpg(InputStream inputStream) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(inputStream, "UTF-8");

            List<Epginfo> batch = new ArrayList<>();
            int batchSize = 500;
            Epginfo currentEpg = null;
            List<EpgProgram> currentPrograms = null;
            String currentChannelName = null;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("channel".equals(tagName)) {
                            currentChannelName = parser.getAttributeValue(null, "id");
                        } else if ("display-name".equals(tagName) && currentChannelName != null) {
                            // 频道名称
                        } else if ("programme".equals(tagName)) {
                            if (currentEpg == null || !currentChannelName.equals(currentEpg.getName())) {
                                if (currentEpg != null) {
                                    currentEpg.setProgram(currentPrograms);
                                    batch.add(currentEpg);
                                    if (batch.size() >= batchSize) {
                                        dbHelper.insertEpgBatch(batch);
                                        batch.clear();
                                    }
                                }
                                currentEpg = new Epginfo();
                                currentEpg.setName(currentChannelName);
                                currentPrograms = new ArrayList<>();
                            }
                            String start = parser.getAttributeValue(null, "start");
                            String stop = parser.getAttributeValue(null, "stop");
                            EpgProgram program = new EpgProgram();
                            program.setStartTime(start);
                            program.setEndTime(stop);
                            currentPrograms.add(program);
                        } else if ("title".equals(tagName) && currentPrograms != null && !currentPrograms.isEmpty()) {
                            // 节目名称
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if ("tv".equals(tagName) && currentEpg != null) {
                            currentEpg.setProgram(currentPrograms);
                            batch.add(currentEpg);
                        }
                        break;
                }
                eventType = parser.next();
            }

            if (currentEpg != null) {
                currentEpg.setProgram(currentPrograms);
                batch.add(currentEpg);
            }
            if (!batch.isEmpty()) {
                dbHelper.insertEpgBatch(batch);
            }
        } catch (Exception e) {
            Log.e(TAG, "streamParseXmlEpg error", e);
        }
    }

    /**
     * 解析 XML EPG（小文件）
     */
    private void parseXmlEpg(String xmlContent) {
        try {
            dbHelper.clearAllEpg();
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xmlContent));

            List<Epginfo> batch = new ArrayList<>();
            int batchSize = 500;
            Epginfo currentEpg = null;
            List<EpgProgram> currentPrograms = null;
            String currentChannelName = null;
            String currentTitle = null;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("channel".equals(tagName)) {
                            currentChannelName = parser.getAttributeValue(null, "id");
                        } else if ("programme".equals(tagName)) {
                            if (currentEpg == null || currentPrograms == null) {
                                currentEpg = new Epginfo();
                                currentEpg.setName(currentChannelName);
                                currentPrograms = new ArrayList<>();
                            }
                            String start = parser.getAttributeValue(null, "start");
                            String stop = parser.getAttributeValue(null, "stop");
                            EpgProgram program = new EpgProgram();
                            program.setStartTime(start);
                            program.setEndTime(stop);
                            currentPrograms.add(program);
                        } else if ("title".equals(tagName) && currentPrograms != null && !currentPrograms.isEmpty()) {
                            currentTitle = parser.nextText();
                            currentPrograms.get(currentPrograms.size() - 1).setTitle(currentTitle);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if ("channel".equals(tagName) && currentEpg != null) {
                            currentEpg.setProgram(currentPrograms);
                            batch.add(currentEpg);
                            if (batch.size() >= batchSize) {
                                dbHelper.insertEpgBatch(batch);
                                batch.clear();
                            }
                            currentEpg = null;
                            currentPrograms = null;
                        }
                        break;
                }
                eventType = parser.next();
            }

            if (currentEpg != null) {
                currentEpg.setProgram(currentPrograms);
                batch.add(currentEpg);
            }
            if (!batch.isEmpty()) {
                dbHelper.insertEpgBatch(batch);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseXmlEpg error", e);
        }
    }

    /**
     * 解析 JSON EPG（小文件）
     */
    private void parseJsonEpg(String jsonContent) {
        try {
            dbHelper.clearAllEpg();
            List<EpgData> epgDataList = gson.fromJson(jsonContent, new TypeToken<List<EpgData>>(){}.getType());
            if (epgDataList == null) return;

            List<Epginfo> batch = new ArrayList<>();
            int batchSize = 500;
            for (EpgData data : epgDataList) {
                if (data == null || data.getName() == null) continue;
                Epginfo epginfo = new Epginfo();
                epginfo.setName(data.getName());
                epginfo.setProgram(data.getProgram());
                batch.add(epginfo);

                if (batch.size() >= batchSize) {
                    dbHelper.insertEpgBatch(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                dbHelper.insertEpgBatch(batch);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseJsonEpg error", e);
        }
    }

    public void clearCache() {
        executor.execute(() -> dbHelper.clearAllEpg());
    }
}

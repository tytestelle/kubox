package com.github.tvbox.osc.util;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.github.tvbox.osc.bean.EpgData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * epg_data.json 解析器
 * 负责解析频道映射数据，供 EPG 模糊匹配和台标查找使用
 */
public class EpgDataJsonParser {
    private static final String TAG = "EpgDataJsonParser";
    private static EpgDataJsonParser instance;
    private final Map<String, EpgData> channelMap = new HashMap<>();
    private final Gson gson = new Gson();
    private boolean parsed = false;

    private EpgDataJsonParser() {}

    public static synchronized EpgDataJsonParser getInstance() {
        if (instance == null) {
            instance = new EpgDataJsonParser();
        }
        return instance;
    }

    /**
     * 加载 epg_data.json
     * 优先级：assets > 本地文件 > 网络下载
     */
    public void loadEpgDataJson(Context context) {
        if (parsed) return;
        try {
            String json = null;
            // 1. 从 assets 读取
            try {
                InputStream is = context.getAssets().open("epg_data.json");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                json = sb.toString();
            } catch (Exception e) {
                Log.d(TAG, "assets epg_data.json not found");
            }

            // 2. 从本地文件读取
            if (TextUtils.isEmpty(json)) {
                File localFile = new File(context.getFilesDir(), "epg_data.json");
                if (localFile.exists()) {
                    FileInputStream fis = new FileInputStream(localFile);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    json = sb.toString();
                }
            }

            // 3. 从网络下载
            if (TextUtils.isEmpty(json)) {
                json = downloadFromNetwork();
                if (!TextUtils.isEmpty(json)) {
                    File localFile = new File(context.getFilesDir(), "epg_data.json");
                    FileOutputStream fos = new FileOutputStream(localFile);
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                    fos.close();
                }
            }

            if (!TextUtils.isEmpty(json)) {
                parseJson(json);
                parsed = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "loadEpgDataJson error", e);
        }
    }

    private void parseJson(String json) {
        try {
            List<EpgData> dataList = gson.fromJson(json, new TypeToken<List<EpgData>>(){}.getType());
            if (dataList == null) return;
            synchronized (channelMap) {
                for (EpgData data : dataList) {
                    if (data == null || TextUtils.isEmpty(data.getName())) continue;
                    channelMap.put(data.getName(), data);
                    // 别名也加入映射
                    if (data.getAlias() != null) {
                        for (String alias : data.getAlias()) {
                            if (!TextUtils.isEmpty(alias)) {
                                channelMap.put(alias, data);
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "Parsed epg_data.json, channel count: " + channelMap.size());
        } catch (Exception e) {
            Log.e(TAG, "parseJson error", e);
        }
    }

    /**
     * 根据频道名获取 EpgData
     */
    public EpgData getChannelData(String channelName) {
        if (TextUtils.isEmpty(channelName)) return null;
        synchronized (channelMap) {
            EpgData data = channelMap.get(channelName);
            if (data != null) return data;
            // 模糊匹配
            for (Map.Entry<String, EpgData> entry : channelMap.entrySet()) {
                if (channelName.contains(entry.getKey()) || entry.getKey().contains(channelName)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 获取频道映射名称（用于 EPG 查询）
     */
    public String getMappedChannelName(String channelName) {
        EpgData data = getChannelData(channelName);
        return data != null ? data.getName() : null;
    }

    public Map<String, EpgData> getAllChannelMap() {
        synchronized (channelMap) {
            return new HashMap<>(channelMap);
        }
    }

    private String downloadFromNetwork() {
        String[] urls = {
            "https://raw.githubusercontent.com/tytestelle/witv_flutter/main/assets/epg_data.json",
            "https://ghproxy.com/https://raw.githubusercontent.com/tytestelle/witv_flutter/main/assets/epg_data.json",
            "https://mirror.ghproxy.com/https://raw.githubusercontent.com/tytestelle/witv_flutter/main/assets/epg_data.json"
        };
        for (String urlStr : urls) {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                conn.disconnect();
                return sb.toString();
            } catch (Exception e) {
                Log.e(TAG, "downloadFromNetwork failed: " + urlStr);
            }
        }
        return null;
    }
}

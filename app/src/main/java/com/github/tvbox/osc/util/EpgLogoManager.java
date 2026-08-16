package com.github.tvbox.osc.util;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.github.tvbox.osc.bean.EpgData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EpgLogoManager {
    private static final String TAG = "EpgLogoManager";
    private static EpgLogoManager instance;
    private final Map<String, String> logoMap = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();
    private boolean loaded = false;

    private EpgLogoManager(Context context) {
        loadLocalCache(context);
    }

    public static synchronized EpgLogoManager getInstance(Context context) {
        if (instance == null) {
            instance = new EpgLogoManager(context.getApplicationContext());
        }
        return instance;
    }

    public void loadLogoDataFromJson(Context context) {
        if (loaded) return;
        executor.execute(() -> {
            try {
                String json = readAssetFile(context, "epg_data.json");
                if (TextUtils.isEmpty(json)) {
                    json = downloadEpgDataJson();
                    if (!TextUtils.isEmpty(json)) {
                        saveToLocal(context, json);
                    }
                }
                if (!TextUtils.isEmpty(json)) {
                    parseLogoData(json);
                    loaded = true;
                    Log.d(TAG, "Logo data loaded, count: " + logoMap.size());
                }
            } catch (Exception e) {
                Log.e(TAG, "loadLogoDataFromJson error", e);
            }
        });
    }

    private void parseLogoData(String json) {
        try {
            List<EpgData> dataList = gson.fromJson(json, new TypeToken<List<EpgData>>(){}.getType());
            if (dataList == null) return;
            synchronized (logoMap) {
                for (EpgData data : dataList) {
                    if (data == null || TextUtils.isEmpty(data.getName())) continue;
                    if (!TextUtils.isEmpty(data.getLogo())) {
                        logoMap.put(data.getName(), data.getLogo());
                        if (data.getAlias() != null) {
                            for (String alias : data.getAlias()) {
                                if (!TextUtils.isEmpty(alias)) {
                                    logoMap.put(alias, data.getLogo());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseLogoData error", e);
        }
    }

    public String getLogoUrl(String channelName) {
        if (TextUtils.isEmpty(channelName)) return null;
        synchronized (logoMap) {
            String url = logoMap.get(channelName);
            if (!TextUtils.isEmpty(url)) return url;
            for (Map.Entry<String, String> entry : logoMap.entrySet()) {
                if (channelName.contains(entry.getKey()) || entry.getKey().contains(channelName)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public void downloadLogo(Context context, String channelName, String logoUrl) {
        if (TextUtils.isEmpty(logoUrl)) return;
        executor.execute(() -> {
            try {
                File cacheDir = new File(context.getCacheDir(), "epg_logos");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File logoFile = new File(cacheDir, channelName.hashCode() + ".png");
                if (logoFile.exists()) return;
                URL url = new URL(logoUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(logoFile);
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                is.close();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "downloadLogo error: " + logoUrl, e);
            }
        });
    }

    public File getLocalLogoFile(Context context, String channelName) {
        File cacheDir = new File(context.getCacheDir(), "epg_logos");
        File logoFile = new File(cacheDir, channelName.hashCode() + ".png");
        return logoFile.exists() ? logoFile : null;
    }

    private String readAssetFile(Context context, String fileName) {
        try {
            InputStream is = context.getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String downloadEpgDataJson() {
        String[] urls = {
            "https://raw.githubusercontent.com/tytestelle/witv_flutter/main/assets/epg_data.json",
            "https://ghproxy.com/https://raw.githubusercontent.com/tytestelle/witv_flutter/main/assets/epg_data.json"
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
                Log.e(TAG, "downloadEpgDataJson failed: " + urlStr, e);
            }
        }
        return null;
    }

    private void saveToLocal(Context context, String json) {
        try {
            File file = new File(context.getFilesDir(), "epg_data.json");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "saveToLocal error", e);
        }
    }

    private void loadLocalCache(Context context) {
        try {
            File file = new File(context.getFilesDir(), "epg_data.json");
            if (file.exists()) {
                InputStream is = new java.io.FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                parseLogoData(sb.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "loadLocalCache error", e);
        }
    }
}

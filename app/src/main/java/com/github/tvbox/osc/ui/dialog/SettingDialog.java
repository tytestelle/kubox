package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置弹窗修复类（适配酷9反编译项目）
 * 修复：直播订阅、EPG订阅点击无响应
 */
public class SettingDialog extends BaseDialog {

    private TextView tvLiveSub;
    private TextView tvEpgSub;
    private TextView tvLiveClear;
    private TextView tvEpgClear;
    private TextView tvLiveStatus;
    private TextView tvEpgStatus;

    public SettingDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_setting);
        initView();
        initData();
        initListener();
    }

    private void initView() {
        tvLiveSub = findViewById(R.id.tvLiveSub);
        tvEpgSub = findViewById(R.id.tvEpgSub);
        tvLiveClear = findViewById(R.id.tvLiveClear);
        tvEpgClear = findViewById(R.id.tvEpgClear);
        tvLiveStatus = findViewById(R.id.tvLiveStatus);
        tvEpgStatus = findViewById(R.id.tvEpgStatus);
    }

    private void initData() {
        String liveUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        String epgUrl = Hawk.get(HawkConfig.EPG_URL, "");

        if (tvLiveStatus != null) {
            tvLiveStatus.setText(liveUrl.isEmpty() ? "未配置" : "已配置");
        }
        if (tvEpgStatus != null) {
            tvEpgStatus.setText(epgUrl.isEmpty() ? "未配置" : "已配置");
        }
    }

    private void initListener() {
        if (tvLiveSub != null) {
            tvLiveSub.setOnClickListener(v -> showLiveSubDialog());
        }

        if (tvEpgSub != null) {
            tvEpgSub.setOnClickListener(v -> showEpgSubDialog());
        }

        if (tvLiveClear != null) {
            tvLiveClear.setOnClickListener(v -> {
                Hawk.put(HawkConfig.LIVE_API_URL, "");
                Hawk.put(HawkConfig.LIVE_API_HISTORY, new ArrayList<>());
                Toast.makeText(getContext(), "直播订阅已清除，重启播放生效", Toast.LENGTH_SHORT).show();
                initData();
                notifyLiveRefresh();
            });
        }

        if (tvEpgClear != null) {
            tvEpgClear.setOnClickListener(v -> {
                Hawk.put(HawkConfig.EPG_URL, "");
                Hawk.put(HawkConfig.EPG_HISTORY, new ArrayList<>());
                Toast.makeText(getContext(), "EPG订阅已清除", Toast.LENGTH_SHORT).show();
                initData();
                notifyEpgRefresh();
            });
        }
    }

    private void showLiveSubDialog() {
        String current = Hawk.get(HawkConfig.LIVE_API_URL, "");
        new InputDialog(getContext())
                .setTitle("直播订阅地址")
                .setHint("请输入直播源订阅链接 (支持m3u/txt/json)...")
                .setDefaultText(current)
                .setOnConfirmListener(text -> {
                    if (text == null || text.trim().isEmpty()) {
                        Toast.makeText(getContext(), "地址不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String url = text.trim();
                    Hawk.put(HawkConfig.LIVE_API_URL, url);
                    saveToHistory(HawkConfig.LIVE_API_HISTORY, url);
                    Toast.makeText(getContext(), "直播订阅已保存，重启播放生效", Toast.LENGTH_LONG).show();
                    initData();
                    notifyLiveRefresh();
                })
                .show();
    }

    private void showEpgSubDialog() {
        String current = Hawk.get(HawkConfig.EPG_URL, "");
        new InputDialog(getContext())
                .setTitle("EPG节目单地址")
                .setHint("请输入EPG订阅链接 (XML/JSON格式)...")
                .setDefaultText(current)
                .setOnConfirmListener(text -> {
                    if (text == null || text.trim().isEmpty()) {
                        Toast.makeText(getContext(), "地址不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String url = text.trim();
                    Hawk.put(HawkConfig.EPG_URL, url);
                    saveToHistory(HawkConfig.EPG_HISTORY, url);
                    Toast.makeText(getContext(), "EPG订阅已保存", Toast.LENGTH_LONG).show();
                    initData();
                    notifyEpgRefresh();
                })
                .show();
    }

    private void saveToHistory(String key, String url) {
        try {
            List<String> history = Hawk.get(key, new ArrayList<>());
            if (history == null) history = new ArrayList<>();
            history.remove(url);
            history.add(0, url);
            if (history.size() > 10) {
                history = history.subList(0, 10);
            }
            Hawk.put(key, history);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyLiveRefresh() {
        try {
            android.content.Intent intent = new android.content.Intent("com.github.tvbox.osc.LIVE_REFRESH");
            getContext().sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyEpgRefresh() {
        try {
            android.content.Intent intent = new android.content.Intent("com.github.tvbox.osc.EPG_REFRESH");
            getContext().sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

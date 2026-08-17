package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Ku9-style live settings popup.
 * It deliberately uses a real Dialog rather than a view placed above the player
 * SurfaceView. This is important on TV devices where SurfaceView can cover normal
 * child views.
 */
public class Ku9LiveSettingDialog extends BaseDialog {
    public interface Callback { void onLiveChanged(); void onEpgChanged(); }

    private final Callback callback;
    private TvRecyclerView groupView, itemView;
    private LiveSettingGroupAdapter groupAdapter;
    private LiveSettingItemAdapter itemAdapter;
    private final List<LiveSettingGroup> groups = new ArrayList<>();

    public Ku9LiveSettingDialog(@NonNull Context context, Callback callback) {
        super(context);
        this.callback = callback;
        setContentView(R.layout.dialog_live_setting_ku9);
        init();
    }

    private void init() {
        groupView = findViewById(R.id.ku9SettingGroupView);
        itemView = findViewById(R.id.ku9SettingItemView);
        groupView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));
        itemView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));
        groupAdapter = new LiveSettingGroupAdapter();
        itemAdapter = new LiveSettingItemAdapter();
        groupView.setAdapter(groupAdapter);
        itemView.setAdapter(itemAdapter);
        buildGroups();
        groupAdapter.setNewData(groups);
        groupAdapter.setOnItemClickListener((a,v,pos) -> selectGroup(pos));
        itemAdapter.setOnItemClickListener((a,v,pos) -> clickItem(pos));
        selectGroup(0);
    }

    private void buildGroups() {
        groups.clear();
        groups.add(group(0, "线路选择", lineItems()));
        groups.add(group(1, "画面比例", named(new String[]{"16:9","4:3","填充","原始","裁剪"})));
        groups.add(group(2, "播放解码", named(new String[]{"硬解","软解"})));
        groups.add(group(3, "超时换源", named(new String[]{"5s","10s","15s","20s","25s","30s"})));
        groups.add(group(4, "偏好设置", named(new String[]{"换台反转","跨选分类","超时换源"})));
        groups.add(group(5, "订阅配置", named(new String[]{"列表订阅","EPG订阅"})));
    }

    private LiveSettingGroup group(int index, String name, ArrayList<LiveSettingItem> items) {
        LiveSettingGroup g = new LiveSettingGroup();
        g.setGroupIndex(index); g.setGroupName(name); g.setLiveSettingItems(items); return g;
    }
    private ArrayList<LiveSettingItem> named(String[] names) {
        ArrayList<LiveSettingItem> list = new ArrayList<>();
        for (int i=0;i<names.length;i++) { LiveSettingItem x=new LiveSettingItem(); x.setItemName(names[i]); x.setItemIndex(i); list.add(x); }
        return list;
    }
    private ArrayList<LiveSettingItem> lineItems() {
        return named(new String[]{"线路1"});
    }

    private void selectGroup(int position) {
        if (position<0 || position>=groups.size()) return;
        int index=groups.get(position).getGroupIndex();
        groupAdapter.setSelectedGroupIndex(index);
        groupAdapter.setFocusedGroupIndex(index);
        List<LiveSettingItem> list=groups.get(position).getLiveSettingItems();
        itemAdapter.setNewData(list==null?new ArrayList<>():list);
        if (itemAdapter.getItemCount()>0) itemAdapter.setSelectedItemIndex(0);
        itemView.requestFocus();
        itemView.post(() -> { if (itemAdapter.getItemCount()>0) itemView.scrollToPosition(0); });
    }

    private void clickItem(int position) {
        if (position<0 || position>=itemAdapter.getItemCount()) return;
        FastClickCheckUtil.check(itemView);
        LiveSettingItem item=itemAdapter.getItem(position);
        int group=groupAdapter.getSelectedGroupIndex();
        if (group==5) {
            if (item.getItemIndex()==0) showLiveSubDialog(); else showEpgSubDialog();
            return;
        }
        if (group==1) Hawk.put(HawkConfig.LIVE_PLAY_SCALE,item.getItemIndex());
        else if (group==2) Hawk.put(HawkConfig.LIVE_PLAY_TYPE,item.getItemIndex());
        else if (group==3) { int[] t={5,10,15,20,25,30}; Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT,t[item.getItemIndex()]); }
        else if (group==4) {
            if(item.getItemIndex()==0) Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE,!Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE,false));
            else if(item.getItemIndex()==1) Hawk.put(HawkConfig.LIVE_CROSS_GROUP,!Hawk.get(HawkConfig.LIVE_CROSS_GROUP,false));
            else Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT_CHANGE_SOURCE,!Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT_CHANGE_SOURCE,true));
        }
        Toast.makeText(getContext(), "已设置："+item.getItemName(), Toast.LENGTH_SHORT).show();
    }

    private void showLiveSubDialog() {
        String current=Hawk.get(HawkConfig.LIVE_API_URL, "");
        new InputDialog(getContext()).setTitle("直播订阅地址").setHint("请输入直播源订阅链接 (支持m3u/txt/json)...")
            .setDefaultText(current).setOnConfirmListener(text -> {
                if(TextUtils.isEmpty(text)||TextUtils.isEmpty(text.trim())) { Toast.makeText(getContext(),"地址不能为空",Toast.LENGTH_SHORT).show(); return; }
                String url=text.trim(); Hawk.put(HawkConfig.LIVE_API_URL,url);
                Toast.makeText(getContext(),"直播订阅已保存，正在重新加载",Toast.LENGTH_LONG).show();
                if(callback!=null) callback.onLiveChanged();
            }).show();
    }
    private void showEpgSubDialog() {
        String current=Hawk.get(HawkConfig.EPG_URL, "");
        new InputDialog(getContext()).setTitle("EPG节目单地址").setHint("请输入EPG订阅链接 (XML/JSON格式)...")
            .setDefaultText(current).setOnConfirmListener(text -> {
                if(TextUtils.isEmpty(text)||TextUtils.isEmpty(text.trim())) { Toast.makeText(getContext(),"地址不能为空",Toast.LENGTH_SHORT).show(); return; }
                Hawk.put(HawkConfig.EPG_URL,text.trim());
                Toast.makeText(getContext(),"EPG订阅已保存，后台更新",Toast.LENGTH_LONG).show();
                if(callback!=null) callback.onEpgChanged();
            }).show();
    }

    @Override public void show() {
        super.show();
        Window w=getWindow();
        if(w!=null){ WindowManager.LayoutParams lp=w.getAttributes(); lp.width=dp(760); lp.height=dp(520); lp.gravity=android.view.Gravity.CENTER; w.setAttributes(lp); }
        groupView.post(() -> { groupView.requestFocus(); if(groupAdapter.getItemCount()>0) groupAdapter.setSelectedGroupIndex(0); });
    }
    private int dp(int v){return (int)(v*getContext().getResources().getDisplayMetrics().density+0.5f);}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()==KeyEvent.ACTION_UP && e.getKeyCode()==KeyEvent.KEYCODE_BACK){ dismiss(); return true; }
        return super.dispatchKeyEvent(e);
    }
}

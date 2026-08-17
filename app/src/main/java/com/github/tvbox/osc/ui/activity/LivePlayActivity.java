package com.github.tvbox.osc.ui.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.Gravity;
import android.widget.EditText;
import android.app.AlertDialog;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveDayListGroup;
import com.github.tvbox.osc.bean.LiveEpgDate;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.player.controller.LiveController;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter;
import com.github.tvbox.osc.ui.adapter.LiveEpgAdapter;
import com.github.tvbox.osc.ui.adapter.LiveEpgDateAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.github.tvbox.osc.ui.adapter.MyEpgAdapter;
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog;
import com.github.tvbox.osc.ui.tv.widget.ChannelListView;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.util.EpgDataJsonParser;
import com.github.tvbox.osc.util.EpgDataManager;
import com.github.tvbox.osc.util.EpgLogoManager;
import com.github.tvbox.osc.util.EpgNameFuzzyMatch;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xyz.doikki.videoplayer.player.VideoView;


/**
 * 修复说明：
 * 1. 修复 EPG 订阅点击崩溃（Hawk.get null 防护、空指针防护）
 * 2. 集成 EPG 数据库管理，支持 60M 大文件
 * 3. 集成 epg_data.json 解析和台标下载
 * 4. 所有 EPG 操作改为异步，避免主线程阻塞
 */
public class LivePlayActivity extends BaseActivity {

    // Android 13+ 返回键/返回手势必须通过 OnBackPressedDispatcher 处理。
    // 电视盒子部分 ROM 不再可靠地走传统 onBackPressed()/KEYCODE_BACK。
    private void installKu9BackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                debugLog("KU9_BACK_DISPATCHER: handled");
                handleKu9Back();
            }
        });
    }

    public static Context context;
    private VideoView mVideoView;
    private TextView tvChannelInfo;
    private TextView tvTime;
    private TextView tvNetSpeed;
    private LinearLayout tvLeftChannelListLayout;
    private TvRecyclerView mChannelGroupView;
    private TvRecyclerView mLiveChannelView;
    private LiveChannelGroupAdapter liveChannelGroupAdapter;
    private LiveChannelItemAdapter liveChannelItemAdapter;

    private LinearLayout tvRightSettingLayout;
    private TvRecyclerView mSettingGroupView;
    private TvRecyclerView mSettingItemView;
    private LiveSettingGroupAdapter liveSettingGroupAdapter;
    private LiveSettingItemAdapter liveSettingItemAdapter;
    private List<LiveSettingGroup> settingGroupList = new ArrayList<>();

    public static int currentChannelGroupIndex = 0;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private List<LiveChannelGroup> liveChannelGroupList = new ArrayList<>();
    private int currentLiveChannelIndex = -1;
    private int currentLiveChangeSourceTimes = 0;
    private LiveChannelItem currentLiveChannelItem = null;
    private LivePlayerManager livePlayerManager = new LivePlayerManager();
    private ArrayList<Integer> channelGroupPasswordConfirmed = new ArrayList<>();

    private static LiveChannelItem channel_Name = null;
    private static Hashtable hsEpg = new Hashtable();
    private List<Epginfo> epgdata = new ArrayList<>();

    private TextView tvChannelName;
    private TextView tvChannelEpg;
    private ImageView iv_circle_bg;
    private TextView tv_current_program_time;
    private TextView tv_current_program_name;
    private TextView tv_next_program_time;
    private TextView tv_next_program_name;
    private TextView tv_srcinfo;
    private TextView tv_curepg_left;
    private TextView tv_nextepg_left;
    private ImageView iv_back;
    private MyEpgAdapter myAdapter;
    private TextView tv_right_top_tipnetspeed;
    private TextView tv_right_top_channel_name;
    private TextView tv_right_top_epg_name;
    private ImageView iv_right_top_icon;

    private ObjectAnimator objectAnimator;
    public String epgString = "";

    private boolean isSHIYI = false;
    private static String shiyi_time;
    private static int shiyi_time_c;
    public static Date currentTime;
    public static Date startTime;
    public static Date endTime;
    private int videoWidth = 1920;
    private int videoHeight = 1080;
    public boolean fullScreen = true;
    private static int EPG_TIME_GAP = 0;
    private int mLastChannelGroupIndex = -1;
    private int mLastChannelIndex = -1;
    private boolean isBack = false;
    private static String playUrl;
    private String setLastChannelUri = null;
    private static String setLastChannel = null;
    private static String setLastSource = null;
    private static int setLastChannelIndex = -1;
    private static int setLastSourceIndex = -1;
    private static int setLastChannelGroupIndex = -1;
    private static int setLastChannelPlayCount = 0;
    private boolean isCurrentLiveChannelItem = false;

    private RelativeLayout ll_epg;
    private RelativeLayout ll_right_top_huikan;
    private RelativeLayout ll_right_top_loading;
    private TextView tv_shiyi;
    private TextView tv_shiyi_time;
    private SeekBar sBar;
    private TextView tv_currentpos;
    private TextView tv_duration;
    private int videoCacheStrategy = 0;
    private boolean isPause = false;
    private static String currentLiveChannelItem_Name = null;
    private static int currentLiveChannelItem_SourceIndex = 0;
    private static int currentLiveChannelItem_NextSourceIndex = 0;
    private static String currentLiveChannelItem_Url = null;
    private static String currentLiveChannelItem_Line_Num = null;
    private static String currentLiveChannelItem_ReqHeader = null;
    private static boolean currentLiveChannelItem_skipLoad = false;

    private View llLoading;
    private View llPause;
    private View llRightTop;
    private View llNetspeed;

    private TvRecyclerView mEpgDateGridView;
    private TvRecyclerView mEpgInfoGridView;
    private LiveEpgDateAdapter liveEpgDateAdapter;
    private LiveEpgAdapter epgListAdapter;
    private List<LiveDayListGroup> liveDayTimeGroupList = new ArrayList<>();

    private LiveController liveController;
    private int currentLiveLineIndex = 0;

    // ========== 修复1: 线程池用于异步EPG操作，避免主线程阻塞 ==========
    private final ExecutorService epgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String TAG = "LivePlay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        debugLog("LIVE_CREATE_001 onCreate BEGIN");
        try {
            super.onCreate(savedInstanceState);
            debugLog("LIVE_CREATE_002 onCreate END");
        } catch (Throwable t) {
            debugLog("LIVE_CREATE_CRASH onCreate", t);
            throw t;
        }
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_play;
    }

    @Override
    protected void init() {
        installKu9BackHandler();
        debugLog("LIVE_INIT_001 init BEGIN");
        try {
            initInternal();
            debugLog("LIVE_INIT_999 init END");
        } catch (Throwable t) {
            debugLog("LIVE_INIT_CRASH init", t);
            throw t;
        }
    }

    private void initInternal() {
        context = this;
        // ========== 修复2: Hawk.get 做空值防护 ==========
        String rawEpg = Hawk.get(HawkConfig.EPG_URL, "");
        epgString = rawEpg != null ? rawEpg : "";

        setLoadSir(findViewById(R.id.live_root));
        mVideoView = findViewById(R.id.mVideoView);
        tvLeftChannelListLayout = findViewById(R.id.tvLeftChannnelListLayout);
        mChannelGroupView = findViewById(R.id.mGroupGridView);
        mLiveChannelView = findViewById(R.id.mChannelGridView);
        tvRightSettingLayout = findViewById(R.id.tvRightSettingLayout);
        mSettingGroupView = findViewById(R.id.mSettingGroupView);
        mSettingItemView = findViewById(R.id.mSettingItemView);
        tvChannelInfo = findViewById(R.id.tvChannel);
        tvTime = findViewById(R.id.tvTime);
        tvNetSpeed = findViewById(R.id.tvNetSpeed);
        llLoading = findViewById(R.id.ll_loading);
        llPause = findViewById(R.id.ll_pause);
        llRightTop = findViewById(R.id.ll_right_top);
        llNetspeed = findViewById(R.id.ll_netspeed);

        tvChannelName = findViewById(R.id.tv_channel_name);
        tvChannelEpg = findViewById(R.id.tv_channel_epg);
        iv_circle_bg = findViewById(R.id.iv_circle_bg);
        tv_current_program_time = findViewById(R.id.tv_current_program_time);
        tv_current_program_name = findViewById(R.id.tv_current_program_name);
        tv_next_program_time = findViewById(R.id.tv_next_program_time);
        tv_next_program_name = findViewById(R.id.tv_next_program_name);
        tv_srcinfo = findViewById(R.id.tv_srcinfo);
        tv_curepg_left = findViewById(R.id.tv_current_program_time_left);
        tv_nextepg_left = findViewById(R.id.tv_next_program_time_left);

        iv_back = findViewById(R.id.iv_back);
        tv_right_top_tipnetspeed = findViewById(R.id.tv_right_top_tipnetspeed);
        tv_right_top_channel_name = findViewById(R.id.tv_right_top_channel_name);
        tv_right_top_epg_name = findViewById(R.id.tv_right_top_epg_name);
        iv_right_top_icon = findViewById(R.id.iv_right_top_icon);

        ll_epg = findViewById(R.id.ll_epg);
        ll_right_top_huikan = findViewById(R.id.ll_right_top_huikan);
        tv_shiyi = findViewById(R.id.tv_shiyi);
        tv_shiyi_time = findViewById(R.id.tv_shiyi_time);
        sBar = findViewById(R.id.pb_progress);
        tv_currentpos = findViewById(R.id.tv_currentpos);
        tv_duration = findViewById(R.id.tv_duration);

        mEpgDateGridView = findViewById(R.id.mEpgDateGridView);
        mEpgInfoGridView = findViewById(R.id.mEpgInfoGridView);

        iv_back.setOnClickListener(view -> {
            if (isBack) {
                isBack = false;
                if (isSHIYI) {
                    stopShiyiAndResume();
                } else {
                    if (mVideoView != null) {
                        mVideoView.release();
                        if (currentLiveLineIndex == currentLiveChannelItem.getSourceNum() - 1) {
                            currentLiveLineIndex = 0;
                        } else {
                            currentLiveLineIndex++;
                        }
                        currentLiveChannelItem.setSourceIndex(currentLiveLineIndex);
                        playNextSource();
                    }
                }
            } else {
                if (isSHIYI) {
                    stopShiyiAndResume();
                } else {
                    exit();
                }
            }
        });

        tv_right_top_tipnetspeed.setText("智能切换中");
        ll_right_top_loading = findViewById(R.id.ll_right_top_loading);
        ll_right_top_loading.setVisibility(View.VISIBLE);

        tv_right_top_channel_name.setText("CCTV-1");
        tv_right_top_channel_name.getPaint().setFakeBoldText(true);
        tv_right_top_epg_name.setText("精彩节目-暂未提供节目预告信息");
        tv_right_top_epg_name.getPaint().setFakeBoldText(true);
        iv_right_top_icon = findViewById(R.id.iv_right_top_icon);

        initEpgDateView();
        initEpgListView();
        initDayList();
        initVideoView();
        initChannelGroupView();
        initLiveChannelView();
        initSettingGroupView();
        initSettingItemView();
        initLiveChannelList();
        initLiveSettingList();

        // 如果没有通过点播配置自动带入直播源，则按酷9的订阅地址直接加载。
        if (ApiConfig.get().getChannelGroupList() == null || ApiConfig.get().getChannelGroupList().isEmpty()) {
            String liveUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
            if (!TextUtils.isEmpty(liveUrl)) {
                debugLog("LIVE_AUTO_LOAD: " + liveUrl);
                ApiConfig.get().loadLiveConfig(true, new ApiConfig.LoadConfigCallback() {
                    @Override public void success() {
                        runOnUiThread(() -> {
                            debugLog("LIVE_AUTO_LOAD_SUCCESS");
                            initLiveChannelList();
                        });
                    }
                    @Override public void error(String msg) {
                        debugLog("LIVE_AUTO_LOAD_ERROR: " + msg);
                    }
                    @Override public void notice(String msg) {
                        debugLog("LIVE_AUTO_LOAD_NOTICE: " + msg);
                    }
                });
            }
        }

        if (mVideoView != null) {
            mVideoView.setOnStateChangeListener(new VideoView.OnStateChangeListener() {
                @Override
                public void onPlayerStateChanged(int playerState) {}

                @Override
                public void onPlayStateChanged(int playState) {
                    switch (playState) {
                        case VideoView.STATE_IDLE:
                        case VideoView.STATE_PAUSED:
                            break;
                        case VideoView.STATE_PREPARED:
                        case VideoView.STATE_BUFFERED:
                        case VideoView.STATE_PLAYING:
                            currentLiveChangeSourceTimes = 0;
                            ll_right_top_loading.setVisibility(View.GONE);
                            llLoading.setVisibility(View.GONE);
                            break;
                        case VideoView.STATE_ERROR:
                        case VideoView.STATE_PLAYBACK_COMPLETED:
                            llLoading.setVisibility(View.GONE);
                            ll_right_top_loading.setVisibility(View.GONE);
                            break;
                        case VideoView.STATE_PREPARING:
                        case VideoView.STATE_BUFFERING:
                            llLoading.setVisibility(View.VISIBLE);
                            ll_right_top_loading.setVisibility(View.VISIBLE);
                            break;
                    }
                }
            });
        }

        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            tvLeftChannelListLayout.setVisibility(View.INVISIBLE);
            tvLeftChannelListLayout.setLayoutParams(new RelativeLayout.LayoutParams(dp2px(160), dp2px(320)));
        }
        tvLeftChannelListLayout.setVisibility(View.INVISIBLE);

        if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            tvRightSettingLayout.setVisibility(View.INVISIBLE);
            tvRightSettingLayout.setLayoutParams(new RelativeLayout.LayoutParams(dp2px(160), dp2px(320)));
        }
        tvRightSettingLayout.setVisibility(View.INVISIBLE);

        isBack = !fullScreen;

        // ========== 修复3: 异步初始化 EPG Data Json 和台标 ==========
        initEpgDataJsonAsync();
    }

    private void stopShiyiAndResume() {
        if (mVideoView == null) return;
        mVideoView.release();
        isSHIYI = false;
        mVideoView.setUrl(currentLiveChannelItem.getUrl());
        mVideoView.start();
        tv_shiyi.setText("时移");
        tv_shiyi.setVisibility(View.GONE);
        ll_right_top_huikan.setVisibility(View.GONE);
        ll_epg.setVisibility(View.GONE);
        sBar.setVisibility(View.GONE);
        tv_currentpos.setVisibility(View.GONE);
        tv_duration.setVisibility(View.GONE);
        ll_right_top_loading.setVisibility(View.VISIBLE);
        tv_right_top_tipnetspeed.setText("智能切换中");
        mHandler.postDelayed(() -> ll_right_top_loading.setVisibility(View.GONE), 1000);
    }

    // ========== 修复4: 异步初始化 epg_data.json 和台标，避免主线程阻塞 ==========
    private void initEpgDataJsonAsync() {
        epgExecutor.execute(() -> {
            try {
                EpgDataJsonParser.getInstance().loadEpgDataJson(LivePlayActivity.this);
                EpgLogoManager.getInstance(LivePlayActivity.this).loadLogoDataFromJson(LivePlayActivity.this);
            } catch (Exception e) {
                Log.e(TAG, "initEpgDataJson error", e);
            }
        });
    }

    private void exit() {
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        if (epgExecutor != null && !epgExecutor.isShutdown()) {
            epgExecutor.shutdown();
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        debugLog("KU9_BACK_LEGACY: called");
        handleKu9Back();
    }

    private void handleKu9Back() {
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            tvLeftChannelListLayout.setVisibility(View.INVISIBLE);
            return;
        }
        if (ll_epg.getVisibility() == View.VISIBLE) {
            hideEpgPanel();
            return;
        }
        if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            hideKu9SettingMenu();
            return;
        }
        openKu9SettingMenu();
    }

    private void openKu9SettingMenu() {
        debugLog("KU9_MENU_OPEN: groups=" + settingGroupList.size());
        mHandler.removeCallbacks(mShowSettingLayoutRun);
        mHandler.removeCallbacks(mHideSettingLayoutRun);

        ViewGroup.LayoutParams lp = tvRightSettingLayout.getLayoutParams();
        lp.width = dp2px(640);
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        tvRightSettingLayout.setLayoutParams(lp);
        tvRightSettingLayout.setVisibility(View.VISIBLE);
        tvRightSettingLayout.bringToFront();

        // 首次打开没有直播源时，直接进入“订阅配置”，让用户可以添加直播订阅。
        int initialGroup = settingGroupList.size() > 5 ? 5 : 0;
        if (!settingGroupList.isEmpty()) {
            selectSettingGroup(initialGroup, true);
            mSettingGroupView.requestFocus();
        }
        debugLog("KU9_MENU_VISIBLE: initialGroup=" + initialGroup
                + ", groupCount=" + settingGroupList.size());
    }

    private void hideKu9SettingMenu() {
        mHandler.removeCallbacks(mShowSettingLayoutRun);
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        tvRightSettingLayout.setVisibility(View.INVISIBLE);
        debugLog("KU9_MENU_HIDE");
    }

    private void hideEpgPanel() {
        ll_epg.setVisibility(View.GONE);
        tv_right_top_channel_name.setText("CCTV-1");
        tv_right_top_channel_name.getPaint().setFakeBoldText(true);
        tv_right_top_epg_name.setText("精彩节目-暂未提供节目预告信息");
        tv_right_top_epg_name.getPaint().setFakeBoldText(true);
        ll_right_top_loading.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        debugLog("LIVE_RESUME_BEGIN");
        super.onResume();
        debugLog("LIVE_RESUME_END");
        if (mVideoView != null) mVideoView.resume();
    }

    @Override
    protected void onPause() {
        debugLog("LIVE_PAUSE_BEGIN");
        super.onPause();
        debugLog("LIVE_PAUSE_END");
        if (mVideoView != null) mVideoView.pause();
    }

    @Override
    protected void onDestroy() {
        debugLog("LIVE_DESTROY_BEGIN");
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        if (epgExecutor != null && !epgExecutor.isShutdown()) {
            epgExecutor.shutdown();
        }
        debugLog("LIVE_DESTROY_END");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            debugLog("KU9_BACK_ON_KEYDOWN");
            handleKu9Back();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO
                    || keyCode == KeyEvent.KEYCODE_HELP || keyCode == KeyEvent.KEYCODE_SETTINGS) {
                showSettingGroup();
            } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                debugLog("KU9_BACK_KEYEVENT: action=DOWN");
                handleKu9Back();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    if (currentLiveChannelIndex < 0) {
                        currentLiveChannelIndex = 0;
                    } else {
                        --currentLiveChannelIndex;
                        if (currentLiveChannelIndex < 0)
                            currentLiveChannelIndex = liveChannelItemAdapter.getItemCount() - 1;
                    }
                    liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
                    mLiveChannelView.scrollToPosition(currentLiveChannelIndex);
                } else {
                    if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
                        playNext();
                    else
                        playPrevious();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    ++currentLiveChannelIndex;
                    if (currentLiveChannelIndex >= liveChannelItemAdapter.getItemCount())
                        currentLiveChannelIndex = 0;
                    liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
                    mLiveChannelView.scrollToPosition(currentLiveChannelIndex);
                } else {
                    if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
                        playPrevious();
                    else
                        playNext();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    if (currentChannelGroupIndex < 0) {
                        currentChannelGroupIndex = 0;
                    } else {
                        --currentChannelGroupIndex;
                        if (currentChannelGroupIndex < 0)
                            currentChannelGroupIndex = liveChannelGroupAdapter.getItemCount() - 1;
                    }
                    liveChannelGroupAdapter.setSelectedGroupIndex(currentChannelGroupIndex);
                    mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
                    loadCurrentChannelGroupChannels();
                } else {
                    showChannelList();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    ++currentChannelGroupIndex;
                    if (currentChannelGroupIndex >= liveChannelGroupAdapter.getItemCount())
                        currentChannelGroupIndex = 0;
                    liveChannelGroupAdapter.setSelectedGroupIndex(currentChannelGroupIndex);
                    mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
                    loadCurrentChannelGroupChannels();
                } else {
                    showChannelList();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    clickLiveChannel(liveChannelItemAdapter.getSelectedChannelIndex());
                } else {
                    showChannelList();
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void showChannelList() {
        if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        }
        if (tvLeftChannelListLayout.getVisibility() == View.INVISIBLE) {
            liveChannelGroupAdapter.setSelectedGroupIndex(currentChannelGroupIndex);
            loadCurrentChannelGroupChannels();
            liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
            mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
            mLiveChannelView.scrollToPosition(currentLiveChannelIndex);
            mHandler.postDelayed(mFocusCurrentChannelAndShowChannelList, 50);
        } else {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        }
    }

    private Runnable mFocusCurrentChannelAndShowChannelList = new Runnable() {
        @Override
        public void run() {
            if (mChannelGroupView.isScrolling() || mLiveChannelView.isScrolling() || mChannelGroupView.isComputingLayout() || mLiveChannelView.isComputingLayout()) {
                mHandler.postDelayed(this, 100);
            } else {
                RecyclerView.ViewHolder holder = mLiveChannelView.findViewHolderForAdapterPosition(currentLiveChannelIndex);
                if (holder != null)
                    holder.itemView.requestFocus();
                tvLeftChannelListLayout.setVisibility(View.VISIBLE);
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvLeftChannelListLayout.getLayoutParams();
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    params.width = dp2px(320);
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    tvLeftChannelListLayout.setLayoutParams(params);
                    mHandler.removeCallbacks(mHideChannelListRun);
                    mHandler.postDelayed(mHideChannelListRun, 5000);
                }
            }
        }
    };

    private Runnable mHideChannelListRun = new Runnable() {
        @Override
        public void run() {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvLeftChannelListLayout.getLayoutParams();
            if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                params.width = dp2px(160);
                params.height = dp2px(320);
                tvLeftChannelListLayout.setLayoutParams(params);
                tvLeftChannelListLayout.setVisibility(View.INVISIBLE);
            }
        }
    };

    private void showSettingGroup() {
        debugLog("SHOW_SETTING_GROUP");
        if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            hideKu9SettingMenu();
        } else {
            openKu9SettingMenu();
        }
    }

    private Runnable mShowSettingLayoutRun = new Runnable() {
        @Override
        public void run() {
            tvRightSettingLayout.setVisibility(View.VISIBLE);
            debugLog("SETTING_LAYOUT_VISIBLE");
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            tvRightSettingLayout.setLayoutParams(params);
            // RecyclerView 不允许 scrollToPosition(-1)。旧代码这里会直接导致：
            // IllegalArgumentException: Invalid target position。
            liveSettingGroupAdapter.setSelectedGroupIndex(-1);

            // 没有直播源时，直接把焦点落到酷9的“订阅配置”，
            // 用户随后可进入“列表订阅 -> 添加新的直播订阅”。
            boolean hasLiveSource = !TextUtils.isEmpty(Hawk.get(HawkConfig.LIVE_API_URL, ""))
                    || (ApiConfig.get().getChannelGroupList() != null
                    && !ApiConfig.get().getChannelGroupList().isEmpty());
            int initialGroup = hasLiveSource ? 0 : 5;
            selectSettingGroup(initialGroup, true);

            mHandler.removeCallbacks(mHideSettingLayoutRun);
            // Ku9 设置菜单保持显示，直到再次按返回键关闭。
        }
    };

    private Runnable mHideSettingLayoutRun = new Runnable() {
        @Override
        public void run() {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
            if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
                params.height = dp2px(320);
                tvRightSettingLayout.setLayoutParams(params);
                tvRightSettingLayout.setVisibility(View.INVISIBLE);
            }
        }
    };

    private void initEpgDateView() {
        mEpgDateGridView.setHasFixedSize(true);
        mEpgDateGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveEpgDateAdapter = new LiveEpgDateAdapter();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        SimpleDateFormat datePresentFormat = new SimpleDateFormat("MM-dd");
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        for (int i = 0; i < 8; i++) {
            LiveEpgDate epgDate = new LiveEpgDate();
            Date dateIns = calendar.getTime();
            epgDate.setIndex(i);
            epgDate.setDatePresented(datePresentFormat.format(dateIns));
            epgDate.setIncludeTaday(i == 1);
            epgDate.setDate(dateIns);
            liveEpgDateAdapter.addData(epgDate);
            calendar.add(Calendar.DAY_OF_MONTH, -1);
        }
        mEpgDateGridView.setAdapter(liveEpgDateAdapter);
        mEpgDateGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, 5000);
            }
        });
        liveEpgDateAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            LiveEpgDate selectedData = liveEpgDateAdapter.getItem(position);
            liveEpgDateAdapter.setSelectedIndex(position);
            mEpgDateGridView.post(() -> liveEpgDateAdapter.notifyDataSetChanged());
            getEpg(new Date());
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.postDelayed(mHideChannelListRun, 5000);
        });
    }

    private void initEpgListView() {
        mEpgInfoGridView.setHasFixedSize(true);
        mEpgInfoGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        epgListAdapter = new LiveEpgAdapter();
        mEpgInfoGridView.setAdapter(epgListAdapter);
        mEpgInfoGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, 5000);
            }
        });
        epgListAdapter.setOnItemClickListener((adapter, view, position) -> {
            Date date = epgListAdapter.getItem(position).getDate();
            Date now = new Date();
            if (date.getTime() < now.getTime()) {
                epgListAdapter.setSelectedIndex(position);
                epgListAdapter.notifyDataSetChanged();
                mVideoView.release();
                String url = epgListAdapter.getItem(position).getUrl();
                mVideoView.setUrl(url);
                mVideoView.start();
                ll_epg.setVisibility(View.GONE);
                ll_right_top_huikan.setVisibility(View.VISIBLE);
                tv_shiyi.setText("回看中");
                tv_shiyi.setVisibility(View.VISIBLE);
                tv_right_top_tipnetspeed.setText("智能切换中");
                ll_right_top_loading.setVisibility(View.VISIBLE);
                isSHIYI = true;
                mHandler.postDelayed(() -> ll_right_top_loading.setVisibility(View.GONE), 1000);
                shiyi_time = epgListAdapter.getItem(position).getDate().getTime() + "";
                shiyi_time_c = (int) (new Date().getTime() - epgListAdapter.getItem(position).getDate().getTime());
            }
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.postDelayed(mHideChannelListRun, 5000);
        });
    }

    private void initDayList() {
        liveDayTimeGroupList = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            LiveDayListGroup daylist = new LiveDayListGroup();
            ArrayList<LiveEpgDate> daylistdata = new ArrayList<>();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.add(Calendar.DAY_OF_MONTH, -i);
            SimpleDateFormat datePresentFormat = new SimpleDateFormat("MM-dd");
            for (int j = 0; j < 24; j++) {
                LiveEpgDate epgDate = new LiveEpgDate();
                Date dateIns = calendar.getTime();
                epgDate.setIndex(j);
                epgDate.setDatePresented(datePresentFormat.format(dateIns) + " " + String.format("%02d", j) + ":00");
                epgDate.setIncludeTaday(i == 1);
                epgDate.setDate(dateIns);
                daylistdata.add(epgDate);
            }
            daylist.setIndex(i);
            daylist.setData(daylistdata);
            liveDayTimeGroupList.add(daylist);
        }
    }

    private void initVideoView() {
        liveController = new LiveController(this);
        liveController.setListener(new LiveController.LiveControlListener() {
            @Override
            public boolean singleTap() {
                showChannelList();
                return true;
            }

            @Override
            public void longPress() {
                showSettingGroup();
            }

            @Override
            public void playStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_IDLE:
                    case VideoView.STATE_PAUSED:
                        break;
                    case VideoView.STATE_PREPARED:
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        currentLiveChangeSourceTimes = 0;
                        break;
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        break;
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        break;
                }
            }

            @Override
            public void changeSource(int direction) {
                if (direction > 0) {
                    if (currentLiveLineIndex == currentLiveChannelItem.getSourceNum() - 1) {
                        currentLiveLineIndex = 0;
                    } else {
                        currentLiveLineIndex++;
                    }
                } else {
                    if (currentLiveLineIndex == 0) {
                        currentLiveLineIndex = currentLiveChannelItem.getSourceNum() - 1;
                    } else {
                        currentLiveLineIndex--;
                    }
                }
                currentLiveChannelItem.setSourceIndex(currentLiveLineIndex);
                playNextSource();
            }
        });
        liveController.setCanChangePosition(false);
        liveController.setEnableInNormal(true);
        liveController.setGestureEnabled(true);
        liveController.setDoubleTapTogglePlayEnabled(false);
        mVideoView.setVideoController(liveController);
        mVideoView.setProgressManager(null);
    }

    private void initChannelGroupView() {
        mChannelGroupView.setHasFixedSize(true);
        mChannelGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveChannelGroupAdapter = new LiveChannelGroupAdapter();
        mChannelGroupView.setAdapter(liveChannelGroupAdapter);
        mChannelGroupView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, 5000);
            }
        });
        liveChannelGroupAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            selectChannelGroup(position, true, -1);
        });
    }

    private void initLiveChannelView() {
        mLiveChannelView.setHasFixedSize(true);
        mLiveChannelView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveChannelItemAdapter = new LiveChannelItemAdapter();
        mLiveChannelView.setAdapter(liveChannelItemAdapter);
        mLiveChannelView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, 5000);
            }
        });
        liveChannelItemAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            clickLiveChannel(position);
        });
    }

    private void initSettingGroupView() {
        mSettingGroupView.setHasFixedSize(true);
        mSettingGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveSettingGroupAdapter = new LiveSettingGroupAdapter();
        mSettingGroupView.setAdapter(liveSettingGroupAdapter);
        mSettingGroupView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, 5000);
            }
        });
        liveSettingGroupAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            selectSettingGroup(position, true);
        });
    }

    private void initSettingItemView() {
        mSettingItemView.setHasFixedSize(true);
        mSettingItemView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveSettingItemAdapter = new LiveSettingItemAdapter();
        mSettingItemView.setAdapter(liveSettingItemAdapter);
        mSettingItemView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, 5000);
            }
        });
        liveSettingItemAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            clickSettingItem(position);
        });
    }

    private void initLiveChannelList() {
        List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
        if (list == null || list.isEmpty()) {
            // 首次进入直播页时没有订阅源是正常状态。酷9不会把“空列表”当成错误，
            // 用户按返回键进入设置菜单后，再从“订阅配置 -> 列表订阅”添加直播源。
            debugLog("LIVE_CHANNELS_EMPTY: no live subscription configured yet");
            liveChannelGroupList.clear();
            liveChannelGroupAdapter.setNewData(liveChannelGroupList);
            if (mChannelGroupView != null) mChannelGroupView.setVisibility(View.VISIBLE);
            if (mLiveChannelView != null) mLiveChannelView.setVisibility(View.VISIBLE);
            return;
        }

        liveChannelGroupList.clear();
        liveChannelGroupList.addAll(list);
        liveChannelGroupAdapter.setNewData(liveChannelGroupList);

        if (currentChannelGroupIndex > -1) {
            selectChannelGroup(currentChannelGroupIndex, false, currentLiveChannelIndex);
        } else {
            selectChannelGroup(0, false, -1);
        }
    }

    private void initLiveSettingList() {
        settingGroupList = new ArrayList<>();

        ArrayList<LiveSettingItem> lineItems = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            LiveSettingItem item = new LiveSettingItem();
            item.setItemName("线路" + (i + 1));
            item.setItemIndex(i);
            lineItems.add(item);
        }

        ArrayList<LiveSettingItem> scaleItems = new ArrayList<>();
        String[] scales = {"16:9", "4:3", "填充", "原始", "裁剪"};
        for (int i = 0; i < scales.length; i++) {
            LiveSettingItem item = new LiveSettingItem();
            item.setItemName(scales[i]);
            item.setItemIndex(i);
            scaleItems.add(item);
        }

        ArrayList<LiveSettingItem> decoderItems = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LiveSettingItem item = new LiveSettingItem();
            item.setItemName(i == 0 ? "硬解" : "软解");
            item.setItemIndex(i);
            decoderItems.add(item);
        }

        ArrayList<LiveSettingItem> timeoutItems = new ArrayList<>();
        int[] timeout = {5, 10, 15, 20, 25, 30};
        for (int i = 0; i < timeout.length; i++) {
            LiveSettingItem item = new LiveSettingItem();
            item.setItemName(timeout[i] + "s");
            item.setItemIndex(i);
            timeoutItems.add(item);
        }

        ArrayList<LiveSettingItem> preferenceItems = new ArrayList<>();
        String[] prefs = {"换台反转", "跨选分类", "超时换源"};
        for (int i = 0; i < prefs.length; i++) {
            LiveSettingItem item = new LiveSettingItem();
            item.setItemName(prefs[i]);
            item.setItemIndex(i);
            preferenceItems.add(item);
        }

        ArrayList<LiveSettingItem> subscribeItems = new ArrayList<>();
        LiveSettingItem listSub = new LiveSettingItem();
        listSub.setItemName("列表订阅");
        listSub.setItemIndex(0);
        subscribeItems.add(listSub);
        LiveSettingItem epgSub = new LiveSettingItem();
        epgSub.setItemName("EPG订阅");
        epgSub.setItemIndex(1);
        subscribeItems.add(epgSub);

        LiveSettingGroup group1 = new LiveSettingGroup();
        group1.setGroupIndex(0);
        group1.setGroupName("线路选择");
        group1.setLiveSettingItems(lineItems);
        settingGroupList.add(group1);

        LiveSettingGroup group2 = new LiveSettingGroup();
        group2.setGroupIndex(1);
        group2.setGroupName("画面比例");
        group2.setLiveSettingItems(scaleItems);
        settingGroupList.add(group2);

        LiveSettingGroup group3 = new LiveSettingGroup();
        group3.setGroupIndex(2);
        group3.setGroupName("播放解码");
        group3.setLiveSettingItems(decoderItems);
        settingGroupList.add(group3);

        LiveSettingGroup group4 = new LiveSettingGroup();
        group4.setGroupIndex(3);
        group4.setGroupName("超时换源");
        group4.setLiveSettingItems(timeoutItems);
        settingGroupList.add(group4);

        LiveSettingGroup group5 = new LiveSettingGroup();
        group5.setGroupIndex(4);
        group5.setGroupName("偏好设置");
        group5.setLiveSettingItems(preferenceItems);
        settingGroupList.add(group5);

        LiveSettingGroup group6 = new LiveSettingGroup();
        group6.setGroupIndex(5);
        group6.setGroupName("订阅配置");
        group6.setLiveSettingItems(subscribeItems);
        settingGroupList.add(group6);

        liveSettingGroupAdapter.setNewData(settingGroupList);
    }

    private void selectChannelGroup(int groupIndex, boolean focus, int liveChannelIndex) {
        if (groupIndex < 0 || groupIndex >= liveChannelGroupList.size()) return;
        if (currentChannelGroupIndex == groupIndex) return;

        currentChannelGroupIndex = groupIndex;
        liveChannelGroupAdapter.setSelectedGroupIndex(currentChannelGroupIndex);
        mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
        if (focus) {
            liveChannelGroupAdapter.setFocusedGroupIndex(currentChannelGroupIndex);
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.postDelayed(mHideChannelListRun, 5000);
        }

        loadCurrentChannelGroupChannels();
        if (liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex);
        } else {
            if (currentLiveChannelIndex > -1 && currentLiveChannelIndex < liveChannelItemAdapter.getItemCount()) {
                mLiveChannelView.scrollToPosition(currentLiveChannelIndex);
                liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
            }
        }
    }

    private void loadCurrentChannelGroupChannels() {
        List<LiveChannelItem> channels = liveChannelGroupList.get(currentChannelGroupIndex).getLiveChannels();
        liveChannelItemAdapter.setNewData(channels);
    }

    private void clickLiveChannel(int position) {
        if (position < 0 || position >= liveChannelItemAdapter.getItemCount()) return;
        currentLiveChannelIndex = position;
        liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        mLiveChannelView.scrollToPosition(currentLiveChannelIndex);

        currentLiveChannelItem = liveChannelItemAdapter.getItem(currentLiveChannelIndex);
        Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
        playChannel(currentLiveChannelItem, false);
        mHandler.removeCallbacks(mHideChannelListRun);
        mHandler.postDelayed(mHideChannelListRun, 5000);
    }

    private void playChannel(LiveChannelItem liveChannelItem, boolean changeSource) {
        if (!changeSource) {
            currentLiveLineIndex = 0;
        }
        // livePlayerManager.getLivePlayer().release(); // EPG修复: 原API不存在
        livePlayerManager.getLiveChannelPlayer(mVideoView, liveChannelItem.getChannelName());
        tvChannelInfo.setText(liveChannelItem.getChannelName());
        tv_right_top_channel_name.setText(liveChannelItem.getChannelName());
        tv_right_top_channel_name.getPaint().setFakeBoldText(true);
        tv_right_top_epg_name.setText("精彩节目-暂未提供节目预告信息");
        tv_right_top_epg_name.getPaint().setFakeBoldText(true);

        // ========== EPG 修复: 加载台标 ==========
        loadChannelLogo(liveChannelItem.getChannelName());

        // ========== EPG 修复: 加载台标（使用 epg_data.json 映射） ==========
        loadChannelLogo(liveChannelItem.getChannelName());

        // ========== 修复5: 加载台标（使用 epg_data.json 映射） ==========
        loadChannelLogo(liveChannelItem.getChannelName());

        if (mVideoView != null) {
            mVideoView.release();
            if (!isSHIYI) {
                mVideoView.setUrl(liveChannelItem.getChannelSourceUrls().get(currentLiveLineIndex));
            }
            mVideoView.start();
        }

        currentLiveChangeSourceTimes = 0;
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 5) + 1) * 1000);

        // ========== 修复6: 异步加载 EPG，避免主线程阻塞 ==========
        loadEpgDataAsync(liveChannelItem.getChannelName());
    }

    // ========== 修复7: 台标加载（支持 epg_data.json 映射） ==========
    private void loadChannelLogo(String channelName) {
        String logoUrl = EpgLogoManager.getInstance(this).getLogoUrl(channelName);
        if (!TextUtils.isEmpty(logoUrl)) {
            Picasso.get().load(logoUrl).placeholder(R.drawable.icon_img_placeholder).into(iv_right_top_icon);
        } else {
            iv_right_top_icon.setImageResource(R.drawable.icon_img_placeholder);
        }
    }

    // ========== 修复8: 异步加载 EPG 数据，使用数据库缓存 ==========
    private void loadEpgDataAsync(final String channelName) {
        epgExecutor.execute(() -> {
            try {
                // 先从数据库查询
                List<Epginfo> epgList = EpgDataManager.getInstance(LivePlayActivity.this).getEpgList(channelName);
                if (epgList != null && !epgList.isEmpty()) {
                    mainHandler.post(() -> updateEpgUI(channelName, epgList));
                } else {
                    // 数据库没有，尝试从网络加载
                    String epgUrl = Hawk.get(HawkConfig.EPG_URL, "");
                    if (epgUrl != null && !epgUrl.isEmpty() && !"默认".equals(epgUrl)) {
                        EpgDataManager.getInstance(LivePlayActivity.this).loadEpgData(epgUrl, new EpgDataManager.EpgDataCallback() {
                            @Override
                            public void onSuccess(List<Epginfo> epgList) {
                                mainHandler.post(() -> updateEpgUI(channelName, epgList));
                            }

                            @Override
                            public void onError(String msg) {
                                Log.e(TAG, "EPG load error: " + msg);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadEpgDataAsync error", e);
            }
        });
    }

    private void updateEpgUI(String channelName, List<Epginfo> epgList) {
        if (epgList == null || epgList.isEmpty()) return;
        epgdata = epgList;
        String currentProgram = EpgUtil.getCurrentProgram(epgList);
        tv_right_top_epg_name.setText(currentProgram);
        tv_right_top_epg_name.getPaint().setFakeBoldText(true);
    }

    private void playNext() {
        if (currentLiveChannelIndex >= liveChannelItemAdapter.getItemCount() - 1) {
            if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                if (currentChannelGroupIndex >= liveChannelGroupList.size() - 1) {
                    selectChannelGroup(0, false, 0);
                } else {
                    selectChannelGroup(currentChannelGroupIndex + 1, false, 0);
                }
            } else {
                selectChannelGroup(currentChannelGroupIndex, false, 0);
            }
        } else {
            clickLiveChannel(currentLiveChannelIndex + 1);
        }
    }

    private void playPrevious() {
        if (currentLiveChannelIndex == 0) {
            if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                if (currentChannelGroupIndex == 0) {
                    selectChannelGroup(liveChannelGroupList.size() - 1, false, -1);
                } else {
                    selectChannelGroup(currentChannelGroupIndex - 1, false, -1);
                }
            } else {
                selectChannelGroup(currentChannelGroupIndex, false, -1);
            }
        } else {
            clickLiveChannel(currentLiveChannelIndex - 1);
        }
    }

    private void playNextSource() {
        if (currentLiveChannelItem == null) return;
        if (currentLiveLineIndex >= currentLiveChannelItem.getSourceNum()) {
            currentLiveLineIndex = 0;
        }
        // livePlayerManager.getLivePlayer().release(); // EPG修复: 原API不存在
        livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
        tvChannelInfo.setText(currentLiveChannelItem.getChannelName());
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView.setUrl(currentLiveChannelItem.getChannelSourceUrls().get(currentLiveLineIndex));
            mVideoView.start();
        }
        currentLiveChangeSourceTimes++;
        if (currentLiveChangeSourceTimes > 1) {
            ll_right_top_loading.setVisibility(View.VISIBLE);
            tv_right_top_tipnetspeed.setText("智能切换中");
        }
    }

    private Runnable mConnectTimeoutChangeSourceRun = new Runnable() {
        @Override
        public void run() {
            if (mVideoView == null) return;
            if (mVideoView.getCurrentPlayState() == VideoView.STATE_PREPARING
                    || mVideoView.getCurrentPlayState() == VideoView.STATE_BUFFERING
                    || mVideoView.getCurrentPlayState() == VideoView.STATE_ERROR) {
                if (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT_CHANGE_SOURCE, true)) {
                    playNextSource();
                }
            }
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 5) + 1) * 1000);
        }
    };

    private Runnable mUpdateNetSpeedRun = new Runnable() {
        @Override
        public void run() {
            if (mVideoView != null) {
                tvNetSpeed.setText(String.format("%.2fMB/s", mVideoView.getTcpSpeed() / 1024.0 / 1024.0));
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    private void selectSettingGroup(int groupIndex, boolean focus) {
        if (groupIndex < 0 || groupIndex >= settingGroupList.size()) return;
        if (liveSettingGroupAdapter.getSelectedGroupIndex() == groupIndex) return;

        liveSettingGroupAdapter.setSelectedGroupIndex(groupIndex);
        if (focus) {
            liveSettingGroupAdapter.setFocusedGroupIndex(groupIndex);
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            // Ku9 设置菜单保持显示，直到再次按返回键关闭。
        }
        mSettingGroupView.scrollToPosition(groupIndex);
        List<LiveSettingItem> items = settingGroupList.get(groupIndex).getLiveSettingItems();
        if (items == null || items.isEmpty()) {
            liveSettingItemAdapter.setNewData(new ArrayList<>());
            return;
        }
        liveSettingItemAdapter.setNewData(items);
        liveSettingItemAdapter.setSelectedItemIndex(items.get(0).getItemIndex());
    }

    private void clickSettingItem(int position) {
        if (position < 0 || position >= liveSettingItemAdapter.getItemCount()) return;
        LiveSettingItem item = liveSettingItemAdapter.getItem(position);
        if (item == null) return;
        int group = liveSettingGroupAdapter.getSelectedGroupIndex();
        if (group < 0 || group >= settingGroupList.size()) return;

        if (group == 5) {
            if (item.getItemIndex() == 0) {
                showLiveSubscriptionDialog();
            } else {
                showEpgSubscriptionDialog();
            }
            return;
        }

        switch (group) {
            case 0:
                if (currentLiveChannelItem != null && item.getItemIndex() < currentLiveChannelItem.getSourceNum()) {
                    currentLiveLineIndex = item.getItemIndex();
                    currentLiveChannelItem.setSourceIndex(currentLiveLineIndex);
                    playNextSource();
                }
                break;
            case 1:
                Hawk.put(HawkConfig.LIVE_PLAY_SCALE, item.getItemIndex());
                break;
            case 2:
                Hawk.put(HawkConfig.LIVE_PLAY_TYPE, item.getItemIndex());
                Toast.makeText(this, "已设置：" + item.getItemName(), Toast.LENGTH_SHORT).show();
                break;
            case 3:
                int[] timeout = {5, 10, 15, 20, 25, 30};
                if (item.getItemIndex() >= 0 && item.getItemIndex() < timeout.length) {
                    Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, timeout[item.getItemIndex()]);
                }
                break;
            case 4:
                if (item.getItemIndex() == 0) {
                    boolean value = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                    Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, value);
                    Toast.makeText(this, value ? "已开启换台反转" : "已关闭换台反转", Toast.LENGTH_SHORT).show();
                } else if (item.getItemIndex() == 1) {
                    boolean value = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
                    Hawk.put(HawkConfig.LIVE_CROSS_GROUP, value);
                    Toast.makeText(this, value ? "已开启跨选分类" : "已关闭跨选分类", Toast.LENGTH_SHORT).show();
                } else {
                    boolean value = !Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT_CHANGE_SOURCE, true);
                    Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT_CHANGE_SOURCE, value);
                    Toast.makeText(this, value ? "已开启超时换源" : "已关闭超时换源", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    /**
     * 酷9风格“列表订阅”入口。
     *
     * 用户要求：返回键 -> 右侧设置 -> 订阅配置 -> 列表订阅。
     * 这里不再只保存一个 URL，而是保存“备注 + 地址”的订阅列表，
     * 同时兼容之前版本只保存 URL 的 LIVE_API_HISTORY。
     */
    private void showLiveSubscriptionDialog() {
        final ArrayList<String> records = loadLiveSubscriptionRecords();
        final ArrayList<String> labels = new ArrayList<>();
        labels.add("＋ 添加新的直播订阅");
        for (String record : records) {
            String[] pair = splitSubscriptionRecord(record);
            labels.add(pair[0]);
        }

        final String currentUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        int checked = -1;
        for (int i = 0; i < records.size(); i++) {
            if (currentUrl.equals(splitSubscriptionRecord(records.get(i))[1])) {
                checked = i + 1;
                break;
            }
        }

        final int selectedChecked = checked;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("列表订阅")
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, which) -> {
                    if (which == 0) {
                        d.dismiss();
                        showLiveSubscriptionInput();
                    } else {
                        String[] pair = splitSubscriptionRecord(records.get(which - 1));
                        d.dismiss();
                        activateLiveSubscription(pair[0], pair[1]);
                    }
                })
                .setNegativeButton("关闭", null)
                .create();

        dialog.setOnShowListener(v -> {
            if (dialog.getListView() != null) {
                dialog.getListView().setDivider(null);
                if (selectedChecked >= 0 && selectedChecked < dialog.getListView().getChildCount()) {
                    dialog.getListView().getChildAt(selectedChecked).requestFocus();
                } else if (dialog.getListView().getChildCount() > 0) {
                    dialog.getListView().getChildAt(0).requestFocus();
                }
            }
        });
        dialog.show();
    }

    /** 兼容旧版本：history 只有 URL 时，自动转换成“备注=URL”。 */
    private ArrayList<String> loadLiveSubscriptionRecords() {
        ArrayList<String> result = new ArrayList<>();
        try {
            List<String> saved = Hawk.get(HawkConfig.LIVE_API_SUBSCRIPTIONS, new ArrayList<String>());
            if (saved != null) {
                for (String item : saved) {
                    if (!TextUtils.isEmpty(item) && !result.contains(item)) result.add(item);
                }
            }
        } catch (Throwable ignored) {
        }

        // 兼容之前 V5/V6 保存的 URL 历史。
        try {
            List<String> history = Hawk.get(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
            if (history != null) {
                for (String url : history) {
                    if (TextUtils.isEmpty(url)) continue;
                    String record = makeSubscriptionRecord(url, url);
                    boolean exists = false;
                    for (String old : result) {
                        if (splitSubscriptionRecord(old)[1].equals(url)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) result.add(record);
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private String makeSubscriptionRecord(String name, String url) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", TextUtils.isEmpty(name) ? url : name);
        obj.addProperty("url", url == null ? "" : url);
        return obj.toString();
    }

    private String[] splitSubscriptionRecord(String record) {
        try {
            JsonObject obj = new Gson().fromJson(record, JsonObject.class);
            if (obj != null && obj.has("url")) {
                String url = obj.get("url").getAsString();
                String name = obj.has("name") ? obj.get("name").getAsString() : url;
                return new String[]{TextUtils.isEmpty(name) ? url : name, url};
            }
        } catch (Throwable ignored) {
        }
        // 旧格式就是 URL。
        return new String[]{record == null ? "" : record, record == null ? "" : record};
    }

    private void saveLiveSubscriptionRecord(String name, String url) {
        ArrayList<String> records = loadLiveSubscriptionRecords();
        String newRecord = makeSubscriptionRecord(name, url);
        ArrayList<String> result = new ArrayList<>();
        result.add(newRecord);
        for (String old : records) {
            String[] pair = splitSubscriptionRecord(old);
            if (!url.equals(pair[1])) result.add(old);
        }
        while (result.size() > 30) result.remove(result.size() - 1);
        Hawk.put(HawkConfig.LIVE_API_SUBSCRIPTIONS, result);

        // 保留旧字段，兼容其它 TVBox 代码。
        ArrayList<String> history = new ArrayList<>();
        for (String item : result) {
            String itemUrl = splitSubscriptionRecord(item)[1];
            if (!TextUtils.isEmpty(itemUrl)) history.add(itemUrl);
        }
        Hawk.put(HawkConfig.LIVE_API_HISTORY, history);
    }

    private void showLiveSubscriptionInput() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp2px(20);
        box.setPadding(pad, 0, pad, 0);

        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("订阅备注，例如：我的直播源");
        box.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2px(52)));

        EditText urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint("直播源地址（m3u / m3u8 / txt / json）");
        box.addView(urlInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2px(60)));

        String current = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (!TextUtils.isEmpty(current)) {
            urlInput.setText(current);
            urlInput.setSelection(urlInput.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("添加直播订阅")
                .setView(box)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            if (TextUtils.isEmpty(url)) {
                urlInput.setError("请输入直播源地址");
                urlInput.requestFocus();
                return;
            }
            if (TextUtils.isEmpty(name)) name = url;
            dialog.dismiss();
            activateLiveSubscription(name, url);
        }));
        dialog.show();
    }

    private void activateLiveSubscription(final String name, final String url) {
        if (TextUtils.isEmpty(url)) return;

        saveLiveSubscriptionRecord(name, url);
        Hawk.put(HawkConfig.LIVE_API_URL, url);

        Toast.makeText(this, "正在加载直播订阅…", Toast.LENGTH_SHORT).show();
        debugLog("LIVE_SUBSCRIBE_LOAD: name=" + name + " url=" + url);
        ApiConfig.get().loadLiveConfig(false, new ApiConfig.LoadConfigCallback() {
            @Override public void success() {
                runOnUiThread(() -> {
                    List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
                    int count = groups == null ? 0 : groups.size();
                    debugLog("LIVE_SUBSCRIBE_SUCCESS: groups=" + count);
                    initLiveChannelList();
                    Toast.makeText(LivePlayActivity.this,
                            count > 0 ? "直播订阅加载成功" : "订阅已加载，但没有解析到频道",
                            Toast.LENGTH_SHORT).show();
                    mHandler.removeCallbacks(mHideSettingLayoutRun);
                    mHandler.post(mHideSettingLayoutRun);
                    if (count > 0) showChannelList();
                });
            }
            @Override public void error(String msg) {
                runOnUiThread(() -> {
                    debugLog("LIVE_SUBSCRIBE_ERROR: " + msg);
                    Toast.makeText(LivePlayActivity.this,
                            "直播订阅加载失败：" + msg, Toast.LENGTH_LONG).show();
                });
            }
            @Override public void notice(String msg) {
                debugLog("LIVE_SUBSCRIBE_NOTICE: " + msg);
            }
        });
    }

    private void showEpgSubscriptionDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("XMLTV / EPG 地址");
        input.setText(Hawk.get(HawkConfig.EPG_URL, ""));
        int pad = dp2px(20);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("EPG订阅")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String url = input.getText().toString().trim();
                    Hawk.put(HawkConfig.EPG_URL, url);
                    List<String> history = Hawk.get(HawkConfig.EPG_HISTORY, new ArrayList<>());
                    if (history == null) history = new ArrayList<>();
                    if (!url.isEmpty()) {
                        history.remove(url);
                        history.add(0, url);
                    }
                    Hawk.put(HawkConfig.EPG_HISTORY, history);
                    Toast.makeText(this, url.isEmpty() ? "EPG已清除" : "EPG订阅已保存", Toast.LENGTH_SHORT).show();
                    if (currentLiveChannelItem != null && !url.isEmpty()) loadEpgDataAsync(currentLiveChannelItem.getChannelName());
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    private void getEpg(Date date) {
        // EPG 数据获取逻辑，使用异步方式
        if (currentLiveChannelItem == null) return;
        loadEpgDataAsync(currentLiveChannelItem.getChannelName());
    }

    // ========== EPG 修复: 新增方法 ==========

    

    

    

    

    

    

    // ========== EPG 修复: 新增辅助方法 ==========

    private int dp2px(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    

    

    

    

    private static final String DEBUG_TAG = "Ku9TVBox-LivePlay";

    private void debugLog(String message) {
        Log.e(DEBUG_TAG, message);
        try { writeDeviceDebugLog(message, null); } catch (Throwable ignored) {}
    }

    private void debugLog(String message, Throwable throwable) {
        Log.e(DEBUG_TAG, message, throwable);
        try { writeDeviceDebugLog(message, throwable); } catch (Throwable ignored) {}
    }

    private void writeDeviceDebugLog(String message, Throwable throwable) {
        try {
            File dir = new File("/sdcard/Ku9TVBox");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "ku9_crash.log");
            FileWriter fw = new FileWriter(file, true);
            fw.write("\n===== " + new java.util.Date() + " =====\n" + message + "\n");
            if (throwable != null) fw.write(android.util.Log.getStackTraceString(throwable) + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    private void installCrashLogger() {
        try {
            final Thread.UncaughtExceptionHandler previous =
                    Thread.getDefaultUncaughtExceptionHandler();

            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                Log.e(DEBUG_TAG,
                        "========== LIVE PLAY CRASH ==========\n"
                                + "thread=" + thread.getName()
                                + "\nactivity=" + LivePlayActivity.this.getClass().getName(),
                        throwable);

                if (previous != null) {
                    previous.uncaughtException(thread, throwable);
                }
            });

            Log.e(DEBUG_TAG, "Crash logger installed");
        } catch (Throwable t) {
            Log.e(DEBUG_TAG, "Failed to install crash logger", t);
        }
    }

}
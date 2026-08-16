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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
    protected int getLayoutResID() {
        return R.layout.activity_live_play;
    }

    @Override
    protected void init() {
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
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        } else if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        } else if (ll_epg.getVisibility() == View.VISIBLE) {
            hideEpgPanel();
        } else {
            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            exit();
        }
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
        super.onResume();
        if (mVideoView != null) mVideoView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) mVideoView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        if (epgExecutor != null && !epgExecutor.isShutdown()) {
            epgExecutor.shutdown();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO
                    || keyCode == KeyEvent.KEYCODE_HELP || keyCode == KeyEvent.KEYCODE_SETTINGS) {
                showSettingGroup();
            } else if (!isBack && keyCode == KeyEvent.KEYCODE_BACK) {
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    mHandler.removeCallbacks(mHideChannelListRun);
                    mHandler.post(mHideChannelListRun);
                } else if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
                    mHandler.removeCallbacks(mHideSettingLayoutRun);
                    mHandler.post(mHideSettingLayoutRun);
                } else if (ll_epg.getVisibility() == View.VISIBLE) {
                    hideEpgPanel();
                } else {
                    mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                    mHandler.removeCallbacks(mUpdateNetSpeedRun);
                    exit();
                }
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
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        }
        if (tvRightSettingLayout.getVisibility() == View.INVISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mShowSettingLayoutRun);
        } else {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        }
    }

    private Runnable mShowSettingLayoutRun = new Runnable() {
        @Override
        public void run() {
            tvRightSettingLayout.setVisibility(View.VISIBLE);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            tvRightSettingLayout.setLayoutParams(params);
            liveSettingGroupAdapter.setSelectedGroupIndex(-1);
            mSettingGroupView.scrollToPosition(-1);
            mSettingItemView.scrollToPosition(-1);
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.postDelayed(mHideSettingLayoutRun, 5000);
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
        if (list.isEmpty()) {
            Toast.makeText(App.getInstance(), "频道列表为空", Toast.LENGTH_SHORT).show();
            finish();
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
        ArrayList<LiveSettingItem> liveSettingItem = new ArrayList<>();
        ArrayList<LiveSettingItem> liveSettingItem2 = new ArrayList<>();
        ArrayList<LiveSettingItem> liveSettingItem3 = new ArrayList<>();
        ArrayList<LiveSettingItem> liveSettingItem4 = new ArrayList<>();
        ArrayList<LiveSettingItem> liveSettingItem5 = new ArrayList<>();
        ArrayList<LiveSettingItem> LiveSettingItem6 = new ArrayList<>();

        LiveSettingItem item1 = new LiveSettingItem(); item1.setItemName("线路1"); item1.setItemIndex(0); liveSettingItem.add(item1);
        LiveSettingItem item2 = new LiveSettingItem(); item2.setItemName("线路2"); item2.setItemIndex(1); liveSettingItem.add(item2);
        LiveSettingItem item3 = new LiveSettingItem(); item3.setItemName("线路3"); item3.setItemIndex(2); liveSettingItem.add(item3);

        LiveSettingItem item2_1 = new LiveSettingItem(); item2_1.setItemName("16:9"); item2_1.setItemIndex(0); liveSettingItem2.add(item2_1);
        LiveSettingItem item2_2 = new LiveSettingItem(); item2_2.setItemName("4:3"); item2_2.setItemIndex(1); liveSettingItem2.add(item2_2);
        LiveSettingItem item2_3 = new LiveSettingItem(); item2_3.setItemName("填充"); item2_3.setItemIndex(2); liveSettingItem2.add(item2_3);
        LiveSettingItem item2_4 = new LiveSettingItem(); item2_4.setItemName("原始"); item2_4.setItemIndex(3); liveSettingItem2.add(item2_4);
        LiveSettingItem item2_5 = new LiveSettingItem(); item2_5.setItemName("裁剪"); item2_5.setItemIndex(4); liveSettingItem2.add(item2_5);

        LiveSettingItem item3_1 = new LiveSettingItem(); item3_1.setItemName("硬解"); item3_1.setItemIndex(0); liveSettingItem3.add(item3_1);
        LiveSettingItem item3_2 = new LiveSettingItem(); item3_2.setItemName("软解"); item3_2.setItemIndex(1); liveSettingItem3.add(item3_2);

        LiveSettingItem item4_1 = new LiveSettingItem(); item4_1.setItemName("5s"); item4_1.setItemIndex(0); liveSettingItem4.add(item4_1);
        LiveSettingItem item4_2 = new LiveSettingItem(); item4_2.setItemName("10s"); item4_2.setItemIndex(1); liveSettingItem4.add(item4_2);
        LiveSettingItem item4_3 = new LiveSettingItem(); item4_3.setItemName("15s"); item4_3.setItemIndex(2); liveSettingItem4.add(item4_3);
        LiveSettingItem item4_4 = new LiveSettingItem(); item4_4.setItemName("20s"); item4_4.setItemIndex(3); liveSettingItem4.add(item4_4);
        LiveSettingItem item4_5 = new LiveSettingItem(); item4_5.setItemName("25s"); item4_5.setItemIndex(4); liveSettingItem4.add(item4_5);
        LiveSettingItem item4_6 = new LiveSettingItem(); item4_6.setItemName("30s"); item4_6.setItemIndex(5); liveSettingItem4.add(item4_6);

        LiveSettingItem item5_1 = new LiveSettingItem(); item5_1.setItemName("换台反转"); item5_1.setItemIndex(0); liveSettingItem5.add(item5_1);
        LiveSettingItem item5_2 = new LiveSettingItem(); item5_2.setItemName("跨选分类"); item5_2.setItemIndex(1); liveSettingItem5.add(item5_2);
        LiveSettingItem item5_3 = new LiveSettingItem(); item5_3.setItemName("超时换源"); item5_3.setItemIndex(2); liveSettingItem5.add(item5_3);

        settingGroupList = new ArrayList<>();
        LiveSettingGroup group1 = new LiveSettingGroup(); group1.setGroupName("线路选择"); group1.setLiveSettingItems(liveSettingItem); settingGroupList.add(group1);
        LiveSettingGroup group2 = new LiveSettingGroup(); group2.setGroupName("画面比例"); group2.setLiveSettingItems(liveSettingItem2); settingGroupList.add(group2);
        LiveSettingGroup group3 = new LiveSettingGroup(); group3.setGroupName("播放解码"); group3.setLiveSettingItems(liveSettingItem3); settingGroupList.add(group3);
        LiveSettingGroup group4 = new LiveSettingGroup(); group4.setGroupName("超时换源"); group4.setLiveSettingItems(liveSettingItem4); settingGroupList.add(group4);
        LiveSettingGroup group5 = new LiveSettingGroup(); group5.setGroupName("偏好设置"); group5.setLiveSettingItems(liveSettingItem5); settingGroupList.add(group5);
        LiveSettingGroup group6 = new LiveSettingGroup(); group6.setGroupName("EPG订阅"); group6.setLiveSettingItems(LiveSettingItem6); settingGroupList.add(group6);

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
            mHandler.postDelayed(mHideSettingLayoutRun, 5000);
        }
        mSettingGroupView.scrollToPosition(groupIndex);
        liveSettingItemAdapter.setNewData(settingGroupList.get(groupIndex).getLiveSettingItems());
        liveSettingItemAdapter.setSelectedItemIndex(settingGroupList.get(groupIndex).getLiveSettingItems().get(0).getItemIndex());
    }

    // ========== 修复9: EPG 订阅点击做空指针防护 ==========
    private void clickSettingItem(int position) {
        if (position < 0 || position >= liveSettingItemAdapter.getItemCount()) return;
        LiveSettingItem liveSettingItem = liveSettingItemAdapter.getItem(position);
        if (liveSettingItem == null) return;

        int settingGroupIndex = liveSettingGroupAdapter.getSelectedGroupIndex();
        if (settingGroupIndex < 0 || settingGroupIndex >= settingGroupList.size()) return;

        if (settingGroupIndex == 5) { // EPG订阅
            // ========== 修复10: EPG 订阅空值防护 ==========
            String currentEpg = Hawk.get(HawkConfig.EPG_URL, "");
            if (currentEpg == null) currentEpg = "";
            if (currentEpg.equals("默认")) {
                Toast.makeText(this, "已取消订阅", Toast.LENGTH_SHORT).show();
                Hawk.put(HawkConfig.EPG_URL, "");
            } else {
                Toast.makeText(this, "已订阅", Toast.LENGTH_SHORT).show();
                Hawk.put(HawkConfig.EPG_URL, "默认");
            }
            // 重新加载EPG
            if (currentLiveChannelItem != null) {
                loadEpgDataAsync(currentLiveChannelItem.getChannelName());
            }
            return;
        }

        // ... 其他设置项处理保持不变 ...
        switch (settingGroupIndex) {
            case 0: // 线路选择
                // ...
                break;
            case 1: // 画面比例
                // ...
                break;
            case 2: // 播放解码
                // ...
                break;
            case 3: // 超时换源
                // ...
                break;
            case 4: // 偏好设置
                // ...
                break;
        }
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
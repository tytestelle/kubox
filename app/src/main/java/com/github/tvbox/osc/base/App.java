package com.github.tvbox.osc.base;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import androidx.multidex.MultiDexApplication;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;
import com.p2p.P2PClass;
import com.whl.quickjs.android.QuickJSLoader;
import com.github.catvod.crawler.JsLoader;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public class App extends MultiDexApplication {
    private static App instance;

    private static P2PClass p;
    public static String burl;
    private static String dashData;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // V4 diagnostic logger: a NEW filename is used so it cannot be confused with an old APK.
        writeV4("=== KU9 DEBUG V4 APK STARTED ===");
        writeV4("time=" + new java.util.Date());
        writeV4("package=" + getPackageName());
        writeV4("process=" + android.os.Process.myPid());

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, Throwable throwable) {
                writeV4("=== UNCAUGHT EXCEPTION ===");
                writeV4("thread=" + thread.getName());
                writeV4(android.util.Log.getStackTraceString(throwable));
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });

        registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, android.os.Bundle b) {
                writeV4("ACTIVITY CREATED: " + a.getClass().getName());
            }
            @Override public void onActivityStarted(Activity a) {
                writeV4("ACTIVITY STARTED: " + a.getClass().getName());
            }
            @Override public void onActivityResumed(Activity a) {
                writeV4("ACTIVITY RESUMED: " + a.getClass().getName());
            }
            @Override public void onActivityPaused(Activity a) {
                writeV4("ACTIVITY PAUSED: " + a.getClass().getName());
            }
            @Override public void onActivityStopped(Activity a) {
                writeV4("ACTIVITY STOPPED: " + a.getClass().getName());
            }
            @Override public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {
                writeV4("ACTIVITY DESTROYED: " + a.getClass().getName());
            }
        });

        writeV4("APPLICATION INIT BEGIN");
        safeInitV4("initParams", new Runnable() { public void run() { initParams(); }});
        safeInitV4("OkGoHelper", new Runnable() { public void run() { OkGoHelper.init(); }});
        safeInitV4("EpgUtil", new Runnable() { public void run() { EpgUtil.init(); }});
        safeInitV4("ControlManager", new Runnable() { public void run() { ControlManager.init(App.this); }});
        safeInitV4("AppDataManager", new Runnable() { public void run() { AppDataManager.init(); }});
        safeInitV4("LoadSir", new Runnable() { public void run() {
            LoadSir.beginBuilder().addCallback(new EmptyCallback()).addCallback(new LoadingCallback()).commit();
        }});
        safeInitV4("AutoSizeConfig", new Runnable() { public void run() {
            AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                    .setSupportDP(false).setSupportSP(false).setSupportSubunits(Subunits.MM);
        }});
        safeInitV4("PlayerHelper", new Runnable() { public void run() { PlayerHelper.init(); }});
        safeInitV4("QuickJSLoader", new Runnable() { public void run() { QuickJSLoader.init(); }});
        safeInitV4("cleanPlayerCache", new Runnable() { public void run() { FileUtils.cleanPlayerCache(); }});
        writeV4("=== APPLICATION INIT END ===");
    }

    private void safeInitV4(String name, Runnable r) {
        writeV4("INIT BEGIN: " + name);
        try {
            r.run();
            writeV4("INIT OK: " + name);
        } catch (Throwable e) {
            writeV4("INIT FAILED: " + name + "\n" + android.util.Log.getStackTraceString(e));
        }
    }

    private synchronized void writeV4(String message) {
        String content = "\n===== " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                java.util.Locale.US).format(new java.util.Date()) + " =====\n" + message + "\n";
        File[] dirs = new File[] {
                new File("/sdcard/Ku9TVBox"),
                new File("/sdcard/Download/Ku9TVBox"),
                new File("/data/media/0/Ku9TVBox")
        };
        for (File dir : dirs) {
            try {
                if (!dir.exists() && !dir.mkdirs()) continue;
                File f = new File(dir, "ku9_debug_v4.log");
                FileWriter fw = new FileWriter(f, true);
                fw.write(content);
                fw.flush();
                fw.close();
            } catch (Throwable ignored) {}
        }
        try {
            File f = new File(getFilesDir(), "ku9_debug_v4.log");
            FileWriter fw = new FileWriter(f, true);
            fw.write(content);
            fw.close();
        } catch (Throwable ignored) {}
    }



    private void ensureExternalDirs() {
        try {
            // 方式1：通过API获取并创建
            File extFiles = getExternalFilesDir(null);
            if (extFiles != null && !extFiles.exists()) {
                extFiles.mkdirs();
            }
            // 再创建子目录
            if (extFiles != null) {
                File logDir = new File(extFiles.getParentFile(), "files");
                if (!logDir.exists()) logDir.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            // 方式2：直接创建sdcard路径（兼容某些电视盒子）
            File sdcardPath = new File("/sdcard/Android/data/" + getPackageName() + "/files");
            if (!sdcardPath.exists()) {
                sdcardPath.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            // 方式3：直接创建data/media路径
            File mediaPath = new File("/data/media/0/Android/data/" + getPackageName() + "/files");
            if (!mediaPath.exists()) {
                mediaPath.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            // 方式4：创建内部存储的files目录（兜底）
            File internalFiles = getFilesDir();
            if (internalFiles != null && !internalFiles.exists()) {
                internalFiles.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void initParams() {
        // Hawk
        Hawk.init(this).build();
        Hawk.put(HawkConfig.DEBUG_OPEN, false);
        if (!Hawk.contains(HawkConfig.PLAY_TYPE)) {
            Hawk.put(HawkConfig.PLAY_TYPE, 1);
        }
    }

    public static App getInstance() {
        return instance;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        try {
            JsLoader.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private VodInfo vodInfo;
    public void setVodInfo(VodInfo vodinfo){
        this.vodInfo = vodinfo;
    }
    public VodInfo getVodInfo(){
        return this.vodInfo;
    }

    public static P2PClass getp2p() {
        try {
            if (p == null) {
                p = new P2PClass(FileUtils.getExternalCachePath());
            }
            return p;
        } catch (Exception e) {
            LOG.e(e.toString());
            return null;
        }
    }

    public Activity getCurrentActivity() {
        return AppManager.getInstance().currentActivity();
    }

    public void setDashData(String data) {
        dashData = data;
    }
    public String getDashData() {
        return dashData;
    }
}

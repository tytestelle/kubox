package com.github.tvbox.osc.util;

import android.text.TextUtils;
import android.util.Log;

import com.github.tvbox.osc.bean.EpgProgram;
import com.github.tvbox.osc.bean.Epginfo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * EPG 工具类
 */
public class EpgUtil {
    private static final String TAG = "EpgUtil";
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat XML_TIME_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());

    /**
     * 获取当前正在播放的节目
     */
    public static String getCurrentProgram(List<Epginfo> epgList) {
        if (epgList == null || epgList.isEmpty()) return "精彩节目-暂未提供节目预告信息";
        Epginfo epg = epgList.get(0);
        if (epg.getProgram() == null || epg.getProgram().isEmpty()) {
            return "精彩节目-暂未提供节目预告信息";
        }
        Date now = new Date();
        for (EpgProgram program : epg.getProgram()) {
            try {
                Date start = parseTime(program.getStartTime());
                Date end = parseTime(program.getEndTime());
                if (start != null && end != null && now.after(start) && now.before(end)) {
                    String title = program.getTitle();
                    if (TextUtils.isEmpty(title)) title = "精彩节目";
                    return TIME_FORMAT.format(start) + " " + title;
                }
            } catch (Exception e) {
                Log.e(TAG, "getCurrentProgram parse error", e);
            }
        }
        return "精彩节目-暂未提供节目预告信息";
    }

    /**
     * 获取当前和下一个节目
     */
    public static String[] getCurrentAndNextProgram(List<Epginfo> epgList) {
        String[] result = new String[]{"精彩节目-暂未提供节目预告信息", "精彩节目-暂未提供节目预告信息"};
        if (epgList == null || epgList.isEmpty()) return result;
        Epginfo epg = epgList.get(0);
        if (epg.getProgram() == null || epg.getProgram().isEmpty()) return result;

        Date now = new Date();
        List<EpgProgram> programs = epg.getProgram();
        for (int i = 0; i < programs.size(); i++) {
            try {
                Date start = parseTime(programs.get(i).getStartTime());
                Date end = parseTime(programs.get(i).getEndTime());
                if (start != null && end != null && now.after(start) && now.before(end)) {
                    String title = programs.get(i).getTitle();
                    if (TextUtils.isEmpty(title)) title = "精彩节目";
                    result[0] = TIME_FORMAT.format(start) + " " + title;
                    if (i + 1 < programs.size()) {
                        String nextTitle = programs.get(i + 1).getTitle();
                        Date nextStart = parseTime(programs.get(i + 1).getStartTime());
                        if (TextUtils.isEmpty(nextTitle)) nextTitle = "精彩节目";
                        result[1] = (nextStart != null ? TIME_FORMAT.format(nextStart) : "") + " " + nextTitle;
                    }
                    break;
                }
            } catch (Exception e) {
                Log.e(TAG, "getCurrentAndNextProgram parse error", e);
            }
        }
        return result;
    }

    /**
     * 解析时间字符串
     */
    public static Date parseTime(String timeStr) {
        if (TextUtils.isEmpty(timeStr)) return null;
        try {
            // XMLTV 格式: 20240101120000 +0800
            String clean = timeStr.replaceAll("\\s+\\S+$", "").trim();
            if (clean.length() >= 14) {
                return XML_TIME_FORMAT.parse(clean.substring(0, 14));
            }
            // 尝试 HH:mm 格式
            return TIME_FORMAT.parse(timeStr);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * 格式化 XMLTV 时间为显示格式
     */
    public static String formatXmlTime(String xmlTime) {
        Date date = parseTime(xmlTime);
        return date != null ? TIME_FORMAT.format(date) : xmlTime;
    }

    /**
     * 初始化 EPG 工具
     */
    public static void init() {
        // EPG 工具初始化，可在应用启动时调用
    }
}
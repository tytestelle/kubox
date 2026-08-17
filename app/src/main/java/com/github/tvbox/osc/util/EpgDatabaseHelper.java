package com.github.tvbox.osc.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import com.github.tvbox.osc.bean.EpgProgram;
import com.github.tvbox.osc.bean.Epginfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * EPG SQLite 数据库帮助类
 * 表结构：
 * - epg_cache: id, channel_name, programs(json), update_time
 */
public class EpgDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "EpgDatabaseHelper";
    private static final String DATABASE_NAME = "epg_data.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_EPG = "epg_cache";

    private static final String COL_ID = "id";
    private static final String COL_CHANNEL_NAME = "channel_name";
    private static final String COL_PROGRAMS = "programs";
    private static final String COL_UPDATE_TIME = "update_time";

    private final Gson gson = new Gson();

    public EpgDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE IF NOT EXISTS " + TABLE_EPG + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_CHANNEL_NAME + " TEXT UNIQUE, "
                + COL_PROGRAMS + " TEXT, "
                + COL_UPDATE_TIME + " INTEGER"
                + ")";
        db.execSQL(createTable);
        // 创建索引加速查询
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channel_name ON " + TABLE_EPG + "(" + COL_CHANNEL_NAME + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EPG);
        onCreate(db);
    }

    /**
     * 批量插入 EPG 数据（使用事务提升性能）
     */
    public synchronized void insertEpgBatch(List<Epginfo> epgList) {
        if (epgList == null || epgList.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Epginfo epg : epgList) {
                if (epg == null || TextUtils.isEmpty(epg.getName())) continue;
                ContentValues values = new ContentValues();
                values.put(COL_CHANNEL_NAME, epg.getName());
                values.put(COL_PROGRAMS, gson.toJson(epg.getProgram()));
                values.put(COL_UPDATE_TIME, System.currentTimeMillis());
                db.insertWithOnConflict(TABLE_EPG, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "insertEpgBatch error", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 根据频道名查询 EPG
     */
    public synchronized List<Epginfo> getEpgByChannel(String channelName) {
        if (TextUtils.isEmpty(channelName)) return new ArrayList<>();
        List<Epginfo> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_EPG, new String[]{COL_CHANNEL_NAME, COL_PROGRAMS},
                    COL_CHANNEL_NAME + " = ?", new String[]{channelName},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                Epginfo epg = new Epginfo();
                epg.setName(cursor.getString(cursor.getColumnIndexOrThrow(COL_CHANNEL_NAME)));
                String programsJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_PROGRAMS));
                if (!TextUtils.isEmpty(programsJson)) {
                    List<EpgProgram> programs = gson.fromJson(programsJson, new TypeToken<List<EpgProgram>>(){}.getType());
                    epg.setProgram(programs);
                }
                result.add(epg);
            }
        } catch (Exception e) {
            Log.e(TAG, "getEpgByChannel error", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    /**
     * 获取所有 EPG（用于调试）
     */
    public synchronized List<Epginfo> getAllEpg() {
        List<Epginfo> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_EPG, new String[]{COL_CHANNEL_NAME, COL_PROGRAMS},
                    null, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Epginfo epg = new Epginfo();
                    epg.setName(cursor.getString(cursor.getColumnIndexOrThrow(COL_CHANNEL_NAME)));
                    String programsJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_PROGRAMS));
                    if (!TextUtils.isEmpty(programsJson)) {
                        List<EpgProgram> programs = gson.fromJson(programsJson, new TypeToken<List<EpgProgram>>(){}.getType());
                        epg.setProgram(programs);
                    }
                    result.add(epg);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getAllEpg error", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    /**
     * 清空所有 EPG 数据
     */
    public synchronized void clearAllEpg() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.delete(TABLE_EPG, null, null);
        } catch (Exception e) {
            Log.e(TAG, "clearAllEpg error", e);
        }
    }

    /**
     * 获取数据库大小（MB）
     */
    public long getDatabaseSize() {
        SQLiteDatabase db = getReadableDatabase();
        return new java.io.File(db.getPath()).length() / (1024 * 1024);
    }
}

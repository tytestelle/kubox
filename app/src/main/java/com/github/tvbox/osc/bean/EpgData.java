package com.github.tvbox.osc.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;

import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "epg_data", primaryKeys = {"channelName", "date", "idx"})
public class EpgData {
    @NonNull
    public String channelName = "";
    @NonNull
    public String date = "";
    public String title = "";
    public String start = "";
    public String end = "";
    public long startTime = 0;
    public long endTime = 0;
    public int idx = 0;

    // Compatibility fields used by the legacy XML/JSON EPG parser and logo mapper.
    @androidx.room.Ignore
    public String name = "";
    @androidx.room.Ignore
    public String logo = "";
    @androidx.room.Ignore
    public List<String> alias = new ArrayList<>();
    @androidx.room.Ignore
    public List<EpgProgram> program = new ArrayList<>();

    public EpgData() {}

    @androidx.room.Ignore
    public EpgData(String channelName, String date, String title, String start, String end, long startTime, long endTime, int idx) {
        this.channelName = channelName;
        this.date = date;
        this.title = title;
        this.start = start;
        this.end = end;
        this.startTime = startTime;
        this.endTime = endTime;
        this.idx = idx;
    }

    public String getName() { return !name.isEmpty() ? name : channelName; }
    public void setName(String name) { this.name = name == null ? "" : name; this.channelName = this.name; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo == null ? "" : logo; }
    public List<String> getAlias() { return alias; }
    public void setAlias(List<String> alias) { this.alias = alias != null ? alias : new ArrayList<String>(); }
    public List<EpgProgram> getProgram() { return program; }
    public void setProgram(List<EpgProgram> program) { this.program = program != null ? program : new ArrayList<EpgProgram>(); }
}

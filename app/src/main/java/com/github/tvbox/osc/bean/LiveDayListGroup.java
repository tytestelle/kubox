package com.github.tvbox.osc.bean;

import java.util.ArrayList;

/**
 * Ku9 live EPG day/time group.
 * Keeps the original TVBox group fields and adds the list API used by the Ku9 UI.
 */
public class LiveDayListGroup {
    private int groupIndex;
    private String groupName;
    private ArrayList<LiveEpgDate> data = new ArrayList<>();

    public int getGroupIndex() { return groupIndex; }
    public void setGroupIndex(int groupIndex) { this.groupIndex = groupIndex; }

    // Ku9 compatibility aliases.
    public int getIndex() { return groupIndex; }
    public void setIndex(int index) { this.groupIndex = index; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public ArrayList<LiveEpgDate> getData() { return data; }
    public void setData(ArrayList<LiveEpgDate> data) {
        this.data = data == null ? new ArrayList<LiveEpgDate>() : data;
    }
}

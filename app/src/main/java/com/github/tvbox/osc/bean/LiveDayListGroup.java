package com.github.tvbox.osc.bean;

import java.util.ArrayList;

public class LiveDayListGroup {
    private int groupIndex;
    private String groupName;
    private ArrayList<LiveEpgDate> data = new ArrayList<>();


    public int getGroupIndex() {
        return groupIndex;
    }

    public void setGroupIndex(int groupIndex) {
        this.groupIndex = groupIndex;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getIndex() { return groupIndex; }
    public void setIndex(int index) { this.groupIndex = index; }
    public ArrayList<LiveEpgDate> getData() { return data; }
    public void setData(ArrayList<LiveEpgDate> data) { this.data = data != null ? data : new ArrayList<LiveEpgDate>(); }
}


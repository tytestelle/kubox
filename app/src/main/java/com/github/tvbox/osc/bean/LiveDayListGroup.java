package com.github.tvbox.osc.bean;

import java.util.ArrayList;

public class LiveDayListGroup {
    private int index;
    private int groupIndex;
    private String groupName;
    private ArrayList<LiveEpgDate> data = new ArrayList<>();

    public int getGroupIndex() {
        return groupIndex;
    }

    public void setGroupIndex(int groupIndex) {
        this.groupIndex = groupIndex;
        this.index = groupIndex;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public void setIndex(int index) {
        this.index = index;
        this.groupIndex = index;
    }

    public int getIndex() {
        return index;
    }

    public void setData(ArrayList<LiveEpgDate> data) {
        this.data = data;
    }

    public ArrayList<LiveEpgDate> getData() {
        return data;
    }
}

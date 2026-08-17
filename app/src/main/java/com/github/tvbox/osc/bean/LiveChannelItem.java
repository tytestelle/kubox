package com.github.tvbox.osc.bean;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 直播频道项，增强EPG支持
 */
public class LiveChannelItem {
    private int channelIndex;
    private int channelNum;
    private String channelName;
    private String channelSourceNames;
    private ArrayList<String> channelSourceUrls;
    private int sourceIndex = 0;
    private int sourceNum = 0;
    private int sourceState = 0;
    private int previousSelectedChannelGroupIndex = 0;
    private int previousSelectedChannelIndex = 0;
    private int previousSelectedChannelSourceIndex = 0;

    // EPG相关
    private String tvgName;
    private String tvgId;
    private String logoUrl;
    private List<Epginfo> epgdata = new ArrayList<>();
    private LinkedHashMap<String, ArrayList<Epginfo>> epgdataMap = new LinkedHashMap<>();

    public int getChannelIndex() { return channelIndex; }
    public void setChannelIndex(int channelIndex) { this.channelIndex = channelIndex; }
    public int getChannelNum() { return channelNum; }
    public void setChannelNum(int channelNum) { this.channelNum = channelNum; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public String getChannelSourceNames() { return channelSourceNames; }
    public void setChannelSourceNames(String channelSourceNames) { this.channelSourceNames = channelSourceNames; }
    public ArrayList<String> getChannelSourceUrls() { return channelSourceUrls; }
    public void setChannelSourceUrls(ArrayList<String> channelSourceUrls) { this.channelSourceUrls = channelSourceUrls; }
    public int getSourceIndex() { return sourceIndex; }
    public void setSourceIndex(int sourceIndex) { this.sourceIndex = sourceIndex; }
    public int getSourceNum() { return sourceNum; }
    public void setSourceNum(int sourceNum) { this.sourceNum = sourceNum; }
    public int getSourceState() { return sourceState; }
    public void setSourceState(int sourceState) { this.sourceState = sourceState; }
    public int getPreviousSelectedChannelGroupIndex() { return previousSelectedChannelGroupIndex; }
    public void setPreviousSelectedChannelGroupIndex(int previousSelectedChannelGroupIndex) { this.previousSelectedChannelGroupIndex = previousSelectedChannelGroupIndex; }
    public int getPreviousSelectedChannelIndex() { return previousSelectedChannelIndex; }
    public void setPreviousSelectedChannelIndex(int previousSelectedChannelIndex) { this.previousSelectedChannelIndex = previousSelectedChannelIndex; }
    public int getPreviousSelectedChannelSourceIndex() { return previousSelectedChannelSourceIndex; }
    public void setPreviousSelectedChannelSourceIndex(int previousSelectedChannelSourceIndex) { this.previousSelectedChannelSourceIndex = previousSelectedChannelSourceIndex; }

    // EPG getters/setters
    public String getTvgName() { return tvgName; }
    public void setTvgName(String tvgName) { this.tvgName = tvgName; }
    public String getTvgId() { return tvgId; }
    public void setTvgId(String tvgId) { this.tvgId = tvgId; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public List<Epginfo> getEpgdata() { return epgdata; }
    public void setEpgdata(List<Epginfo> epgdata) { this.epgdata = epgdata; }
    public LinkedHashMap<String, ArrayList<Epginfo>> getEpgdataMap() { return epgdataMap; }
    public void setEpgdataMap(LinkedHashMap<String, ArrayList<Epginfo>> epgdataMap) { this.epgdataMap = epgdataMap; }

    public String getSourceName() {
        if (sourceIndex > -1 && channelSourceNames != null && !channelSourceNames.isEmpty()) {
            String[] names = channelSourceNames.split("#");
            if (sourceIndex < names.length) {
                return names[sourceIndex];
            }
        }
        return "";
    }

    public String getUrl() {
        if (sourceIndex > -1 && channelSourceUrls != null && sourceIndex < channelSourceUrls.size()) {
            return channelSourceUrls.get(sourceIndex);
        }
        return "";
    }

    public void nextSource() {
        sourceIndex++;
        if (sourceIndex >= sourceNum) sourceIndex = 0;
    }

    public void preSource() {
        sourceIndex--;
        if (sourceIndex < 0) sourceIndex = sourceNum - 1;
    }
}

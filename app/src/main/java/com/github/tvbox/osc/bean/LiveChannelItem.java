package com.github.tvbox.osc.bean;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/** Live channel model compatible with Ku9 LivePlayActivity and TVBox parser. */
public class LiveChannelItem {
    private int channelIndex;
    private int channelNum;
    private String channelName;
    private ArrayList<String> channelSourceNames = new ArrayList<>();
    private ArrayList<String> channelSourceUrls = new ArrayList<>();
    private int sourceIndex = 0;
    private int sourceNum = 0;
    private int sourceState = 0;
    private int previousSelectedChannelGroupIndex = 0;
    private int previousSelectedChannelIndex = 0;
    private int previousSelectedChannelSourceIndex = 0;
    private String channelLogo = "";
    private String channelEpg = "";
    private String channelUa = "";
    private String channelClick = "";
    private String channelFormat = "";
    private String channelOrigin = "";
    private String channelReferer = "";
    private String channelTvgId = "";
    private String channelTvgName = "";
    private int channelParse = 0;
    private JsonObject channelCatchup;
    private HashMap<String,String> channelHeader = new HashMap<>();
    private List<Epginfo> epgdata = new ArrayList<>();
    private LinkedHashMap<String, ArrayList<Epginfo>> epgdataMap = new LinkedHashMap<>();

    public int getChannelIndex(){return channelIndex;} public void setChannelIndex(int v){channelIndex=v;}
    public int getChannelNum(){return channelNum;} public void setChannelNum(int v){channelNum=v;}
    public String getChannelName(){return channelName;} public void setChannelName(String v){channelName=v;}
    public ArrayList<String> getChannelSourceNames(){return channelSourceNames;}
    public void setChannelSourceNames(ArrayList<String> v){channelSourceNames=v==null?new ArrayList<>():v;}
    public ArrayList<String> getChannelSourceUrls(){return channelSourceUrls;}
    public void setChannelSourceUrls(ArrayList<String> v){channelSourceUrls=v==null?new ArrayList<>():v;}
    public ArrayList<String> getChannelUrls(){return channelSourceUrls;} public void setChannelUrls(ArrayList<String> v){setChannelSourceUrls(v);}
    public int getSourceIndex(){return sourceIndex;} public void setSourceIndex(int v){sourceIndex=v;}
    public int getSourceNum(){return sourceNum;} public void setSourceNum(int v){sourceNum=v;}
    public int getSourceState(){return sourceState;} public void setSourceState(int v){sourceState=v;}
    public int getPreviousSelectedChannelGroupIndex(){return previousSelectedChannelGroupIndex;} public void setPreviousSelectedChannelGroupIndex(int v){previousSelectedChannelGroupIndex=v;}
    public int getPreviousSelectedChannelIndex(){return previousSelectedChannelIndex;} public void setPreviousSelectedChannelIndex(int v){previousSelectedChannelIndex=v;}
    public int getPreviousSelectedChannelSourceIndex(){return previousSelectedChannelSourceIndex;} public void setPreviousSelectedChannelSourceIndex(int v){previousSelectedChannelSourceIndex=v;}
    public String getChannelLogo(){return channelLogo;} public void setChannelLogo(String v){channelLogo=v;}
    public String getChannelEpg(){return channelEpg;} public void setChannelEpg(String v){channelEpg=v;}
    public String getChannelUa(){return channelUa;} public void setChannelUa(String v){channelUa=v;}
    public String getChannelClick(){return channelClick;} public void setChannelClick(String v){channelClick=v;}
    public String getChannelFormat(){return channelFormat;} public void setChannelFormat(String v){channelFormat=v;}
    public String getChannelOrigin(){return channelOrigin;} public void setChannelOrigin(String v){channelOrigin=v;}
    public String getChannelReferer(){return channelReferer;} public void setChannelReferer(String v){channelReferer=v;}
    public String getChannelTvgId(){return channelTvgId;} public void setChannelTvgId(String v){channelTvgId=v;}
    public String getChannelTvgName(){return channelTvgName;} public void setChannelTvgName(String v){channelTvgName=v;}
    public int getChannelParse(){return channelParse;} public void setChannelParse(int v){channelParse=v;}
    public JsonObject getChannelCatchup(){return channelCatchup;} public void setChannelCatchup(JsonObject v){channelCatchup=v;}
    public HashMap<String,String> getChannelHeader(){return channelHeader;} public void setChannelHeader(HashMap<String,String> v){channelHeader=v==null?new HashMap<>():v;}
    public String getTvgName(){return channelTvgName;} public void setTvgName(String v){channelTvgName=v;}
    public String getTvgId(){return channelTvgId;} public void setTvgId(String v){channelTvgId=v;}
    public String getLogoUrl(){return channelLogo;} public void setLogoUrl(String v){channelLogo=v;}
    public List<Epginfo> getEpgdata(){return epgdata;} public void setEpgdata(List<Epginfo> v){epgdata=v;}
    public LinkedHashMap<String,ArrayList<Epginfo>> getEpgdataMap(){return epgdataMap;} public void setEpgdataMap(LinkedHashMap<String,ArrayList<Epginfo>> v){epgdataMap=v;}
    public String getSourceName(){return sourceIndex>=0&&sourceIndex<channelSourceNames.size()?channelSourceNames.get(sourceIndex):"";}
    public String getUrl(){return sourceIndex>=0&&sourceIndex<channelSourceUrls.size()?channelSourceUrls.get(sourceIndex):"";}
    public void nextSource(){if(!channelSourceUrls.isEmpty()){sourceIndex=(sourceIndex+1)%channelSourceUrls.size();}}
    public void preSource(){if(!channelSourceUrls.isEmpty()){sourceIndex=(sourceIndex-1+channelSourceUrls.size())%channelSourceUrls.size();}}
}

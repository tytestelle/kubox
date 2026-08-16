package com.github.tvbox.osc.util;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * EPG 频道名称模糊匹配器
 * 处理各种频道命名差异，如 "CCTV-1" vs "CCTV1" vs "央视综合"
 */
public class EpgNameFuzzyMatch {
    private static EpgNameFuzzyMatch instance;
    private final Map<String, String> nameMapping = new HashMap<>();

    private EpgNameFuzzyMatch() {
        initDefaultMappings();
    }

    public static synchronized EpgNameFuzzyMatch getInstance() {
        if (instance == null) {
            instance = new EpgNameFuzzyMatch();
        }
        return instance;
    }

    private void initDefaultMappings() {
        // CCTV 系列
        nameMapping.put("cctv1", "CCTV1");
        nameMapping.put("cctv-1", "CCTV1");
        nameMapping.put("cctv2", "CCTV2");
        nameMapping.put("cctv-2", "CCTV2");
        nameMapping.put("cctv3", "CCTV3");
        nameMapping.put("cctv-3", "CCTV3");
        nameMapping.put("cctv4", "CCTV4");
        nameMapping.put("cctv-4", "CCTV4");
        nameMapping.put("cctv5", "CCTV5");
        nameMapping.put("cctv-5", "CCTV5");
        nameMapping.put("cctv5+", "CCTV5+");
        nameMapping.put("cctv6", "CCTV6");
        nameMapping.put("cctv-6", "CCTV6");
        nameMapping.put("cctv7", "CCTV7");
        nameMapping.put("cctv-7", "CCTV7");
        nameMapping.put("cctv8", "CCTV8");
        nameMapping.put("cctv-8", "CCTV8");
        nameMapping.put("cctv9", "CCTV9");
        nameMapping.put("cctv-9", "CCTV9");
        nameMapping.put("cctv10", "CCTV10");
        nameMapping.put("cctv-10", "CCTV10");
        nameMapping.put("cctv11", "CCTV11");
        nameMapping.put("cctv-11", "CCTV11");
        nameMapping.put("cctv12", "CCTV12");
        nameMapping.put("cctv-12", "CCTV12");
        nameMapping.put("cctv13", "CCTV13");
        nameMapping.put("cctv-13", "CCTV13");
        nameMapping.put("cctv14", "CCTV14");
        nameMapping.put("cctv-14", "CCTV14");
        nameMapping.put("cctv15", "CCTV15");
        nameMapping.put("cctv-15", "CCTV15");
        nameMapping.put("cctv16", "CCTV16");
        nameMapping.put("cctv-16", "CCTV16");
        nameMapping.put("cctv17", "CCTV17");
        nameMapping.put("cctv-17", "CCTV17");
        nameMapping.put("cctv4k", "CCTV4K");
        nameMapping.put("cctv-4k", "CCTV4K");
        nameMapping.put("cctv8k", "CCTV8K");
        nameMapping.put("cctv-8k", "CCTV8K");

        // 卫视系列
        nameMapping.put("湖南卫视", "湖南卫视");
        nameMapping.put("浙江卫视", "浙江卫视");
        nameMapping.put("东方卫视", "东方卫视");
        nameMapping.put("江苏卫视", "江苏卫视");
        nameMapping.put("北京卫视", "北京卫视");
        nameMapping.put("深圳卫视", "深圳卫视");
        nameMapping.put("广东卫视", "广东卫视");
        nameMapping.put("山东卫视", "山东卫视");
        nameMapping.put("天津卫视", "天津卫视");
        nameMapping.put("四川卫视", "四川卫视");
        nameMapping.put("湖北卫视", "湖北卫视");
        nameMapping.put("安徽卫视", "安徽卫视");
        nameMapping.put("辽宁卫视", "辽宁卫视");
        nameMapping.put("重庆卫视", "重庆卫视");
        nameMapping.put("黑龙江卫视", "黑龙江卫视");
        nameMapping.put("河北卫视", "河北卫视");
        nameMapping.put("河南卫视", "河南卫视");
        nameMapping.put("江西卫视", "江西卫视");
        nameMapping.put("广西卫视", "广西卫视");
        nameMapping.put("吉林卫视", "吉林卫视");
        nameMapping.put("海南卫视", "海南卫视");
        nameMapping.put("贵州卫视", "贵州卫视");
        nameMapping.put("云南卫视", "云南卫视");
        nameMapping.put("东南卫视", "东南卫视");
        nameMapping.put("甘肃卫视", "甘肃卫视");
        nameMapping.put("内蒙古卫视", "内蒙古卫视");
        nameMapping.put("宁夏卫视", "宁夏卫视");
        nameMapping.put("青海卫视", "青海卫视");
        nameMapping.put("新疆卫视", "新疆卫视");
        nameMapping.put("西藏卫视", "西藏卫视");
        nameMapping.put("陕西卫视", "陕西卫视");
        nameMapping.put("山西卫视", "山西卫视");
    }

    /**
     * 获取映射后的频道名称
     */
    public String getMappedName(String channelName) {
        if (TextUtils.isEmpty(channelName)) return channelName;
        String key = channelName.toLowerCase().replaceAll("\\s+", "").replaceAll("hd$", "").replaceAll("\\+$", "+");
        String mapped = nameMapping.get(key);
        if (!TextUtils.isEmpty(mapped)) return mapped;

        // 尝试从 epg_data.json 获取映射
        EpgDataJsonParser parser = EpgDataJsonParser.getInstance();
        String jsonMapped = parser.getMappedChannelName(channelName);
        if (!TextUtils.isEmpty(jsonMapped)) return jsonMapped;

        return channelName;
    }

    /**
     * 添加自定义映射
     */
    public void addMapping(String source, String target) {
        if (!TextUtils.isEmpty(source) && !TextUtils.isEmpty(target)) {
            nameMapping.put(source.toLowerCase(), target);
        }
    }
}

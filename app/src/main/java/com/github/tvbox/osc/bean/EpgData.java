package com.github.tvbox.osc.bean;

import java.util.List;

/**
 * epg_data.json 对应的 Bean
 */
public class EpgData {
    private String name;
    private String logo;
    private String url;
    private String epg;
    private List<String> alias;
    private List<EpgProgram> program;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEpg() {
        return epg;
    }

    public void setEpg(String epg) {
        this.epg = epg;
    }

    public List<String> getAlias() {
        return alias;
    }

    public void setAlias(List<String> alias) {
        this.alias = alias;
    }

    public List<EpgProgram> getProgram() {
        return program;
    }

    public void setProgram(List<EpgProgram> program) {
        this.program = program;
    }
}

package com.github.tvbox.osc.bean;

import java.util.Date;
import java.util.List;

/**
 * EPG 信息 Bean
 */
public class Epginfo {
    private String name;
    private List<EpgProgram> program;

    // Adapter 需要的公共字段
    public String title;
    public String start;
    public String end;
    public int index;
    public String currentEpgDate;
    public Date startdateTime;
    public Date enddateTime;
    public String url;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<EpgProgram> getProgram() {
        return program;
    }

    public void setProgram(List<EpgProgram> program) {
        this.program = program;
    }
}

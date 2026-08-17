package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Epginfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * EPG列表适配器
 */
public class EpgListAdapter extends RecyclerView.Adapter<EpgListAdapter.ViewHolder> {

    private LinkedHashMap<String, ArrayList<Epginfo>> epgMap = new LinkedHashMap<>();
    private List<String> dateList = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Epginfo epginfo);
    }

    public void setData(LinkedHashMap<String, ArrayList<Epginfo>> data) {
        this.epgMap = data != null ? data : new LinkedHashMap<>();
        this.dateList = new ArrayList<>(epgMap.keySet());
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_epg, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String date = dateList.get(position);
        ArrayList<Epginfo> programs = epgMap.get(date);

        holder.tvDate.setText(date);

        // 构建节目字符串
        StringBuilder sb = new StringBuilder();
        if (programs != null) {
            for (int i = 0; i < Math.min(programs.size(), 5); i++) {
                Epginfo epg = programs.get(i);
                sb.append(epg.getStartTime()).append(" ").append(epg.getTitle()).append("\n");
            }
        }
        holder.tvPrograms.setText(sb.toString().trim());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null && programs != null && !programs.isEmpty()) {
                // 找到当前正在播放的节目
                long now = System.currentTimeMillis();
                for (Epginfo epg : programs) {
                    if (epg.getStartTimeL() <= now && epg.getEndTimeL() >= now) {
                        listener.onItemClick(epg);
                        break;
                    }
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return dateList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;
        TextView tvPrograms;

        ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvPrograms = itemView.findViewById(R.id.tvPrograms);
        }
    }
}

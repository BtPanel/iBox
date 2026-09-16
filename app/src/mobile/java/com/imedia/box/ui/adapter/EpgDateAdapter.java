package com.imedia.box.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.imedia.box.R;
import com.imedia.box.bean.Epg;
import com.imedia.box.databinding.AdapterEpgDateBinding;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EpgDateAdapter extends RecyclerView.Adapter<EpgDateAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Epg> items = new ArrayList<>();
    private final DateTimeFormatter monthDay = DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault());
    private final DateTimeFormatter week = DateTimeFormatter.ofPattern("E", Locale.getDefault());
    private LocalDate today;
    private Epg selected;

    public EpgDateAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void addAll(List<Epg> values, Epg current, LocalDate today) {
        items.clear();
        items.addAll(values);
        selected = current;
        this.today = today;
        notifyDataSetChanged();
    }

    public void setSelected(Epg item) {
        selected = item;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterEpgDateBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Epg item = items.get(position);
        LocalDate date = parse(item.getDate());
        holder.binding.date.setText(date == null ? item.getDate() : getDateLabel(holder, date));
        holder.binding.summary.setText(date == null ? "" : monthDay.format(date));
        boolean isSelected = item == selected;
        holder.binding.getRoot().setSelected(isSelected);
        holder.binding.date.setSelected(isSelected);
        holder.binding.summary.setSelected(isSelected);
        holder.binding.getRoot().setOnClickListener(view -> listener.selectDate(item));
    }

    private LocalDate parse(String date) {
        try {
            return LocalDate.parse(date);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getDateLabel(ViewHolder holder, LocalDate date) {
        long offset = ChronoUnit.DAYS.between(today, date);
        if (offset == 0) return holder.itemView.getContext().getString(R.string.today);
        if (offset == 1) return holder.itemView.getContext().getString(R.string.tomorrow);
        if (offset == 2) return holder.itemView.getContext().getString(R.string.day_after_tomorrow);
        return week.format(date);
    }

    public interface OnClickListener {

        void selectDate(Epg item);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpgDateBinding binding;

        ViewHolder(@NonNull AdapterEpgDateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

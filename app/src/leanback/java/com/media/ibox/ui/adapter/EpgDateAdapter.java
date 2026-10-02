package com.media.ibox.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.media.ibox.R;
import com.media.ibox.bean.Epg;
import com.media.ibox.databinding.AdapterEpgDateBinding;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class EpgDateAdapter extends RecyclerView.Adapter<EpgDateAdapter.ViewHolder> {

    private static final DateTimeFormatter SOURCE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private final OnClickListener listener;
    private final List<Epg> items;
    private final DateTimeFormatter monthDay;
    private final DateTimeFormatter week;
    private Epg selected;
    private int itemHeight;

    public EpgDateAdapter(OnClickListener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();
        this.monthDay = DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault());
        this.week = DateTimeFormatter.ofPattern("E", Locale.getDefault());
    }

    public void addAll(List<Epg> values, Epg current) {
        items.clear();
        items.addAll(values);
        items.sort(Comparator.comparing(Epg::getDate));
        selected = current;
        notifyDataSetChanged();
    }

    public Epg get(int position) {
        return items.get(position);
    }

    public void clear() {
        items.clear();
        selected = null;
        notifyDataSetChanged();
    }

    public int indexOf(Epg item) {
        return items.indexOf(item);
    }

    public void setSelected(Epg item) {
        selected = item;
        notifyDataSetChanged();
    }

    public void setAvailableHeight(int height) {
        if (height <= 0 || items.isEmpty()) return;
        int value = height / items.size();
        if (value == itemHeight) return;
        itemHeight = value;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterEpgDateBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Epg item = items.get(position);
        if (itemHeight > 0) {
            ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
            params.height = itemHeight;
            holder.binding.getRoot().setLayoutParams(params);
        }
        LocalDate date = parse(item.getDate());
        holder.binding.date.setText(date == null ? item.getDate() : getDateLabel(holder, date));
        holder.binding.week.setText(date == null ? "" : monthDay.format(date));
        holder.binding.getRoot().setSelected(item == selected);
        holder.binding.getRoot().setLeftListener(listener::focusGuideEntry);
        holder.binding.getRoot().setRightListener(listener::focusPrograms);
        holder.binding.getRoot().setOnClickListener(view -> listener.selectDate(item));
    }

    private LocalDate parse(String date) {
        try {
            return LocalDate.parse(date, SOURCE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getDateLabel(ViewHolder holder, LocalDate date) {
        long offset = ChronoUnit.DAYS.between(LocalDate.now(), date);
        if (offset == 0) return holder.itemView.getContext().getString(R.string.today);
        if (offset == 1) return holder.itemView.getContext().getString(R.string.tomorrow);
        if (offset == 2) return holder.itemView.getContext().getString(R.string.day_after_tomorrow);
        return week.format(date);
    }

    public interface OnClickListener {

        void selectDate(Epg item);

        void focusGuideEntry();

        void focusPrograms();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpgDateBinding binding;

        ViewHolder(@NonNull AdapterEpgDateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

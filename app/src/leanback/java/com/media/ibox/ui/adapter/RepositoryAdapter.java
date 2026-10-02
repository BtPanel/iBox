package com.media.ibox.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.media.ibox.R;
import com.media.ibox.bean.Config;
import com.media.ibox.databinding.AdapterRepositoryBinding;

import java.util.ArrayList;
import java.util.List;

public class RepositoryAdapter extends RecyclerView.Adapter<RepositoryAdapter.ViewHolder> {

    public interface Listener {
        void onSelect(Config item);

        void onEdit(Config item);

        void onDelete(Config item);
    }

    private final Listener listener;
    private final List<Config> items = new ArrayList<>();
    private int selectedId;

    public RepositoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Config> values, Config selected) {
        items.clear();
        if (values != null) items.addAll(values);
        selectedId = selected == null ? -1 : selected.getId();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterRepositoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Config item = items.get(position);
        boolean selected = item.getId() == selectedId;
        holder.binding.name.setText(item.getDesc());
        holder.binding.url.setText(item.getUrl());
        holder.binding.getRoot().setSelected(selected);
        holder.binding.selected.setSelected(selected);
        holder.binding.selected.setImageResource(selected ? R.drawable.ic_msr_check_circle : R.drawable.ic_msr_radio_button_unchecked);
        holder.binding.selected.setContentDescription(holder.itemView.getContext().getString(selected ? R.string.repository_selected : R.string.setting_choose));
        holder.binding.getRoot().setOnClickListener(view -> listener.onSelect(item));
        holder.binding.selected.setOnClickListener(view -> listener.onSelect(item));
        holder.binding.edit.setOnClickListener(view -> listener.onEdit(item));
        holder.binding.delete.setOnClickListener(view -> listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final AdapterRepositoryBinding binding;

        ViewHolder(AdapterRepositoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

package com.media.ibox.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.media.ibox.databinding.AdapterLiveChannelSearchBinding;
import com.media.ibox.live.LiveChannelSearch;

import java.util.ArrayList;
import java.util.List;

public class LiveChannelSearchAdapter extends RecyclerView.Adapter<LiveChannelSearchAdapter.ViewHolder> {

    public interface Listener {
        void onClick(LiveChannelSearch.Match match);
    }

    private final List<LiveChannelSearch.Match> items = new ArrayList<>();
    private final Listener listener;

    public LiveChannelSearchAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<LiveChannelSearch.Match> result) {
        items.clear();
        items.addAll(result);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterLiveChannelSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LiveChannelSearch.Match item = items.get(position);
        holder.binding.number.setText(item.channel().getNumber());
        holder.binding.name.setText(item.channel().getShow());
        holder.binding.group.setText(item.group().getName());
        holder.binding.getRoot().setOnClickListener(view -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterLiveChannelSearchBinding binding;

        ViewHolder(AdapterLiveChannelSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

package com.media.ibox.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.media.ibox.R;
import com.media.ibox.bean.Channel;
import com.media.ibox.databinding.AdapterLineBinding;

public class LineAdapter extends RecyclerView.Adapter<LineAdapter.ViewHolder> {

    private final OnClickListener listener;
    private Channel channel;

    public LineAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onLineClick(int position);
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        if (channel == null) return;
        channel.setIndex(position);
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public int getItemCount() {
        return channel == null ? 0 : channel.getUrls().size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterLineBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String[] parts = channel.getUrls().get(position).split("\\$", 2);
        String fallback = holder.itemView.getResources().getString(R.string.live_line, String.valueOf(position + 1));
        holder.binding.name.setText(parts.length > 1 && !parts[1].isEmpty() ? parts[1] : fallback);
        holder.binding.name.setSelected(channel.getIndex() == position);
        holder.binding.name.setOnClickListener(view -> listener.onLineClick(holder.getBindingAdapterPosition()));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterLineBinding binding;

        ViewHolder(@NonNull AdapterLineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

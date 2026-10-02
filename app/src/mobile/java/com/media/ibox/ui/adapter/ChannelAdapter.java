package com.media.ibox.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.media.ibox.R;
import com.media.ibox.bean.Channel;
import com.media.ibox.databinding.AdapterChannelBinding;
import com.media.ibox.live.LiveChannelStore;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private static final Object PAYLOAD_SELECTION = new Object();
    private static final Object PAYLOAD_STATUS = new Object();
    private final OnClickListener listener;
    private final List<Channel> mItems;

    public ChannelAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Channel item);

        boolean onLongClick(Channel item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int position = mItems.indexOf(item);
        if (position == -1) return;
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    public void refresh(Channel item) {
        int position = mItems.indexOf(item);
        if (position >= 0) notifyItemChanged(position, PAYLOAD_STATUS);
    }

    public void setSelected(int position) {
        if (position < 0 || position >= mItems.size()) return;
        int previous = findSelectedPosition();
        if (previous != position && previous >= 0) {
            mItems.get(previous).setSelected(false);
            notifyItemChanged(previous, PAYLOAD_SELECTION);
        }
        mItems.get(position).setSelected(true);
        if (previous != position) notifyItemChanged(position, PAYLOAD_SELECTION);
    }

    public int setSelected(Channel channel) {
        int position = mItems.indexOf(channel);
        setSelected(position);
        return position;
    }

    private int findSelectedPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return -1;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ChannelAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelAdapter.ViewHolder holder, int position) {
        Channel item = mItems.get(position);
        item.loadLogo(holder.binding.logo);
        holder.binding.name.setText(item.getShow());
        holder.binding.number.setText(item.getNumber());
        holder.binding.lines.setText(holder.itemView.getResources().getString(R.string.live_line_count, item.getUrls().size()));
        bindStatus(holder, item);
        bindSelection(holder, item);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLongClick(item));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }
        Channel item = mItems.get(position);
        if (payloads.contains(PAYLOAD_STATUS)) bindStatus(holder, item);
        if (payloads.contains(PAYLOAD_SELECTION)) bindSelection(holder, item);
    }

    private void bindStatus(ViewHolder holder, Channel item) {
        holder.binding.status.setVisibility(LiveChannelStore.isFailed(item) ? View.VISIBLE : View.GONE);
    }

    private void bindSelection(ViewHolder holder, Channel item) {
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.number.setSelected(item.isSelected());
        holder.binding.name.setSelected(item.isSelected());
        holder.binding.lines.setSelected(item.isSelected());
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.logo).clear(holder.binding.logo);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterChannelBinding binding;

        ViewHolder(@NonNull AdapterChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

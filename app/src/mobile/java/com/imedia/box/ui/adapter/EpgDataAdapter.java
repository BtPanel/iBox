package com.imedia.box.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.imedia.box.R;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.EpgData;
import com.imedia.box.databinding.AdapterEpgDataBinding;
import com.imedia.box.live.EpgReminder;

import java.util.ArrayList;
import java.util.List;

public class EpgDataAdapter extends RecyclerView.Adapter<EpgDataAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<EpgData> mItems;
    private Channel channel;

    public EpgDataAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(EpgData item);
    }

    public void clear() {
        channel = null;
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(Channel channel, List<EpgData> items) {
        this.channel = channel;
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setSelected(EpgData item) {
        setSelected(mItems.indexOf(item));
    }

    public void setSelected(int position) {
        if (position < 0 || position >= mItems.size()) return;
        int previous = findSelectedPosition();
        if (previous != position && previous >= 0) {
            mItems.get(previous).setSelected(false);
            notifyItemChanged(previous);
        }
        mItems.get(position).setSelected(true);
        if (previous != position) notifyItemChanged(position);
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
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterEpgDataBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EpgData item = mItems.get(position);
        holder.binding.time.setText(item.getTime());
        holder.binding.title.setText(item.getTitle());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.title.setSelected(item.isSelected());
        holder.binding.time.setSelected(item.isSelected());
        boolean reminded = channel != null && item.isFuture() && EpgReminder.isScheduled(channel, item);
        holder.binding.dot.setActivated(item.isInRange() || reminded);
        holder.binding.status.setVisibility(item.isFuture() || item.isInRange() ? View.VISIBLE : View.GONE);
        holder.binding.status.setSelected(item.isInRange() || reminded);
        holder.binding.status.setText(item.isInRange() ? R.string.live_epg_now : reminded ? R.string.live_epg_reminder_enabled : R.string.live_epg_reminder_action);
        holder.binding.getRoot().setContentDescription(item.getTime() + ", " + item.getTitle() + (item.isFuture() ? ", " + holder.binding.status.getText() : ""));
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpgDataBinding binding;

        ViewHolder(@NonNull AdapterEpgDataBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

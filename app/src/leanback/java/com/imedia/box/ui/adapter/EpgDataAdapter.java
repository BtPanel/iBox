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

    private final OnClickListener mListener;
    private final List<EpgData> mItems;
    private Channel channel;

    public EpgDataAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(Channel channel, List<EpgData> items) {
        this.channel = channel;
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        channel = null;
        mItems.clear();
        notifyDataSetChanged();
    }

    public EpgData get(int position) {
        return mItems.get(position);
    }

    public void setSelected(EpgData selected) {
        int position = mItems.indexOf(selected);
        if (position < 0) return;
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
        boolean future = !item.isPlaceholder() && item.isFuture();
        boolean reminded = future && channel != null && EpgReminder.isScheduled(channel, item);
        boolean current = !item.isPlaceholder() && item.isInRange();
        boolean past = !item.isPlaceholder() && !future && !current;
        boolean replayAvailable = past && channel != null && (channel.hasCatchup() || channel.isRtsp());
        boolean showStatus = current || future || past;
        holder.binding.dot.setActivated(current || reminded || replayAvailable);
        holder.binding.status.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        holder.binding.status.setSelected(current || reminded || replayAvailable);
        holder.binding.status.setActivated(replayAvailable);
        holder.binding.status.setText(current ? R.string.live_epg_now : past ? R.string.live_epg_replay : reminded ? R.string.live_epg_reminder_enabled : R.string.live_epg_reminder_action);
        holder.binding.getRoot().setContentDescription(item.getTime() + ", " + item.getTitle() + (showStatus ? ", " + holder.binding.status.getText() : ""));
        holder.binding.getRoot().setLeftListener(mListener::focusEpgDate);
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    public interface OnClickListener {

        void focusEpgDate();

        void onItemClick(EpgData item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpgDataBinding binding;

        ViewHolder(@NonNull AdapterEpgDataBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

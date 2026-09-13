package com.imedia.box.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.imedia.box.R;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.EpgData;
import com.imedia.box.databinding.AdapterChannelBinding;
import com.imedia.box.live.LiveProgramSchedule;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private static final Object PAYLOAD_SELECTION = new Object();
    private static final Object PAYLOAD_CONTENT = new Object();
    private final OnClickListener mListener;
    private final List<Channel> mItems;

    public ChannelAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int index = mItems.indexOf(item);
        if (index < 0) return;
        mItems.remove(index);
        notifyItemRemoved(index);
    }

    public void refresh(Channel item) {
        int position = mItems.indexOf(item);
        if (position >= 0) notifyItemChanged(position, PAYLOAD_CONTENT);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public Channel get(int position) {
        return mItems.get(position);
    }

    public void setSelected(Channel selected) {
        int position = mItems.indexOf(selected);
        if (position < 0) return;
        int previous = findSelectedPosition();
        if (previous != position && previous >= 0) {
            mItems.get(previous).setSelected(false);
            notifyItemChanged(previous, PAYLOAD_SELECTION);
        }
        mItems.get(position).setSelected(true);
        if (previous != position) notifyItemChanged(position, PAYLOAD_SELECTION);
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
        return new ViewHolder(AdapterChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel item = mItems.get(position);
        boolean showLogo = LiveSetting.isShowChannelLogo();
        boolean showNumber = LiveSetting.isShowChannelNumber();
        holder.binding.logo.setVisibility(showLogo ? View.VISIBLE : View.GONE);
        holder.binding.number.setVisibility(showNumber ? View.VISIBLE : View.GONE);
        if (showLogo) item.loadLogo(holder.binding.logo);
        else Glide.with(holder.binding.logo).clear(holder.binding.logo);
        holder.binding.name.setText(item.getShow());
        holder.binding.number.setText(item.getNumber());
        bindProgram(holder, item);
        bindSelection(holder, item);
        holder.binding.getRoot().setLeftListener(mListener::focusGroup);
        holder.binding.getRoot().setRightListener(mListener::openEpg);
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(v -> mListener.onLongClick(item));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }
        Channel item = mItems.get(position);
        if (payloads.contains(PAYLOAD_CONTENT)) bindProgram(holder, item);
        if (payloads.contains(PAYLOAD_SELECTION)) bindSelection(holder, item);
    }

    private void bindProgram(ViewHolder holder, Channel item) {
        int epgMode = LiveSetting.getChannelEpgMode();
        LiveProgramSchedule.Snapshot schedule = LiveProgramSchedule.select(item, System.currentTimeMillis());
        EpgData current = schedule.current();
        EpgData next = schedule.next();
        boolean showNext = epgMode > 0 && next != null;
        holder.binding.summary.setVisibility(epgMode > 0 ? View.VISIBLE : View.GONE);
        holder.binding.next.setVisibility(showNext ? View.VISIBLE : View.GONE);
        holder.binding.getRoot().getLayoutParams().height = ResUtil.dp2px(LiveSetting.isCompactList()
                ? showNext ? 76 : 56 : showNext ? 84 : 64);
        holder.binding.summary.setText(current == null ? holder.itemView.getContext().getString(R.string.live_epg_empty)
                : holder.itemView.getContext().getString(R.string.live_epg_current_format,
                epgMode == 1 ? current.getTitle() : current.format()));
        if (showNext) holder.binding.next.setText(holder.itemView.getContext().getString(R.string.live_epg_next_format,
                epgMode == 1 ? next.getTitle() : next.format()));
    }

    private void bindSelection(ViewHolder holder, Channel item) {
        holder.binding.getRoot().setSelected(item.isSelected());
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.logo).clear(holder.binding.logo);
    }

    public interface OnClickListener {

        void focusGroup();

        void openEpg();

        void showEpg(Channel item);

        void onItemClick(Channel item);

        boolean onLongClick(Channel item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterChannelBinding binding;

        ViewHolder(@NonNull AdapterChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

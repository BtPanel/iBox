package com.imedia.box.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.imedia.box.bean.Danmaku;
import com.imedia.box.databinding.AdapterDanmakuBinding;
import com.imedia.box.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class DanmakuAdapter extends RecyclerView.Adapter<DanmakuAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Danmaku> mItems;

    public DanmakuAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Danmaku item);
    }

    public void clear() {
        int size = mItems.size();
        mItems.clear();
        notifyItemRangeRemoved(0, size);
    }

    public DanmakuAdapter addAll(List<Danmaku> items) {
        if (items == null || items.isEmpty()) return this;
        mItems.addAll(items);
        notifyItemRangeInserted(0, mItems.size());
        return this;
    }

    public int getSelected() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDanmakuBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Danmaku item = mItems.get(position);
        String name = item.getName();
        int sourceEnd = name.startsWith("【") ? name.indexOf('】') : -1;
        holder.binding.source.setVisibility(sourceEnd > 1 ? View.VISIBLE : View.GONE);
        if (sourceEnd > 1) {
            holder.binding.source.setText(name.substring(1, sourceEnd));
            name = name.substring(sourceEnd + 1).trim();
        }
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.binding.text.getLayoutParams();
        params.setMarginStart(sourceEnd > 1 ? ResUtil.dp2px(10) : 0);
        holder.binding.text.setText(name);
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.episode.setText(item.getEpisode());
        holder.binding.episode.setVisibility(item.getEpisode().isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.getRoot().setSelected(item.isSelected());
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterDanmakuBinding binding;

        public ViewHolder(@NonNull AdapterDanmakuBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            listener.onItemClick(mItems.get(getLayoutPosition()));
        }
    }
}

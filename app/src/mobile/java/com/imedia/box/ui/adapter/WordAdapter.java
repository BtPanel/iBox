package com.imedia.box.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.imedia.box.bean.Word;
import com.imedia.box.databinding.AdapterSearchWordBinding;
import com.imedia.box.R;
import com.imedia.box.utils.ResUtil;

public class WordAdapter extends BaseDiffAdapter<Word.Data, WordAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final int[] rankColors = {0xFFFE2D46, 0xFFFF6600, 0xFFF7B500};
    private boolean ranked;

    public WordAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(String text);
    }

    public void setRanked(boolean ranked) {
        if (this.ranked == ranked) return;
        this.ranked = ranked;
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchWordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Word.Data item = getItem(position);
        holder.binding.rank.setVisibility(ranked ? View.VISIBLE : View.GONE);
        holder.binding.rank.setText(String.valueOf(position + 1));
        if (position < rankColors.length) holder.binding.rank.setTextColor(rankColors[position]);
        else holder.binding.rank.setTextColor(ResUtil.getColor(R.color.ibox_text_secondary));
        holder.binding.text.setText(item.getTitle());
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(item.getTitle()));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchWordBinding binding;

        public ViewHolder(@NonNull AdapterSearchWordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

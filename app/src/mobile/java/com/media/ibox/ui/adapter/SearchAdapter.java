package com.media.ibox.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.media.ibox.search.SearchResultGroup;
import com.media.ibox.databinding.AdapterSearchBinding;
import com.media.ibox.utils.ImgUtil;

public class SearchAdapter extends BaseDiffAdapter<SearchResultGroup, SearchAdapter.ViewHolder> {

    private final OnClickListener listener;

    public SearchAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(SearchResultGroup item);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchResultGroup item = getItem(position);
        holder.binding.name.setText(item.name());
        holder.binding.year.setText(item.year());
        holder.binding.site.setText(item.sourceSummary());
        holder.binding.remark.setText(item.qualitySummary());
        holder.binding.year.setVisibility(item.year().isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        holder.binding.site.setVisibility(item.sourceSummary().isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        holder.binding.remark.setVisibility(item.qualitySummary().isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        ImgUtil.load(item.name(), item.pic(), holder.binding.image);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchBinding binding;

        ViewHolder(@NonNull AdapterSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

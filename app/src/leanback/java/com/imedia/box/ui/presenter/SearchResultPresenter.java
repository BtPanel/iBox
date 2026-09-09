package com.imedia.box.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.imedia.box.Product;
import com.imedia.box.bean.Style;
import com.imedia.box.databinding.AdapterVodRectBinding;
import com.imedia.box.search.SearchResultGroup;
import com.imedia.box.utils.ImgUtil;

public class SearchResultPresenter extends Presenter {

    private final OnClickListener listener;
    private final int[] size = Product.getSpec(Style.rect());

    public SearchResultPresenter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onItemClick(SearchResultGroup item);

        boolean onLongClick(SearchResultGroup item);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        AdapterVodRectBinding binding = AdapterVodRectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.image.getLayoutParams().height = size[1];
        binding.getRoot().getLayoutParams().width = size[0];
        return new ResultViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object object) {
        ResultViewHolder holder = (ResultViewHolder) viewHolder;
        SearchResultGroup item = (SearchResultGroup) object;
        holder.binding.name.setText(item.name());
        holder.binding.year.setText(item.year());
        holder.binding.site.setText(item.sourceSummary());
        holder.binding.remark.setText(item.qualitySummary());
        holder.binding.year.setVisibility(item.year().isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.site.setVisibility(item.sourceSummary().isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.remark.setVisibility(item.qualitySummary().isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLongClick(item));
        ImgUtil.load(item.name(), item.pic(), holder.binding.image);
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
        ResultViewHolder holder = (ResultViewHolder) viewHolder;
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    private static final class ResultViewHolder extends ViewHolder {
        private final AdapterVodRectBinding binding;

        private ResultViewHolder(AdapterVodRectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

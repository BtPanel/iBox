package com.media.ibox.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.media.ibox.Product;
import com.media.ibox.bean.Style;
import com.media.ibox.bean.Vod;
import com.media.ibox.databinding.AdapterHomeVodBinding;
import com.media.ibox.utils.ImgUtil;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.utils.TvFocusScale;

public class HomeVodPresenter extends Presenter {

    public static final int COLUMN_COUNT = 5;
    private final VodPresenter.OnClickListener listener;
    private final int[] size;

    public HomeVodPresenter(VodPresenter.OnClickListener listener) {
        this.listener = listener;
        int horizontalSpace = ResUtil.dp2px(96 + 20 * (COLUMN_COUNT - 1));
        size = Product.getSpec(horizontalSpace, COLUMN_COUNT, Style.rect());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        AdapterHomeVodBinding binding = AdapterHomeVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.getRoot().getLayoutParams().width = size[0];
        binding.image.getLayoutParams().height = size[1];
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Vod item = (Vod) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.name.setText(item.getName());
        holder.binding.site.setText(item.getSiteName());
        holder.binding.remark.setText(item.getRemarks());
        holder.binding.site.setVisibility(item.getSiteVisible());
        holder.binding.remark.setVisibility(item.getRemarkVisible());
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLongClick(item));
        holder.binding.getRoot().setOnFocusChangeListener((view, focused) -> {
            TvFocusScale.apply(view, focused);
            if (focused) listener.onItemFocus(item);
        });
        ImgUtil.load(item.getName(), item.getPic(), holder.binding.image);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.getRoot().setOnFocusChangeListener(null);
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    private static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterHomeVodBinding binding;

        private ViewHolder(AdapterHomeVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

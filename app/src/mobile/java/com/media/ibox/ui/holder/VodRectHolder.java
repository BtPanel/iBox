package com.media.ibox.ui.holder;

import android.view.View;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.media.ibox.bean.Vod;
import com.media.ibox.databinding.AdapterVodRectBinding;
import com.media.ibox.ui.adapter.VodAdapter;
import com.media.ibox.ui.base.BaseVodHolder;
import com.media.ibox.utils.ImgUtil;

public class VodRectHolder extends BaseVodHolder {

    private final VodAdapter.OnClickListener listener;
    private final AdapterVodRectBinding binding;

    public VodRectHolder(@NonNull AdapterVodRectBinding binding, VodAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    public VodRectHolder size(int[] size) {
        binding.image.getLayoutParams().height = size[1];
        binding.getRoot().getLayoutParams().width = size[0];
        return this;
    }

    @Override
    public void initView(Vod item) {
        String remark = getScoreText(item.getRemarks());
        binding.name.setText(item.getName());
        binding.year.setText(item.getYear());
        binding.site.setText(item.getSiteName());
        binding.remark.setText(remark);
        binding.site.setVisibility(item.getSiteVisible());
        binding.name.setVisibility(item.getNameVisible());
        binding.year.setVisibility(item.getYearVisible());
        binding.remark.setVisibility(remark.isEmpty() ? View.GONE : View.VISIBLE);
        binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
        ImgUtil.load(item.getName(), item.getPic(), binding.image);
    }

    @Override
    public void unbind() {
        Glide.with(binding.image).clear(binding.image);
    }

    private String getScoreText(String remark) {
        String cleaned = remark.replaceFirst("^\\s*\\d{4}\\s*[/／|｜·,， -]+\\s*", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("0") || cleaned.equals("0.0")) return "";
        return cleaned;
    }
}

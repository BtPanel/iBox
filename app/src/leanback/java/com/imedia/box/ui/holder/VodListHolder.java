package com.imedia.box.ui.holder;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.imedia.box.bean.Vod;
import com.imedia.box.databinding.AdapterVodListBinding;
import com.imedia.box.ui.base.BaseVodHolder;
import com.imedia.box.ui.presenter.VodPresenter;
import com.imedia.box.utils.ImgUtil;

public class VodListHolder extends BaseVodHolder {

    private final VodPresenter.OnClickListener listener;
    private final AdapterVodListBinding binding;

    public VodListHolder(@NonNull AdapterVodListBinding binding, VodPresenter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    @Override
    public void initView(Vod item) {
        binding.name.setText(item.getName());
        binding.remark.setText(item.getRemarks());
        binding.name.setVisibility(item.getNameVisible());
        binding.remark.setVisibility(item.getRemarkVisible());
        binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
        ImgUtil.load(item.getName(), item.getPic(), binding.image, true);
    }

    @Override
    public void unbind() {
        Glide.with(binding.image).clear(binding.image);
    }
}

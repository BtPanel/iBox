package com.media.ibox.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.media.ibox.bean.Vod;
import com.media.ibox.databinding.AdapterHeroBinding;
import com.media.ibox.utils.ImgUtil;

public class HeroPresenter extends Presenter {

    private final VodPresenter.OnClickListener listener;

    public HeroPresenter(VodPresenter.OnClickListener listener) {
        this.listener = listener;
    }

    public record Item(Vod vod) {
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterHeroBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false).getRoot());
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object object) {
        Item item = (Item) object;
        Vod vod = item.vod();
        AdapterHeroBinding binding = AdapterHeroBinding.bind(viewHolder.view);
        binding.name.setText(vod.getName());
        binding.remark.setText(vod.getRemarks());
        binding.getRoot().setOnClickListener(view -> listener.onItemClick(vod));
        binding.getRoot().setOnLongClickListener(view -> listener.onLongClick(vod));
        ImgUtil.load(vod.getName(), vod.getPic(), binding.image, true);
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
        Glide.with(viewHolder.view).clear(AdapterHeroBinding.bind(viewHolder.view).image);
    }
}

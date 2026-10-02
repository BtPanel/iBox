package com.media.ibox.ui.holder;

import androidx.annotation.NonNull;

import com.media.ibox.bean.Episode;
import com.media.ibox.databinding.AdapterEpisodeGridBinding;
import com.media.ibox.ui.adapter.EpisodeAdapter;
import com.media.ibox.ui.base.BaseEpisodeHolder;

public class EpisodeGridHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeGridBinding binding;
    private final boolean strip;

    public EpisodeGridHolder(@NonNull AdapterEpisodeGridBinding binding, EpisodeAdapter.OnClickListener listener, boolean strip) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        this.strip = strip;
    }

    @Override
    public void initView(Episode item) {
        if (strip) binding.text.getLayoutParams().width = com.media.ibox.utils.ResUtil.dp2px(72);
        binding.text.setSelected(item.isSelected());
        binding.text.setText(item.getDisplay());
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }
}

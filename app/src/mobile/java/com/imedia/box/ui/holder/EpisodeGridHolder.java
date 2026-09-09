package com.imedia.box.ui.holder;

import androidx.annotation.NonNull;

import com.imedia.box.bean.Episode;
import com.imedia.box.databinding.AdapterEpisodeGridBinding;
import com.imedia.box.ui.adapter.EpisodeAdapter;
import com.imedia.box.ui.base.BaseEpisodeHolder;

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
        if (strip) binding.text.getLayoutParams().width = com.imedia.box.utils.ResUtil.dp2px(72);
        binding.text.setSelected(item.isSelected());
        binding.text.setText(item.getDisplay());
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }
}

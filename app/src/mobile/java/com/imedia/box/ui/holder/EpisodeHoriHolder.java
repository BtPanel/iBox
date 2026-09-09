package com.imedia.box.ui.holder;

import androidx.annotation.NonNull;

import com.imedia.box.bean.Episode;
import com.imedia.box.databinding.AdapterEpisodeHoriBinding;
import com.imedia.box.ui.adapter.EpisodeAdapter;
import com.imedia.box.ui.base.BaseEpisodeHolder;
import com.imedia.box.utils.ResUtil;

public class EpisodeHoriHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeHoriBinding binding;
    private final int maxWidth;

    public EpisodeHoriHolder(@NonNull AdapterEpisodeHoriBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        this.maxWidth = ResUtil.getScreenWidth() - ResUtil.dp2px(32);
    }

    @Override
    public void initView(Episode item) {
        binding.text.setMaxWidth(maxWidth);
        binding.text.setSelected(item.isSelected());
        binding.text.setText(item.getDisplay());
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }
}

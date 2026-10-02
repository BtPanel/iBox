package com.media.ibox.ui.holder;

import androidx.annotation.NonNull;

import com.media.ibox.bean.Episode;
import com.media.ibox.databinding.AdapterEpisodeHoriBinding;
import com.media.ibox.ui.adapter.EpisodeAdapter;
import com.media.ibox.ui.base.BaseEpisodeHolder;
import com.media.ibox.utils.ResUtil;

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

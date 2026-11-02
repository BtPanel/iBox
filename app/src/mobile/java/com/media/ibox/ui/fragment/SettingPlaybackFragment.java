package com.media.ibox.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.FragmentSettingPlaybackBinding;
import com.media.ibox.databinding.ViewSettingRowBinding;
import com.media.ibox.setting.DanmakuSetting;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.activity.HomeActivity;
import com.media.ibox.ui.base.BaseFragment;

/**
 * 二级页：播放与弹幕。播放器与弹幕各自还有下一层页面，
 * 自动切线路 / 自动切源这两个开关直接留在这里。
 */
public class SettingPlaybackFragment extends BaseFragment {

    private FragmentSettingPlaybackBinding mBinding;

    public static SettingPlaybackFragment newInstance() {
        return new SettingPlaybackFragment();
    }

    private HomeActivity getRoot() {
        return (HomeActivity) requireActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPlaybackBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        bindBackNavigation(mBinding.toolbar);
        initRow(mBinding.rowPlayer, R.drawable.ic_msr_play_arrow, R.string.setting_player);
        mBinding.rowPlayer.summary.setText(R.string.settings_player_summary);
        mBinding.rowPlayer.summary.setVisibility(View.VISIBLE);
        initRow(mBinding.rowDanmaku, R.drawable.ic_msr_subtitles, R.string.setting_danmaku);
        mBinding.rowDanmaku.summary.setText(R.string.settings_danmaku_summary);
        mBinding.rowDanmaku.summary.setVisibility(View.VISIBLE);
        mBinding.rowAutoFallbackLine.icon.setImageResource(R.drawable.ic_msr_refresh);
        mBinding.rowAutoFallbackLine.title.setText(R.string.setting_auto_fallback_line);
        mBinding.rowAutoFallbackSource.icon.setImageResource(R.drawable.ic_msr_swap_horiz);
        mBinding.rowAutoFallbackSource.title.setText(R.string.setting_auto_fallback_source);
        refresh();
    }

    private void initRow(ViewSettingRowBinding row, int icon, int title) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
        row.value.setVisibility(View.VISIBLE);
    }

    private void refresh() {
        if (mBinding == null) return;
        mBinding.rowDanmaku.value.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        mBinding.rowAutoFallbackLine.toggle.setChecked(Setting.isAutoFallbackLine());
        mBinding.rowAutoFallbackSource.toggle.setChecked(Setting.isAutoFallbackSource());
    }

    @Override
    protected void initEvent() {
        mBinding.rowPlayer.getRoot().setOnClickListener(view -> getRoot().change(HomeActivity.PAGE_PLAYER));
        mBinding.rowDanmaku.getRoot().setOnClickListener(view -> getRoot().change(HomeActivity.PAGE_DANMAKU));
        mBinding.rowAutoFallbackLine.getRoot().setOnClickListener(this::setAutoFallbackLine);
        mBinding.rowAutoFallbackSource.getRoot().setOnClickListener(this::setAutoFallbackSource);
    }

    private void setAutoFallbackLine(View view) {
        Setting.putAutoFallbackLine(!Setting.isAutoFallbackLine());
        mBinding.rowAutoFallbackLine.toggle.setChecked(Setting.isAutoFallbackLine());
    }

    private void setAutoFallbackSource(View view) {
        Setting.putAutoFallbackSource(!Setting.isAutoFallbackSource());
        mBinding.rowAutoFallbackSource.toggle.setChecked(Setting.isAutoFallbackSource());
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) refresh();
    }
}

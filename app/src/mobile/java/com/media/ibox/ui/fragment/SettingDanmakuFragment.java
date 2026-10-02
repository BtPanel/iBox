package com.media.ibox.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.FragmentSettingDanmakuBinding;
import com.media.ibox.setting.DanmakuSetting;
import com.media.ibox.ui.base.BaseFragment;

public class SettingDanmakuFragment extends BaseFragment {

    private FragmentSettingDanmakuBinding mBinding;

    public static SettingDanmakuFragment newInstance() {
        return new SettingDanmakuFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingDanmakuBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mBinding.danmakuAuto.setChecked(DanmakuSetting.isAuto());
        mBinding.danmakuLoad.setChecked(DanmakuSetting.isLoad());
        mBinding.danmakuSpider.setChecked(DanmakuSetting.isSpiderFirst());
        String apiUrl = DanmakuSetting.getSourceApiUrl();
        if (TextUtils.isEmpty(apiUrl)) mBinding.danmakuApiUrl.setText(R.string.danmaku_api_none);
        else mBinding.danmakuApiUrl.setText(apiUrl);
        updateApiVisibility();
    }

    @Override
    protected void initEvent() {
        mBinding.danmakuAuto.setOnCheckedChangeListener((button, checked) -> setDanmakuAuto(checked));
        mBinding.danmakuLoad.setOnCheckedChangeListener((button, checked) -> setDanmakuLoad(checked));
        mBinding.danmakuSpider.setOnCheckedChangeListener((button, checked) -> DanmakuSetting.putSpiderFirst(checked));
    }

    private void setDanmakuLoad(boolean checked) {
        DanmakuSetting.putLoad(checked);
        updateApiVisibility();
    }

    private void updateApiVisibility() {
        updateAutoVisibility();
    }

    private void updateAutoVisibility() {
        boolean load = DanmakuSetting.isLoad();
        mBinding.danmakuMatchingTitle.setVisibility(load ? View.VISIBLE : View.GONE);
        mBinding.danmakuMatchingGroup.setVisibility(load ? View.VISIBLE : View.GONE);
        mBinding.danmakuAuto.setVisibility(load ? View.VISIBLE : View.GONE);
        updateSpiderVisibility();
    }

    private void updateSpiderVisibility() {
        boolean show = DanmakuSetting.isLoad() && DanmakuSetting.isAuto();
        mBinding.danmakuSpider.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setDanmakuAuto(boolean checked) {
        DanmakuSetting.putAuto(checked);
        updateSpiderVisibility();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) initView();
    }
}

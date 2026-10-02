package com.media.ibox.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.ActivitySettingDanmakuBinding;
import com.media.ibox.setting.DanmakuSetting;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.base.BaseActivity;

public class SettingDanmakuActivity extends BaseActivity {

    private ActivitySettingDanmakuBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingDanmakuActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingDanmakuBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.danmakuLoad.requestFocus();
        mBinding.danmakuAutoText.setText(Setting.getSwitch(DanmakuSetting.isAuto()));
        mBinding.danmakuLoadText.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        mBinding.danmakuSpiderText.setText(Setting.getSwitch(DanmakuSetting.isSpiderFirst()));
        updateApiVisibility();
    }

    @Override
    protected void initEvent() {
        mBinding.danmakuAuto.setOnClickListener(this::setDanmakuAuto);
        mBinding.danmakuLoad.setOnClickListener(this::setDanmakuLoad);
        mBinding.danmakuSpider.setOnClickListener(this::setDanmakuSpider);
    }

    private void setDanmakuLoad(View view) {
        DanmakuSetting.putLoad(!DanmakuSetting.isLoad());
        mBinding.danmakuLoadText.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        updateApiVisibility();
    }

    private void updateApiVisibility() {
        updateAutoVisibility();
    }

    private void updateAutoVisibility() {
        boolean show = DanmakuSetting.isLoad();
        mBinding.danmakuAuto.setVisibility(show ? View.VISIBLE : View.GONE);
        updateSpiderVisibility();
    }

    private void updateSpiderVisibility() {
        boolean show = DanmakuSetting.isLoad() && DanmakuSetting.isAuto();
        mBinding.danmakuSpider.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setDanmakuAuto(View view) {
        DanmakuSetting.putAuto(!DanmakuSetting.isAuto());
        mBinding.danmakuAutoText.setText(Setting.getSwitch(DanmakuSetting.isAuto()));
        updateSpiderVisibility();
    }

    private void setDanmakuSpider(View view) {
        DanmakuSetting.putSpiderFirst(!DanmakuSetting.isSpiderFirst());
        mBinding.danmakuSpiderText.setText(Setting.getSwitch(DanmakuSetting.isSpiderFirst()));
    }
}

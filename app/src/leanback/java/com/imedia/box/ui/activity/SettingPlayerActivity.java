package com.imedia.box.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.databinding.ActivitySettingPlayerBinding;
import com.imedia.box.impl.BufferListener;
import com.imedia.box.impl.UaListener;
import com.imedia.box.player.track.PlaybackLanguage;
import com.imedia.box.setting.PlayerSetting;
import com.imedia.box.setting.Setting;
import com.imedia.box.ui.base.BaseActivity;
import com.imedia.box.ui.dialog.BufferDialog;
import com.imedia.box.ui.dialog.MpvConfDialog;
import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;
import com.imedia.box.ui.dialog.UaDialog;
import com.imedia.box.utils.ResUtil;

public class SettingPlayerActivity extends BaseActivity implements UaListener, BufferListener {

    private ActivitySettingPlayerBinding mBinding;
    private String[] render;
    private String[] scale;
    private String[] engine;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPlayerActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPlayerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setVisible();
        setPlaybackModeText();
        mBinding.engine.requestFocus();
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
        mBinding.bufferText.setText(String.valueOf(PlayerSetting.getBuffer()));
        mBinding.mpvVulkanText.setText(Setting.getSwitch(PlayerSetting.isMpvVulkan()));
        mBinding.mpvGpuNextText.setText(Setting.getSwitch(PlayerSetting.isMpvGpuNext()));
        mBinding.backgroundText.setText(Setting.getSwitch(PlayerSetting.isBackgroundOn()));
        mBinding.autoNextText.setText(Setting.getSwitch(PlayerSetting.isAutoNextEnabled()));
        String[] languages = PlaybackLanguage.labels(this);
        mBinding.audioLanguageText.setText(languages[PlayerSetting.getAudioLanguageIndex()]);
        mBinding.textLanguageText.setText(languages[PlayerSetting.getTextLanguageIndex()]);
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[PlayerSetting.getScale()]);
    }

    @Override
    protected void initEvent() {
        mBinding.engine.setOnClickListener(this::setEngine);
        mBinding.decode.setOnClickListener(this::onDecodeSetting);
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.mpvConf.setOnClickListener(this::onMpvConf);
        mBinding.mpvGpuNext.setOnClickListener(this::setMpvGpuNext);
        mBinding.mpvVulkan.setOnClickListener(this::setMpvVulkan);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.background.setOnClickListener(this::onBackground);
        mBinding.autoNext.setOnClickListener(this::onAutoNext);
        mBinding.audioLanguage.setOnClickListener(view -> chooseLanguage(true));
        mBinding.textLanguage.setOnClickListener(view -> chooseLanguage(false));
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.preload.setOnClickListener(this::onPreloadSetting);
        mBinding.ua.setOnClickListener(this::onUa);
    }

    private void setVisible() {
        boolean exo = PlayerSetting.isExo();
        if (PlayerSetting.isBackgroundPiP()) PlayerSetting.putBackground(1);
        mBinding.mpvConf.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.mpvVulkan.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.mpvGpuNext.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.adblock.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.buffer.setVisibility(exo ? View.VISIBLE : View.GONE);
    }

    private void setEngine(View view) {
        int index = (PlayerSetting.getEngine() + 1) % engine.length;
        PlayerSetting.putEngine(index);
        setPlaybackModeText();
        setVisible();
    }

    private void onMpvConf(View view) {
        MpvConfDialog.show(this);
    }

    private void setMpvGpuNext(View view) {
        PlayerSetting.putMpvGpuNext(!PlayerSetting.isMpvGpuNext());
        mBinding.mpvGpuNextText.setText(Setting.getSwitch(PlayerSetting.isMpvGpuNext()));
    }

    private void setMpvVulkan(View view) {
        PlayerSetting.putMpvVulkan(!PlayerSetting.isMpvVulkan());
        mBinding.mpvVulkanText.setText(Setting.getSwitch(PlayerSetting.isMpvVulkan()));
    }

    private void setRender(View view) {
        int index = (PlayerSetting.getRender() + 1) % render.length;
        PlayerSetting.putRender(index);
        setPlaybackModeText();
    }

    private void setPlaybackModeText() {
        engine = ResUtil.getStringArray(R.array.select_engine);
        render = ResUtil.getStringArray(R.array.select_render);
        mBinding.engineText.setText(engine[PlayerSetting.getEngine()]);
        mBinding.renderText.setText(render[PlayerSetting.getRender()]);
    }

    private void setScale(View view) {
        int index = (PlayerSetting.getScale() + 1) % scale.length;
        mBinding.scaleText.setText(scale[index]);
        PlayerSetting.putScale(index);
    }

    private void onBuffer(View view) {
        BufferDialog.show(this);
    }

    @Override
    public void setBuffer(int times) {
        mBinding.bufferText.setText(String.valueOf(times));
        PlayerSetting.putBuffer(times);
    }

    private void onBackground(View view) {
        PlayerSetting.putBackground(PlayerSetting.isBackgroundOn() ? 0 : 1);
        mBinding.backgroundText.setText(Setting.getSwitch(PlayerSetting.isBackgroundOn()));
    }

    private void onAutoNext(View view) {
        PlayerSetting.putAutoNextEnabled(!PlayerSetting.isAutoNextEnabled());
        mBinding.autoNextText.setText(Setting.getSwitch(PlayerSetting.isAutoNextEnabled()));
    }

    private void chooseLanguage(boolean audio) {
        String[] choices = PlaybackLanguage.labels(this);
        int current = audio ? PlayerSetting.getAudioLanguageIndex() : PlayerSetting.getTextLanguageIndex();
        new IBoxAlertDialogBuilder(this)
                .setTitle(audio ? R.string.player_audio_language : R.string.player_text_language)
                .setSingleChoiceItems(choices, current, (dialog, which) -> {
                    if (audio) {
                        PlayerSetting.putAudioLanguageIndex(which);
                        mBinding.audioLanguageText.setText(choices[which]);
                    } else {
                        PlayerSetting.putTextLanguageIndex(which);
                        mBinding.textLanguageText.setText(choices[which]);
                    }
                    dialog.dismiss();
                }).setNegativeButton(R.string.dialog_negative, null).show();
    }

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
    }

    private void onPreloadSetting(View view) {
        SettingPreloadActivity.start(this);
    }

    private void onDecodeSetting(View view) {
        SettingDecodeActivity.start(this);
    }

    private void onUa(View view) {
        UaDialog.show(this);
    }

    @Override
    public void setUa(String ua) {
        Setting.putUa(ua);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBinding != null) setPlaybackModeText();
    }
}

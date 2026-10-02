package com.media.ibox.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.FragmentSettingPlayerBinding;
import com.media.ibox.impl.BufferListener;
import com.media.ibox.impl.UaListener;
import com.media.ibox.player.track.PlaybackLanguage;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.activity.HomeActivity;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.ui.dialog.BufferDialog;
import com.media.ibox.ui.dialog.MpvConfDialog;
import com.media.ibox.ui.dialog.UaDialog;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

public class SettingPlayerFragment extends BaseFragment implements UaListener, BufferListener {

    private FragmentSettingPlayerBinding mBinding;
    private String[] background;
    private String[] render;
    private String[] scale;
    private String[] engine;

    public static SettingPlayerFragment newInstance() {
        return new SettingPlayerFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPlayerBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setVisible();
        setPlaybackModeText();
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
        mBinding.bufferText.setText(String.valueOf(PlayerSetting.getBuffer()));
        mBinding.mpvVulkanText.setText(Setting.getSwitch(PlayerSetting.isMpvVulkan()));
        mBinding.mpvGpuNextText.setText(Setting.getSwitch(PlayerSetting.isMpvGpuNext()));
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[PlayerSetting.getScale()]);
        mBinding.backgroundText.setText((background = ResUtil.getStringArray(R.array.select_background))[PlayerSetting.getBackground()]);
        mBinding.autoNextText.setText(Setting.getSwitch(PlayerSetting.isAutoNextEnabled()));
        String[] languages = PlaybackLanguage.labels(requireContext());
        mBinding.audioLanguageText.setText(languages[PlayerSetting.getAudioLanguageIndex()]);
        mBinding.textLanguageText.setText(languages[PlayerSetting.getTextLanguageIndex()]);
    }

    @Override
    protected void initEvent() {
        mBinding.engine.setOnClickListener(this::setEngine);
        mBinding.decode.setOnClickListener(this::onDecode);
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.mpvConf.setOnClickListener(this::onMpvConf);
        mBinding.mpvGpuNext.setOnClickListener(this::setMpvGpuNext);
        mBinding.mpvVulkan.setOnClickListener(this::setMpvVulkan);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.scale.setOnClickListener(this::onScale);
        mBinding.background.setOnClickListener(this::onBackground);
        mBinding.autoNext.setOnClickListener(this::onAutoNext);
        mBinding.audioLanguage.setOnClickListener(view -> chooseLanguage(true));
        mBinding.textLanguage.setOnClickListener(view -> chooseLanguage(false));
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.preload.setOnClickListener(this::onPreload);
        mBinding.ua.setOnClickListener(this::onUa);
    }

    private void setVisible() {
        boolean exo = PlayerSetting.isExo();
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

    private void onScale(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.player_scale).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(scale, PlayerSetting.getScale(), (dialog, which) -> {
            mBinding.scaleText.setText(scale[which]);
            PlayerSetting.putScale(which);
            dialog.dismiss();
        }).show();
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
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.player_background).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(background, PlayerSetting.getBackground(), (dialog, which) -> {
            mBinding.backgroundText.setText(background[which]);
            PlayerSetting.putBackground(which);
            dialog.dismiss();
        }).show();
    }

    private void onAutoNext(View view) {
        PlayerSetting.putAutoNextEnabled(!PlayerSetting.isAutoNextEnabled());
        mBinding.autoNextText.setText(Setting.getSwitch(PlayerSetting.isAutoNextEnabled()));
    }

    private void chooseLanguage(boolean audio) {
        String[] choices = PlaybackLanguage.labels(requireContext());
        int current = audio ? PlayerSetting.getAudioLanguageIndex() : PlayerSetting.getTextLanguageIndex();
        new IBoxAlertDialogBuilder(requireActivity())
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

    private void onPreload(View view) {
        ((HomeActivity) requireActivity()).change(4);
    }

    private void onDecode(View view) {
        ((HomeActivity) requireActivity()).change(5);
    }

    private void onUa(View view) {
        UaDialog.show(this);
    }

    @Override
    public void setUa(String ua) {
        Setting.putUa(ua);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) initView();
    }
}

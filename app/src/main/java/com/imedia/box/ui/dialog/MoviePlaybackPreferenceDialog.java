package com.imedia.box.ui.dialog;

import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.imedia.box.R;
import com.imedia.box.databinding.DialogMoviePlaybackPreferenceBinding;
import com.imedia.box.playback.preference.MoviePlaybackPreference;
import com.imedia.box.playback.preference.MoviePlaybackPreferenceStore;
import com.imedia.box.player.PlayerManager;
import com.imedia.box.player.engine.PlayerEngine;
import com.imedia.box.setting.PlayerSetting;
import com.imedia.box.setting.SpeedSetting;
import com.imedia.box.setting.SubtitleSetting;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Util;

import java.util.Locale;

public final class MoviePlaybackPreferenceDialog extends BaseAlertDialog {

    private static final String TAG = "movie-playback-preference";

    private DialogMoviePlaybackPreferenceBinding binding;
    private PlayerManager player;
    private String key;
    private Runnable listener;
    private boolean bindingValues;

    public static void show(FragmentActivity activity, String key, PlayerManager player, Runnable listener) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        MoviePlaybackPreferenceDialog dialog = new MoviePlaybackPreferenceDialog();
        dialog.key = key;
        dialog.player = player;
        dialog.listener = listener;
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogMoviePlaybackPreferenceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        int padding = ResUtil.dp2px(Util.isLeanback() ? 32 : 24);
        View content = binding.getRoot().getChildAt(0);
        content.setPadding(padding, padding, padding, padding);
        binding.speed.setLabelFormatter(SpeedSetting::format);
        binding.subtitleScale.setLabelFormatter(value -> formatPercent(value * 100));
        binding.subtitlePosition.setLabelFormatter(this::formatPosition);
        MoviePlaybackPreference saved = MoviePlaybackPreferenceStore.get(key);
        bind(saved == null ? MoviePlaybackPreference.global(player.getDecode()) : saved, saved != null);
    }

    @Override
    protected void initEvent() {
        binding.enabled.setOnCheckedChangeListener((button, checked) -> {
            if (bindingValues) return;
            setControlsEnabled(checked);
            if (checked) saveAndApply();
            else restoreGlobal();
        });
        binding.engine.addOnButtonCheckedListener((group, id, checked) -> saveIfChanged(checked));
        binding.decode.addOnButtonCheckedListener((group, id, checked) -> saveIfChanged(checked));
        binding.subtitleStyle.addOnButtonCheckedListener((group, id, checked) -> saveIfChanged(checked));
        binding.speed.addOnChangeListener((slider, value, fromUser) -> saveIfChanged(fromUser));
        binding.subtitleScale.addOnChangeListener((slider, value, fromUser) -> {
            updateLabels();
            saveIfChanged(fromUser);
        });
        binding.subtitlePosition.addOnChangeListener((slider, value, fromUser) -> {
            updateLabels();
            saveIfChanged(fromUser);
        });
        binding.restore.setOnClickListener(view -> restoreGlobal());
    }

    private void bind(MoviePlaybackPreference preference, boolean enabled) {
        bindingValues = true;
        binding.enabled.setChecked(enabled);
        binding.engine.check(preference.engine() == PlayerSetting.ENGINE_MPV ? R.id.engineMpv : R.id.engineExo);
        binding.decode.check(preference.decode() == PlayerEngine.SOFT ? R.id.decodeSoft : R.id.decodeHard);
        binding.speed.setValue(Math.clamp(preference.speed(), 0.5f, 3.0f));
        binding.subtitleStyle.check(switch (preference.subtitleSource()) {
            case SubtitleSetting.STYLE_SOURCE_SYSTEM -> R.id.subtitleSystem;
            case SubtitleSetting.STYLE_SOURCE_CUSTOM -> R.id.subtitleCustom;
            default -> R.id.subtitleOriginal;
        });
        binding.subtitleScale.setValue(preference.subtitleScale());
        binding.subtitlePosition.setValue(preference.subtitlePosition());
        bindingValues = false;
        setControlsEnabled(enabled);
        updateLabels();
    }

    private void saveIfChanged(boolean changed) {
        if (!bindingValues && changed && binding.enabled.isChecked()) saveAndApply();
    }

    private void saveAndApply() {
        MoviePlaybackPreference preference = current();
        MoviePlaybackPreferenceStore.put(key, preference);
        apply(preference);
    }

    private void restoreGlobal() {
        MoviePlaybackPreferenceStore.remove(key);
        MoviePlaybackPreference global = MoviePlaybackPreference.global(PlayerEngine.HARD);
        bind(global, false);
        player.setEngine(global.engine(), false);
        player.setDecodePreference(global.decode());
        player.setSpeed(global.speed());
        player.setSubtitleStyleOverride(null);
        notifyChanged();
    }

    private void apply(MoviePlaybackPreference preference) {
        player.setEngine(preference.engine(), false);
        player.setDecodePreference(preference.decode());
        player.setSpeed(preference.speed());
        player.setSubtitleStyleOverride(preference.subtitleStyle());
        notifyChanged();
    }

    private MoviePlaybackPreference current() {
        int engine = binding.engine.getCheckedButtonId() == R.id.engineMpv ? PlayerSetting.ENGINE_MPV : PlayerSetting.ENGINE_EXO;
        int decode = binding.decode.getCheckedButtonId() == R.id.decodeSoft ? PlayerEngine.SOFT : PlayerEngine.HARD;
        int subtitle = SubtitleSetting.STYLE_SOURCE_ORIGINAL;
        int subtitleButton = binding.subtitleStyle.getCheckedButtonId();
        if (subtitleButton == R.id.subtitleSystem) subtitle = SubtitleSetting.STYLE_SOURCE_SYSTEM;
        else if (subtitleButton == R.id.subtitleCustom) subtitle = SubtitleSetting.STYLE_SOURCE_CUSTOM;
        return new MoviePlaybackPreference(engine, decode, binding.speed.getValue(), subtitle,
                binding.subtitleScale.getValue(), binding.subtitlePosition.getValue(), System.currentTimeMillis());
    }

    private void setControlsEnabled(boolean enabled) {
        setEnabled(binding.engine, enabled);
        setEnabled(binding.decode, enabled);
        setEnabled(binding.speed, enabled);
        setEnabled(binding.subtitleStyle, enabled);
        setEnabled(binding.subtitleScale, enabled);
        setEnabled(binding.subtitlePosition, enabled);
        binding.restore.setEnabled(enabled);
    }

    private void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1.0f : 0.38f);
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).setEnabled(enabled);
    }

    private void updateLabels() {
        binding.subtitleScaleLabel.setText(getString(R.string.movie_preference_subtitle_size, formatPercent(binding.subtitleScale.getValue() * 100)));
        binding.subtitlePositionLabel.setText(getString(R.string.movie_preference_subtitle_position, formatPosition(binding.subtitlePosition.getValue())));
    }

    private String formatPercent(float value) {
        return String.format(Locale.getDefault(), "%.0f%%", value);
    }

    private String formatPosition(float value) {
        return String.format(Locale.getDefault(), "%+.0f%%", value);
    }

    private void notifyChanged() {
        if (listener != null) listener.run();
    }
}

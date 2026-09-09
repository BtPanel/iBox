package com.imedia.box.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.bean.CastVideo;
import com.imedia.box.bean.Device;
import com.imedia.box.databinding.ActivityCastControlBinding;
import com.imedia.box.dlna.DLNACastSession;
import com.imedia.box.ui.base.BaseActivity;
import com.imedia.box.utils.ImgUtil;
import com.google.android.material.slider.Slider;

import org.jupnp.support.model.TransportState;

import java.util.Locale;

public class CastControlActivity extends BaseActivity implements DLNACastSession.Listener {

    private ActivityCastControlBinding mBinding;
    private DLNACastSession session;
    private boolean seeking;
    private boolean changingVolume;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, CastControlActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCastControlBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        session = DLNACastSession.get();
        if (!session.isActive()) {
            finish();
            return;
        }
        setSupportActionBar(mBinding.toolbar);
        CastVideo video = session.getVideo();
        Device device = session.getDevice();
        mBinding.videoTitle.setText(video.name());
        mBinding.device.setText(device.getName());
        ImgUtil.load(video.name(), video.image(), mBinding.artwork);
        onStateChanged(session.getState());
    }

    @Override
    protected void initEvent() {
        mBinding.play.setOnClickListener(v -> session.togglePlayback());
        mBinding.rewind.setOnClickListener(v -> seekBy(-10000));
        mBinding.forward.setOnClickListener(v -> seekBy(10000));
        mBinding.disconnect.setOnClickListener(v -> {
            mBinding.disconnect.setEnabled(false);
            mBinding.status.setText(R.string.cast_disconnecting);
            session.disconnect();
        });
        mBinding.progress.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                session.seekTo((long) (slider.getValue() * 1000));
                seeking = false;
            }
        });
        mBinding.progress.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) mBinding.elapsed.setText(formatTime((long) value * 1000));
        });
        mBinding.volume.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                changingVolume = true;
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                session.setVolume(Math.round(slider.getValue()));
                changingVolume = false;
            }
        });
        mBinding.volume.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) mBinding.volumeValue.setText(getString(R.string.cast_volume_value, Math.round(value)));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session != null) session.setListener(this);
    }

    @Override
    protected void onPause() {
        if (session != null) session.setListener(null);
        super.onPause();
    }

    @Override
    public void onStateChanged(DLNACastSession.State state) {
        if (!state.active()) {
            finish();
            return;
        }
        mBinding.status.setText(getStatus(state.transportState()));
        mBinding.play.setIconResource(state.playing() ? R.drawable.ic_msr_pause : R.drawable.ic_msr_play_arrow);
        mBinding.play.setContentDescription(getString(state.playing() ? R.string.cast_pause : R.string.cast_play));
        updateProgress(state);
        updateVolume(state);
    }

    private void updateProgress(DLNACastSession.State state) {
        long duration = Math.max(0, state.duration());
        long position = duration > 0 ? Math.min(state.position(), duration) : Math.max(0, state.position());
        float valueTo = Math.max(1, duration / 1000f);
        float value = Math.min(valueTo, position / 1000f);
        mBinding.elapsed.setText(formatTime(position));
        mBinding.duration.setText(duration > 0 ? formatTime(duration) : getString(R.string.cast_live));
        mBinding.progress.setEnabled(duration > 0);
        mBinding.rewind.setEnabled(duration > 0);
        mBinding.forward.setEnabled(duration > 0);
        mBinding.rewind.setAlpha(duration > 0 ? 1f : 0.38f);
        mBinding.forward.setAlpha(duration > 0 ? 1f : 0.38f);
        if (!seeking) {
            if (mBinding.progress.getValue() > valueTo) mBinding.progress.setValue(valueTo);
            mBinding.progress.setValueTo(valueTo);
            mBinding.progress.setValue(value);
        }
    }

    private void updateVolume(DLNACastSession.State state) {
        mBinding.volume.setEnabled(state.volumeSupported());
        mBinding.volumeGroup.setAlpha(state.volumeSupported() ? 1f : 0.38f);
        if (!changingVolume) {
            mBinding.volume.setValue(state.volume());
            mBinding.volumeValue.setText(getString(R.string.cast_volume_value, state.volume()));
        }
    }

    private void seekBy(long deltaMs) {
        DLNACastSession.State state = session.getState();
        if (state.duration() > 0) session.seekTo(state.position() + deltaMs);
    }

    private String getStatus(TransportState state) {
        if (state == TransportState.PLAYING) return getString(R.string.cast_playing);
        if (state == TransportState.PAUSED_PLAYBACK) return getString(R.string.cast_paused);
        if (state == TransportState.STOPPED || state == TransportState.NO_MEDIA_PRESENT) return getString(R.string.cast_stopped);
        return getString(R.string.cast_connecting);
    }

    private String formatTime(long ms) {
        long seconds = Math.max(0, ms) / 1000;
        if (seconds >= 3600) return String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackInvoked();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

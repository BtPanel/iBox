package com.imedia.box.dlna;

import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.bean.CastVideo;
import com.imedia.box.bean.Device;
import com.imedia.box.utils.Notify;

import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.support.avtransport.callback.GetPositionInfo;
import org.jupnp.support.avtransport.callback.GetTransportInfo;
import org.jupnp.support.avtransport.callback.Pause;
import org.jupnp.support.avtransport.callback.Play;
import org.jupnp.support.avtransport.callback.Seek;
import org.jupnp.support.avtransport.callback.Stop;
import org.jupnp.support.model.PositionInfo;
import org.jupnp.support.model.SeekMode;
import org.jupnp.support.model.TransportInfo;
import org.jupnp.support.model.TransportState;
import org.jupnp.support.renderingcontrol.callback.GetVolume;
import org.jupnp.support.renderingcontrol.callback.SetVolume;

import java.util.Locale;

public class DLNACastSession {

    private static final long POLL_INTERVAL = 2000;

    private final Runnable pollTask = this::poll;
    private final Runnable disconnectTask = this::finish;

    private RemoteService avTransport;
    private RemoteService renderingControl;
    private ControlPoint controlPoint;
    private CastVideo video;
    private Device device;
    private Listener listener;
    private TransportState transportState = TransportState.NO_MEDIA_PRESENT;
    private long position;
    private long duration;
    private int volume = 50;
    private int pollCount;
    private boolean active;
    private boolean retained;

    public static DLNACastSession get() {
        return Loader.INSTANCE;
    }

    public void start(CastVideo video, Device device) {
        DLNACastManager manager = DLNACastManager.get();
        this.video = video;
        this.device = device;
        this.position = Math.max(0, video.position());
        this.duration = 0;
        this.pollCount = 0;
        this.controlPoint = manager.getControlPoint();
        this.avTransport = manager.findAVTransport(device);
        this.renderingControl = manager.findRenderingControl(device);
        this.transportState = TransportState.PLAYING;
        this.active = controlPoint != null && avTransport != null;
        if (!active) return;
        if (!retained) {
            manager.init(App.get());
            retained = true;
        }
        notifyChanged();
        poll();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) App.post(() -> listener.onStateChanged(getState()));
    }

    public boolean isActive() {
        return active;
    }

    public CastVideo getVideo() {
        return video;
    }

    public Device getDevice() {
        return device;
    }

    public State getState() {
        return new State(active, transportState == TransportState.PLAYING, position, duration, volume, renderingControl != null, transportState);
    }

    public void togglePlayback() {
        if (!active || controlPoint == null || avTransport == null) return;
        if (transportState == TransportState.PLAYING) pause();
        else play();
    }

    public void seekTo(long positionMs) {
        if (!active || controlPoint == null || avTransport == null) return;
        long target = Math.max(0, duration > 0 ? Math.min(positionMs, duration) : positionMs);
        position = target;
        notifyChanged();
        controlPoint.execute(new Seek(avTransport, SeekMode.REL_TIME, formatMs(target)) {
            @Override
            public void success(ActionInvocation invocation) {
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                notifyFailure(message);
            }
        });
    }

    public void setVolume(int value) {
        if (!active || controlPoint == null || renderingControl == null) return;
        int target = Math.max(0, Math.min(value, 100));
        volume = target;
        notifyChanged();
        controlPoint.execute(new SetVolume(renderingControl, target) {
            @Override
            public void success(ActionInvocation invocation) {
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                notifyFailure(message);
            }
        });
    }

    public void disconnect() {
        if (!active) {
            finish();
            return;
        }
        App.post(disconnectTask, 2000);
        if (controlPoint == null || avTransport == null) return;
        controlPoint.execute(new Stop(avTransport) {
            @Override
            public void success(ActionInvocation invocation) {
                App.post(DLNACastSession.this::finish);
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                App.post(DLNACastSession.this::finish);
            }
        });
    }

    private void play() {
        controlPoint.execute(new Play(avTransport) {
            @Override
            public void success(ActionInvocation invocation) {
                transportState = TransportState.PLAYING;
                notifyChanged();
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                notifyFailure(message);
            }
        });
    }

    private void pause() {
        controlPoint.execute(new Pause(avTransport) {
            @Override
            public void success(ActionInvocation invocation) {
                transportState = TransportState.PAUSED_PLAYBACK;
                notifyChanged();
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                notifyFailure(message);
            }
        });
    }

    private void poll() {
        if (!active || controlPoint == null || avTransport == null) return;
        controlPoint.execute(new GetPositionInfo(avTransport) {
            @Override
            public void received(ActionInvocation<?> invocation, PositionInfo info) {
                position = Math.max(0, info.getTrackElapsedSeconds() * 1000);
                duration = Math.max(0, info.getTrackDurationSeconds() * 1000);
                notifyChanged();
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
            }
        });
        controlPoint.execute(new GetTransportInfo(avTransport) {
            @Override
            public void received(ActionInvocation<?> invocation, TransportInfo info) {
                transportState = info.getCurrentTransportState();
                notifyChanged();
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
            }
        });
        if (renderingControl != null && pollCount++ % 3 == 0) pollVolume();
        App.post(pollTask, POLL_INTERVAL);
    }

    private void pollVolume() {
        controlPoint.execute(new GetVolume(renderingControl) {
            @Override
            public void received(ActionInvocation<?> invocation, int currentVolume) {
                volume = Math.max(0, Math.min(currentVolume, 100));
                notifyChanged();
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
            }
        });
    }

    private synchronized void finish() {
        if (!active && !retained) return;
        App.removeCallbacks(pollTask, disconnectTask);
        active = false;
        transportState = TransportState.STOPPED;
        notifyChanged();
        if (retained) {
            DLNACastManager.get().release(App.get());
            retained = false;
        }
        controlPoint = null;
        avTransport = null;
        renderingControl = null;
    }

    private void notifyChanged() {
        State state = getState();
        App.post(() -> {
            if (listener != null) listener.onStateChanged(state);
        });
    }

    private void notifyFailure(String message) {
        App.post(() -> {
            if (message == null || message.isEmpty()) Notify.show(R.string.device_offline);
            else Notify.show(message);
        });
    }

    private String formatMs(long ms) {
        long seconds = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    public record State(boolean active, boolean playing, long position, long duration, int volume,
                        boolean volumeSupported, TransportState transportState) {
    }

    public interface Listener {

        void onStateChanged(State state);
    }

    private static class Loader {
        static final DLNACastSession INSTANCE = new DLNACastSession();
    }
}

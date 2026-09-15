package com.imedia.box.dlna;

import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.api.SiteApi;
import com.imedia.box.bean.CastVideo;
import com.imedia.box.bean.Device;
import com.imedia.box.bean.History;
import com.imedia.box.bean.Result;
import com.imedia.box.db.AppDatabase;
import com.imedia.box.setting.PlayerSetting;
import com.imedia.box.setting.Setting;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.AsyncSnapshotSaver;
import com.imedia.box.utils.Task;

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
    private static final long POLL_TIMEOUT = 5000;
    private static final AsyncSnapshotSaver<History> HISTORY_SAVER = new AsyncSnapshotSaver<>(History::copy, History::save, AppDatabase::runAsync);

    private final Runnable pollTask = this::poll;
    private final Runnable disconnectTask = this::finish;
    private final CastConnectionHealth connection = new CastConnectionHealth();

    private RemoteService avTransport;
    private RemoteService renderingControl;
    private ControlPoint controlPoint;
    private CastVideo video;
    private Device device;
    private CastEpisodeQueue episodes;
    private History history;
    private Listener listener;
    private TransportState transportState = TransportState.NO_MEDIA_PRESENT;
    private long position;
    private long duration;
    private int volume = 50;
    private int pollCount;
    private boolean active;
    private boolean retained;
    private boolean switching;
    private boolean changingEpisode;
    private boolean nearEnd;
    private boolean autoNextAttempted;
    private int endedPolls;
    private long generation;
    private Runnable retryTask;

    public static DLNACastSession get() {
        return Loader.INSTANCE;
    }

    public void start(CastVideo video, Device device) {
        DLNACastManager manager = DLNACastManager.get();
        generation++;
        switching = false;
        changingEpisode = false;
        nearEnd = false;
        autoNextAttempted = false;
        endedPolls = 0;
        episodes = null;
        history = null;
        connection.reset();
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

    public void setEpisodes(CastEpisodeQueue episodes, History history) {
        this.episodes = episodes;
        this.history = history;
        notifyChanged();
    }

    public History prepareResumeOnPhone() {
        if (!active || history == null || Setting.isIncognito()) return null;
        saveCastProgress(true);
        return history.copy();
    }

    private void saveCastProgress(boolean force) {
        if (history == null || Setting.isIncognito() || position < 0 || (!force && duration <= 0)) return;
        if (!force && !history.canScheduleSave()) return;
        history.setPosition(duration > 0 ? Math.min(position, duration) : position);
        if (duration > 0) history.setDuration(duration);
        history.setCreateTime(System.currentTimeMillis());
        history.markSaveScheduled();
        HISTORY_SAVER.save(history);
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
        return new State(active, transportState == TransportState.PLAYING, position, duration, volume,
                renderingControl != null, transportState, connection.status(), switching,
                episodes != null && episodes.hasPrevious(), episodes != null && episodes.hasNext(), changingEpisode,
                history != null && !Setting.isIncognito());
    }

    public void togglePlayback() {
        if (!active || switching || changingEpisode || connection.status() != CastConnectionHealth.Status.CONNECTED || controlPoint == null || avTransport == null) return;
        if (transportState == TransportState.PLAYING) pause();
        else play();
    }

    public void seekTo(long positionMs) {
        if (!active || switching || changingEpisode || connection.status() != CastConnectionHealth.Status.CONNECTED || controlPoint == null || avTransport == null) return;
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
        if (!active || switching || changingEpisode || connection.status() != CastConnectionHealth.Status.CONNECTED || controlPoint == null || renderingControl == null) return;
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
        generation++;
        if (retryTask != null) App.removeCallbacks(retryTask);
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

    public void retry() {
        if (!active || switching || changingEpisode || !connection.beginRetry()) return;
        long attempt = ++generation;
        notifyChanged();
        DLNACastManager.get().search();
        retryTask = () -> {
            if (!active || attempt != generation) return;
            CastVideo resume = new CastVideo(video.name(), video.url(), position, video.headers(), video.image());
            new DLNACast(resume, () -> {
                if (!active || attempt != generation) return;
                DLNACastManager manager = DLNACastManager.get();
                controlPoint = manager.getControlPoint();
                avTransport = manager.findAVTransport(device);
                renderingControl = manager.findRenderingControl(device);
                if (controlPoint == null || avTransport == null) {
                    connection.retryFailed();
                    notifyChanged();
                    return;
                }
                connection.retrySucceeded();
                transportState = TransportState.PLAYING;
                notifyChanged();
            }, () -> {
                if (!active || attempt != generation) return;
                connection.retryFailed();
                notifyChanged();
            }).cast(device);
        };
        App.post(retryTask, 1500);
    }

    public void switchTo(Device target, Runnable success, Runnable failure) {
        if (!active || switching || changingEpisode || target == null || !target.isDLNA() || target.equals(device)) {
            failure.run();
            return;
        }
        switching = true;
        long attempt = ++generation;
        RemoteService oldTransport = avTransport;
        ControlPoint oldControl = controlPoint;
        CastVideo resume = new CastVideo(video.name(), video.url(), position, video.headers(), video.image());
        notifyChanged();
        new DLNACast(resume, () -> {
            if (!active || attempt != generation) return;
            DLNACastManager manager = DLNACastManager.get();
            ControlPoint nextControl = manager.getControlPoint();
            RemoteService nextTransport = manager.findAVTransport(target);
            if (nextControl == null || nextTransport == null) {
                switching = false;
                notifyChanged();
                failure.run();
                return;
            }
            device = target;
            controlPoint = nextControl;
            avTransport = nextTransport;
            renderingControl = manager.findRenderingControl(target);
            transportState = TransportState.PLAYING;
            duration = 0;
            nearEnd = false;
            connection.reset();
            switching = false;
            notifyChanged();
            if (oldControl != null && oldTransport != null) {
                oldControl.execute(new Stop(oldTransport) {
                    @Override
                    public void success(ActionInvocation invocation) {
                    }

                    @Override
                    public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                    }
                });
            }
            success.run();
        }, () -> {
            if (!active || attempt != generation) return;
            switching = false;
            notifyChanged();
            failure.run();
        }).cast(target);
    }

    public void nextEpisode() {
        changeEpisode(episodes == null ? null : episodes.next());
    }

    public void previousEpisode() {
        changeEpisode(episodes == null ? null : episodes.previous());
    }

    private void changeEpisode(CastEpisodeQueue.Candidate candidate) {
        if (!active || switching || changingEpisode || connection.status() != CastConnectionHealth.Status.CONNECTED || candidate == null) return;
        changingEpisode = true;
        endedPolls = 0;
        long attempt = ++generation;
        notifyChanged();
        Task.execute(() -> {
            Result result;
            try {
                result = SiteApi.playerContent(episodes.siteKey(), episodes.flag(), candidate.item().url());
            } catch (Exception e) {
                App.post(() -> episodeFailed(attempt));
                return;
            }
            App.post(() -> {
                if (!active || attempt != generation) return;
                if (result.hasMsg() || result.needParse() || result.isUseParse() || result.getDrm() != null
                        || !isCastableUrl(result.getRealUrl())) {
                    episodeFailed(attempt);
                    return;
                }
                CastVideo next = new CastVideo(candidate.item().name(), result.getRealUrl(), 0,
                        result.getHeader(), video.image());
                CastVideo previous = video;
                new DLNACast(next, () -> {
                    if (!active || attempt != generation) return;
                    episodes.select(candidate);
                    video = next;
                    position = 0;
                    duration = 0;
                    nearEnd = false;
                    autoNextAttempted = false;
                    transportState = TransportState.PLAYING;
                    connection.reset();
                    changingEpisode = false;
                    if (history != null && !Setting.isIncognito()) {
                        history.setVodRemarks(candidate.item().name());
                        history.setEpisodeUrl(candidate.item().url());
                        history.setPosition(0);
                        history.setDuration(0);
                        history.setCreateTime(System.currentTimeMillis());
                        HISTORY_SAVER.save(history);
                    }
                    notifyChanged();
                }, () -> {
                    if (!active || attempt != generation) return;
                    new DLNACast(new CastVideo(previous.name(), previous.url(), position,
                            previous.headers(), previous.image()), () -> episodeFailed(attempt),
                            () -> episodeFailed(attempt)).cast(device);
                }).cast(device);
            });
        });
    }

    private boolean isCastableUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://");
    }

    private void episodeFailed(long attempt) {
        if (!active || attempt != generation) return;
        changingEpisode = false;
        notifyChanged();
        Notify.show(R.string.cast_episode_unavailable);
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
        if (!switching && !changingEpisode && connection.status() != CastConnectionHealth.Status.RETRYING) {
            long currentGeneration = generation;
            CastPollResult result = new CastPollResult();
            controlPoint.execute(new GetPositionInfo(avTransport) {
                @Override
                public void received(ActionInvocation<?> invocation, PositionInfo info) {
                    App.post(() -> {
                        if (!active || currentGeneration != generation) return;
                        position = Math.max(0, info.getTrackElapsedSeconds() * 1000);
                        duration = Math.max(0, info.getTrackDurationSeconds() * 1000);
                        if (duration > 0 && position > 0) nearEnd = position >= Math.max(duration * 9 / 10, duration - 30000);
                        saveCastProgress(false);
                        onPollResult(result.position(true));
                        notifyChanged();
                    });
                }

                @Override
                public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                    App.post(() -> {
                        if (active && currentGeneration == generation) onPollResult(result.position(false));
                    });
                }
            });
            controlPoint.execute(new GetTransportInfo(avTransport) {
                @Override
                public void received(ActionInvocation<?> invocation, TransportInfo info) {
                    App.post(() -> {
                        if (!active || currentGeneration != generation) return;
                        transportState = info.getCurrentTransportState();
                        if (transportState == TransportState.STOPPED && nearEnd && !autoNextAttempted && episodes != null
                                && episodes.hasNext() && PlayerSetting.isAutoNextEnabled()) {
                            if (++endedPolls >= 2) {
                                autoNextAttempted = true;
                                nextEpisode();
                            }
                        } else if (transportState != TransportState.STOPPED) {
                            endedPolls = 0;
                        }
                        onPollResult(result.transport(true));
                        notifyChanged();
                    });
                }

                @Override
                public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
                    App.post(() -> {
                        if (!active || currentGeneration != generation) return;
                        onPollResult(result.transport(false));
                    });
                }
            });
            App.post(() -> {
                if (active && currentGeneration == generation) onPollResult(result.timeout());
            }, POLL_TIMEOUT);
            if (renderingControl != null && pollCount++ % 3 == 0) pollVolume(currentGeneration);
        }
        App.post(pollTask, POLL_INTERVAL);
    }

    private void onPollResult(CastPollResult.Outcome outcome) {
        CastConnectionHealth.Status before = connection.status();
        if (outcome == CastPollResult.Outcome.ALIVE) connection.pollSucceeded();
        if (outcome == CastPollResult.Outcome.FAILED) connection.pollFailed();
        if (connection.status() != before) notifyChanged();
    }

    private void pollVolume(long currentGeneration) {
        controlPoint.execute(new GetVolume(renderingControl) {
            @Override
            public void received(ActionInvocation<?> invocation, int currentVolume) {
                App.post(() -> {
                    if (!active || currentGeneration != generation) return;
                    volume = Math.max(0, Math.min(currentVolume, 100));
                    notifyChanged();
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse response, String message) {
            }
        });
    }

    private synchronized void finish() {
        if (!active && !retained) return;
        saveCastProgress(true);
        generation++;
        switching = false;
        changingEpisode = false;
        episodes = null;
        history = null;
        App.removeCallbacks(pollTask, disconnectTask);
        if (retryTask != null) App.removeCallbacks(retryTask);
        active = false;
        connection.reset();
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
                        boolean volumeSupported, TransportState transportState,
                        CastConnectionHealth.Status connection, boolean switching,
                        boolean hasPreviousEpisode, boolean hasNextEpisode, boolean changingEpisode,
                        boolean canResumeOnPhone) {
    }

    public interface Listener {

        void onStateChanged(State state);
    }

    private static class Loader {
        static final DLNACastSession INSTANCE = new DLNACastSession();
    }
}

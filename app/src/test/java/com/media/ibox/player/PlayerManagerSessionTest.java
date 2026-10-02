package com.media.ibox.player;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.text.TextUtils;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import com.media.ibox.App;
import com.media.ibox.player.engine.PlayerEngine;
import com.media.ibox.player.engine.PlayerEngineFactory;
import com.media.ibox.player.media.PlaySpec;
import com.media.ibox.setting.DanmakuSetting;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.utils.ResUtil;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.List;

public class PlayerManagerSessionTest {
    @Test public void playingChangesKeepTheirRequestAndRejectObsoleteListeners() {
        try (Harness h = new Harness()) {
            Object old = new Object();
            Object current = new Object();
            h.manager.start(h.spec, 1000, 0, old);
            Player.Listener obsolete = h.latestListener();
            h.manager.start(h.spec, 1000, 0, current);
            h.latestListener().onIsPlayingChanged(true);
            h.latestListener().onIsPlayingChanged(false);
            verify(h.callback).onIsPlayingChanged(current, true);
            verify(h.callback).onIsPlayingChanged(current, false);
            clearInvocations(h.callback);
            obsolete.onIsPlayingChanged(false);
            verifyNoInteractions(h.callback);
        }
    }

    @Test public void stoppedVodCannotEmitTimeoutOrErrorWhileLiveUrlIsStillResolving() {
        try (Harness h = new Harness()) {
            h.manager.start(h.spec, 1000, 0);
            Player.Listener vodListener = h.latestListener();
            Runnable vodTimeout = h.timeouts.get(h.timeouts.size() - 1);
            h.manager.stop(); // Live stops VOD before requesting its unresolved URL.
            clearInvocations(h.callback, h.engine);

            vodTimeout.run(); // Already dequeued work must also be harmless.
            vodListener.onPlayerError(h.error);
            vodListener.onPlaybackStateChanged(Player.STATE_READY);

            verifyNoInteractions(h.callback);
            verify(h.engine, never()).handleError(any());
            verify(h.engine, never()).stop();
            h.app.verify(() -> App.removeCallbacks(vodTimeout), atLeastOnce());
            h.manager.start(h.spec, 1000, 0);
            h.latestListener().onPlayerError(h.error);
            verify(h.callback).onError(any(PlaybackFailure.class));
        }
    }

    @Test public void oldListenerAndTimeoutCannotEmitEventsForNewPlaybackRequest() {
        try (Harness h = new Harness()) {
            Object old = new Object();
            Object current = new Object();
            h.manager.start(h.spec, 1000, 0, old);
            Player.Listener obsolete = h.latestListener();
            Runnable obsoleteTimeout = h.timeouts.get(h.timeouts.size() - 1);
            h.manager.start(h.spec, 1000, 0, current);
            clearInvocations(h.callback, h.engine);

            obsolete.onPlaybackStateChanged(Player.STATE_READY);
            obsolete.onPlayerError(h.error);
            obsoleteTimeout.run();

            verifyNoInteractions(h.callback);
            verify(h.engine, never()).handleError(any());
            verify(h.engine, never()).stop();
            h.latestListener().onPlaybackStateChanged(Player.STATE_READY);
            verify(h.callback).onPlaybackStateChanged(current, Player.STATE_READY);
            h.latestListener().onPlayerError(h.error);
            verify(h.callback).onError(eq(current), any(PlaybackFailure.class));
        }
    }

    @Test public void legacyVodCallbackStillReceivesTypedFailure() {
        try (Harness h = new Harness()) {
            h.manager.start(h.spec, 1000, 0);
            h.latestListener().onPlayerError(h.error);
            verify(h.callback).onError(any(PlaybackFailure.class));
            verify(h.callback, never()).onError(any(), any());
        }
    }

    private static final class Harness implements AutoCloseable {
        final MockedStatic<App> app = mockStatic(App.class);
        final MockedStatic<ResUtil> resources = mockStatic(ResUtil.class);
        final MockedStatic<TextUtils> text = mockStatic(TextUtils.class);
        final MockedStatic<PlayerSetting> settings = mockStatic(PlayerSetting.class);
        final MockedStatic<DanmakuSetting> danmaku = mockStatic(DanmakuSetting.class);
        final MockedStatic<PlayerEngineFactory> engines = mockStatic(PlayerEngineFactory.class);
        final PlayerManager.Callback callback = mock(PlayerManager.Callback.class);
        final PlayerEngine engine = mock(PlayerEngine.class);
        final Player player = mock(Player.class);
        final PlaySpec spec = mock(PlaySpec.class);
        final PlaybackException error = mock(PlaybackException.class);
        final List<Player.Listener> listeners = new ArrayList<>();
        final List<Runnable> timeouts = new ArrayList<>();
        final PlayerManager manager;

        Harness() {
            text.when(() -> TextUtils.isEmpty(any())).thenAnswer(call -> call.getArgument(0) == null || call.getArgument(0).toString().isEmpty());
            resources.when(() -> ResUtil.getString(anyInt())).thenReturn("");
            app.when(() -> App.post(any(Runnable.class), anyLong())).thenAnswer(call -> { timeouts.add(call.getArgument(0)); return null; });
            engines.when(() -> PlayerEngineFactory.create(anyInt(), anyInt(), any())).thenAnswer(call -> { listeners.add(call.getArgument(2)); return engine; });
            engines.when(() -> PlayerEngineFactory.matches(any(), any(), anyInt())).thenReturn(true);
            when(engine.getPlayer()).thenReturn(player);
            when(engine.handleError(any())).thenReturn(PlayerEngine.ErrorAction.FATAL);
            when(engine.getErrorMessage(any())).thenReturn("error");
            doAnswer(call -> { listeners.add(call.getArgument(0)); return null; }).when(player).addListener(any());
            when(spec.getUrl()).thenReturn("https://live");
            when(spec.checkUa()).thenReturn(spec);
            manager = new PlayerManager(callback);
        }

        Player.Listener latestListener() { return listeners.get(listeners.size() - 1); }
        @Override public void close() {
            manager.release();
            engines.close(); danmaku.close(); settings.close(); text.close(); resources.close(); app.close();
        }
    }
}

package com.media.ibox.ui.activity;

import com.media.ibox.player.PlaybackFailure;
import com.media.ibox.player.PlayerManager;
import com.media.ibox.playback.live.LivePlaybackController;
import com.media.ibox.playback.live.LivePlayRequest;
import androidx.media3.common.Player;
import java.lang.reflect.Field;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class LivePlaybackEventsTest {
    @Test public void liveStateBridgeIncludesBufferingAndPausedReady() throws Exception {
        LiveActivity activity = mock(LiveActivity.class, CALLS_REAL_METHODS);
        LivePlaybackController controller = mock(LivePlaybackController.class);
        LivePlayRequest request = mock(LivePlayRequest.class);
        PlayerManager player = mock(PlayerManager.class);
        Field field = LiveActivity.class.getDeclaredField("mLive");
        field.setAccessible(true);
        field.set(activity, controller);
        doReturn(player).when(activity).player();
        doNothing().when(activity).onStateChanged(anyInt());
        when(controller.isCurrentPlayback(request)).thenReturn(true);

        activity.onPlaybackRequestStateChanged(request, Player.STATE_BUFFERING);
        activity.onPlaybackRequestStateChanged(request, Player.STATE_READY);

        verify(controller).onPlaybackStateChanged(request, Player.STATE_BUFFERING, false);
        verify(controller).onPlaybackStateChanged(request, Player.STATE_READY, false);
    }

    @Test public void anonymousPlayerErrorCannotBecomeCurrentLiveResolverFailure() {
        LiveActivity activity = mock(LiveActivity.class, CALLS_REAL_METHODS);
        // There is no resolved live player yet. An anonymous VOD error must be ignored.
        activity.onError(PlaybackFailure.timeout("old VOD timeout"));
        verify(activity, never()).onError(anyString());
    }

    @Test public void vodStillDelegatesTypedFailureToItsStringCallback() {
        PlaybackActivity activity = mock(PlaybackActivity.class, CALLS_REAL_METHODS);
        activity.onError(PlaybackFailure.timeout("VOD timeout"));
        verify(activity).onError("VOD timeout");
    }
}

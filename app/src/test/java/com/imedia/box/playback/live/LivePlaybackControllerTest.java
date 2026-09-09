package com.imedia.box.playback.live;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.imedia.box.bean.Channel;
import com.imedia.box.bean.EpgData;

import org.junit.Test;

public class LivePlaybackControllerTest {

    @Test
    public void resumesOnlyTheSameActiveCatchupRequest() {
        Channel channel = new Channel();
        EpgData active = new EpgData();
        EpgData other = new EpgData();
        LivePlayRequest live = LivePlayRequest.live(channel, 160_000);
        LivePlayRequest catchup = LivePlayRequest.catchup(channel, active, 0);

        assertFalse(LivePlaybackController.isActiveCatchup(live, active));
        assertFalse(LivePlaybackController.isActiveCatchup(catchup, other));
        assertTrue(LivePlaybackController.isActiveCatchup(catchup, active));
    }

    @Test
    public void stateTracksPendingAndPlayingCatchupRequests() {
        LivePlaybackState state = new LivePlaybackState();
        Channel channel = new Channel();
        LivePlayRequest live = LivePlayRequest.live(channel, 0);
        LivePlayRequest catchup = LivePlayRequest.catchup(channel, new EpgData(), 0);

        assertFalse(state.isCatchup());
        state.setPendingRequest(live);
        assertFalse(state.isCatchup());
        state.setPendingRequest(catchup);
        assertTrue(state.isCatchup());
        state.setPlayingRequest(catchup, "catchup");
        assertTrue(state.isCatchup());
        state.clearPlayback();
        assertFalse(state.isCatchup());
    }
}

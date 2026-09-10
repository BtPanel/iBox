package com.imedia.box.playback.live;

import static com.imedia.box.playback.live.LiveFailureKind.DECODE;
import static com.imedia.box.playback.live.LiveFailureKind.NETWORK;
import static com.imedia.box.playback.live.LiveFailureKind.PLAYBACK;
import static com.imedia.box.playback.live.LiveFailureKind.SOURCE;
import static com.imedia.box.playback.live.LiveFailureKind.TIMEOUT;
import static com.imedia.box.playback.live.LiveRecoveryDecision.Action.FAIL;
import static com.imedia.box.playback.live.LiveRecoveryDecision.Action.RETRY_CURRENT;
import static com.imedia.box.playback.live.LiveRecoveryDecision.Action.SWITCH_LINE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class LiveRecoveryPolicyTest {

    @Test
    public void networkRetriesOnceThenSwitches() {
        assertEquals(RETRY_CURRENT, decide(NETWORK, false, List.of(1)).action());
        assertEquals(1, decide(NETWORK, true, List.of(1)).lineIndex());
        assertEquals(SWITCH_LINE, decide(NETWORK, true, List.of(1)).action());
    }

    @Test
    public void sourceAndDecodeSkipSameLineRetry() {
        assertEquals(SWITCH_LINE, decide(SOURCE, false, List.of(1)).action());
        assertEquals(SWITCH_LINE, decide(DECODE, false, List.of(1)).action());
    }

    @Test
    public void timeoutAndPlaybackRetryOnce() {
        assertEquals(RETRY_CURRENT, decide(TIMEOUT, false, List.of(1)).action());
        assertEquals(RETRY_CURRENT, decide(PLAYBACK, false, List.of(1)).action());
        assertEquals(SWITCH_LINE, decide(TIMEOUT, true, List.of(1)).action());
        assertEquals(SWITCH_LINE, decide(PLAYBACK, true, List.of(1)).action());
    }

    @Test
    public void disabledSettingsEndRecoveryAtTheirBoundary() {
        assertEquals(SWITCH_LINE, decide(NETWORK, false, List.of(1), false, true).action());
        assertEquals(FAIL, decide(NETWORK, true, List.of(1), true, false).action());
        assertEquals(FAIL, decide(SOURCE, false, List.of(), true, true).action());
    }

    @Test
    public void retryNormalizesNegativeDelayAndKeepsCurrentLine() {
        LiveRecoveryDecision decision = decide(NETWORK, false, List.of(1), true, true, -25);

        assertEquals(RETRY_CURRENT, decision.action());
        assertEquals(7, decision.lineIndex());
        assertEquals(0, decision.delayMs());
    }

    @Test
    public void switchUsesFirstRankedCandidateWithoutReordering() {
        LiveRecoveryDecision decision = decide(SOURCE, false, List.of(4, 2, 9));

        assertEquals(SWITCH_LINE, decision.action());
        assertEquals(4, decision.lineIndex());
        assertEquals(0, decision.delayMs());
    }

    @Test
    public void emptyOrNullCandidatesFailWhenRetryIsUnavailable() {
        assertEquals(FAIL, decide(NETWORK, true, List.of()).action());
        assertEquals(FAIL, decide(NETWORK, true, null).action());
    }

    private static LiveRecoveryDecision decide(LiveFailureKind failure,
            boolean retriedCurrent, List<Integer> candidates) {
        return decide(failure, retriedCurrent, candidates, true, true, 1_000);
    }

    private static LiveRecoveryDecision decide(LiveFailureKind failure,
            boolean retriedCurrent, List<Integer> candidates,
            boolean reconnectEnabled, boolean changeLineEnabled) {
        return decide(failure, retriedCurrent, candidates,
                reconnectEnabled, changeLineEnabled, 1_000);
    }

    private static LiveRecoveryDecision decide(LiveFailureKind failure,
            boolean retriedCurrent, List<Integer> candidates,
            boolean reconnectEnabled, boolean changeLineEnabled, long reconnectDelayMs) {
        return new LiveRecoveryPolicy().decide(failure, 7, retriedCurrent, candidates,
                reconnectEnabled, changeLineEnabled, reconnectDelayMs);
    }
}

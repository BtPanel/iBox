package com.media.ibox.playback.live;

import java.util.List;

public class LiveRecoveryPolicy {

    public LiveRecoveryDecision decide(
            LiveFailureKind failure,
            int currentLine,
            boolean retriedCurrent,
            List<Integer> candidates,
            boolean reconnectEnabled,
            boolean changeLineEnabled,
            long reconnectDelayMs) {
        boolean retryable = failure == LiveFailureKind.NETWORK
                || failure == LiveFailureKind.TIMEOUT
                || failure == LiveFailureKind.PLAYBACK;
        if (retryable && reconnectEnabled && !retriedCurrent) {
            return LiveRecoveryDecision.retryCurrent(currentLine, reconnectDelayMs);
        }
        if (changeLineEnabled && candidates != null && !candidates.isEmpty()) {
            return LiveRecoveryDecision.switchLine(candidates.get(0));
        }
        return LiveRecoveryDecision.fail();
    }
}

package com.media.ibox.playback.live;

public record LiveRecoveryDecision(Action action, int lineIndex, long delayMs) {

    public enum Action { RETRY_CURRENT, SWITCH_LINE, FAIL }

    public static LiveRecoveryDecision retryCurrent(int lineIndex, long delayMs) {
        return new LiveRecoveryDecision(Action.RETRY_CURRENT, lineIndex, Math.max(0, delayMs));
    }

    public static LiveRecoveryDecision switchLine(int targetIndex) {
        return new LiveRecoveryDecision(Action.SWITCH_LINE, targetIndex, 0);
    }

    public static LiveRecoveryDecision fail() {
        return new LiveRecoveryDecision(Action.FAIL, -1, 0);
    }
}

package com.media.ibox.dlna;

final class CastPollResult {

    enum Outcome {
        WAITING, ALIVE, FAILED
    }

    private Boolean position;
    private Boolean transport;
    private boolean settled;

    Outcome position(boolean success) {
        position = success;
        return outcome(success);
    }

    Outcome transport(boolean success) {
        transport = success;
        return outcome(success);
    }

    Outcome timeout() {
        if (settled) return Outcome.WAITING;
        settled = true;
        return Outcome.FAILED;
    }

    private Outcome outcome(boolean success) {
        if (settled) return Outcome.WAITING;
        if (success) {
            settled = true;
            return Outcome.ALIVE;
        }
        if (Boolean.FALSE.equals(position) && Boolean.FALSE.equals(transport)) {
            settled = true;
            return Outcome.FAILED;
        }
        return Outcome.WAITING;
    }
}

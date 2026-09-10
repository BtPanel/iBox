package com.imedia.box.playback.live;

public record LiveConnectionState(Type type, int fromLine, int line, int total,
                                  int attempt, int delaySeconds, String message) {
    public enum Type { CONNECTING, RECONNECTING, SWITCHING, PLAYING, FAILED }

    public LiveConnectionState {
        message = message == null ? "" : message;
    }

    public static LiveConnectionState connecting(int line, int total) {
        return new LiveConnectionState(Type.CONNECTING, -1, line, total, 0, 0, "");
    }

    public static LiveConnectionState reconnecting(int line, int total, int delaySeconds) {
        return new LiveConnectionState(Type.RECONNECTING, -1, line, total, 1, Math.max(0, delaySeconds), "");
    }

    public static LiveConnectionState switching(int fromLine, int line, int total) {
        return new LiveConnectionState(Type.SWITCHING, fromLine, line, total, 0, 0, "");
    }

    public static LiveConnectionState playing(int line, int total) {
        return new LiveConnectionState(Type.PLAYING, -1, line, total, 0, 0, "");
    }

    public static LiveConnectionState failed(String message) {
        return new LiveConnectionState(Type.FAILED, -1, -1, 0, 0, 0, message);
    }
}

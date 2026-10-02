package com.media.ibox.live;

public final class LiveProgramLabel {

    private LiveProgramLabel() {
    }

    public static String format(String start, String title, boolean includeTime) {
        if (!includeTime || start.isEmpty()) return title;
        return start + " " + title;
    }

    public static String join(String now, String next) {
        return next.isEmpty() ? now : now + "  \u203A  " + next;
    }
}

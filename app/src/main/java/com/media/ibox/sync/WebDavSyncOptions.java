package com.media.ibox.sync;

import com.github.catvod.utils.Prefers;

public final class WebDavSyncOptions {

    public static final int FAVORITES = 1;
    public static final int HISTORY = 1 << 1;
    public static final int PROGRESS = 1 << 2;
    public static final int SUBSCRIPTIONS = 1 << 3;
    public static final int SETTINGS = 1 << 4;
    public static final int ALL = FAVORITES | HISTORY | PROGRESS | SUBSCRIPTIONS | SETTINGS;

    private static final String KEY_SELECTION = "webdav_selection";

    private final int selection;

    public WebDavSyncOptions(int selection) {
        this.selection = selection;
    }

    public static WebDavSyncOptions saved() {
        return new WebDavSyncOptions(Prefers.getInt(KEY_SELECTION, ALL));
    }

    public int value() {
        return selection;
    }

    public boolean includes(int item) {
        return (selection & item) != 0;
    }

    public boolean isEmpty() {
        return selection == 0;
    }

    public void save() {
        Prefers.put(KEY_SELECTION, selection);
    }
}

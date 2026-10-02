package com.media.ibox.playback.live;

import com.media.ibox.App;

public interface LivePlaybackScheduler {
    void post(Runnable runnable, long delayMs);
    void remove(Runnable runnable);

    static LivePlaybackScheduler app() {
        return new LivePlaybackScheduler() {
            @Override public void post(Runnable runnable, long delayMs) { App.post(runnable, delayMs); }
            @Override public void remove(Runnable runnable) { App.removeCallbacks(runnable); }
        };
    }
}

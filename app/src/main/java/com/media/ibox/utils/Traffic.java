package com.media.ibox.utils;

import android.net.TrafficStats;
import android.view.View;
import android.widget.TextView;

import com.media.ibox.App;

import java.text.DecimalFormat;

public class Traffic {

    private static final DecimalFormat format = new DecimalFormat("#.0");
    private static final int UID = App.get().getApplicationInfo().uid;
    private static final String UNIT_KB = " KB/s";
    private static final String UNIT_MB = " MB/s";

    private static long lastTotalRxBytes;
    private static long lastTimeStamp;

    public static void setSpeed(TextView view) {
        if (TrafficStats.getUidRxBytes(UID) == TrafficStats.UNSUPPORTED) return;
        view.setVisibility(View.VISIBLE);
        view.setText(getSpeed());
    }

    private static String getSpeed() {
        long nowTimeStamp = System.currentTimeMillis();
        long nowTotalRxBytes = TrafficStats.getUidRxBytes(UID) / 1024;
        long speed = (nowTotalRxBytes - lastTotalRxBytes) * 1000 / Math.max(nowTimeStamp - lastTimeStamp, 1);
        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
        return speed < 1000 ? speed + UNIT_KB : format.format(speed / 1024f) + UNIT_MB;
    }

    public static void reset() {
        lastTotalRxBytes = 0;
        lastTimeStamp = 0;
    }

    public static Meter meter() {
        return new Meter();
    }

    public static final class Meter {

        private long lastTotalRxBytes;
        private long lastTimeStamp;

        public void setSpeed(TextView view) {
            long total = TrafficStats.getUidRxBytes(UID);
            if (total == TrafficStats.UNSUPPORTED) {
                view.setVisibility(View.GONE);
                return;
            }
            long now = System.currentTimeMillis();
            long totalKb = total / 1024;
            long speed = lastTimeStamp == 0 ? 0 : (totalKb - lastTotalRxBytes) * 1000 / Math.max(now - lastTimeStamp, 1);
            lastTimeStamp = now;
            lastTotalRxBytes = totalKb;
            view.setVisibility(View.VISIBLE);
            view.setText(speed < 1000 ? speed + UNIT_KB : format.format(speed / 1024f) + UNIT_MB);
        }

        public void reset() {
            lastTotalRxBytes = 0;
            lastTimeStamp = 0;
        }
    }
}

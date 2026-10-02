package com.media.ibox.offline;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadNotificationHelper;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.PlatformScheduler;
import androidx.media3.exoplayer.scheduler.Requirements;
import androidx.media3.exoplayer.scheduler.Scheduler;

import com.media.ibox.R;
import com.media.ibox.ui.activity.OfflineDownloadActivity;

import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class OfflineDownloadService extends DownloadService {

    private static final int FOREGROUND_NOTIFICATION_ID = 3201;
    private static final int JOB_ID = 3202;

    private DownloadNotificationHelper notificationHelper;

    public OfflineDownloadService() {
        super(
                FOREGROUND_NOTIFICATION_ID,
                DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
                OfflineDownloadManager.CHANNEL_ID,
                R.string.download_notification_channel,
                0);
    }

    @Override
    protected DownloadManager getDownloadManager() {
        return OfflineDownloadManager.get();
    }

    @Nullable
    @Override
    protected Scheduler getScheduler() {
        return new PlatformScheduler(this, JOB_ID);
    }

    @Override
    protected Notification getForegroundNotification(List<Download> downloads, @Requirements.RequirementFlags int notMetRequirements) {
        return helper().buildProgressNotification(
                this,
                R.drawable.ic_notification,
                contentIntent(),
                null,
                downloads,
                notMetRequirements);
    }

    private DownloadNotificationHelper helper() {
        if (notificationHelper == null) notificationHelper = new DownloadNotificationHelper(this, OfflineDownloadManager.CHANNEL_ID);
        return notificationHelper;
    }

    private PendingIntent contentIntent() {
        Intent intent = new Intent(this, OfflineDownloadActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

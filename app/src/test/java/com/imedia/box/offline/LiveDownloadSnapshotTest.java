package com.imedia.box.offline;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadProgress;
import androidx.media3.exoplayer.offline.DownloadRequest;
import java.util.List;
import org.junit.Test;

public class LiveDownloadSnapshotTest {

    @Test
    public void activeProgressOverridesStaleIndexWhileCompletedItemsRemain() {
        Download indexed = download("active", Download.STATE_DOWNLOADING, 1_000);
        Download live = download("active", Download.STATE_DOWNLOADING, 2_000);
        Download completed = download("completed", Download.STATE_COMPLETED, 3_000);

        List<Download> result = OfflineDownloadManager.withLiveProgress(
                List.of(indexed, completed), List.of(live));

        assertEquals(2_000, result.stream().filter(item -> item.request.id.equals("active"))
                .findFirst().orElseThrow().getBytesDownloaded());
        assertEquals(3_000, result.stream().filter(item -> item.request.id.equals("completed"))
                .findFirst().orElseThrow().getBytesDownloaded());
        assertEquals(2, result.size());
    }

    private static Download download(String id, int state, long bytes) {
        Uri uri = mock(Uri.class);
        when(uri.getScheme()).thenReturn("https");
        when(uri.toString()).thenReturn("https://example.com/video.mp4");
        when(uri.getLastPathSegment()).thenReturn("video.mp4");
        when(uri.getPath()).thenReturn("/video.mp4");
        DownloadProgress progress = new DownloadProgress();
        progress.bytesDownloaded = bytes;
        return new Download(new DownloadRequest.Builder(id, uri).build(), state,
                1, 1, 4_000, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE, progress);
    }
}

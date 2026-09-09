package com.imedia.box.offline;

import android.content.Context;

import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.DownloadService;

import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.api.SiteApi;
import com.imedia.box.bean.Episode;
import com.imedia.box.bean.Flag;
import com.imedia.box.bean.Result;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.Task;

import java.util.List;

public final class OfflineDownloadCoordinator {

    private OfflineDownloadCoordinator() {
    }

    public static void enqueue(String siteKey, String vodId, String vodName, String vodPic, Flag flag, List<Episode> episodes) {
        if (flag == null || episodes == null || episodes.isEmpty()) return;
        Notify.show(App.get().getString(R.string.download_preparing, episodes.size()));
        Task.executeSerial(() -> resolveAndEnqueue(siteKey, vodId, vodName, vodPic, flag, episodes));
    }

    private static void resolveAndEnqueue(String siteKey, String vodId, String vodName, String vodPic, Flag flag, List<Episode> episodes) {
        int added = 0;
        int unsupported = 0;
        Context context = App.get();
        String offlineVodPic = OfflineArtworkManager.cache(siteKey, vodId, vodPic);
        for (Episode episode : episodes) {
            try {
                Result result = SiteApi.playerContent(siteKey, flag.getFlag(), episode.getUrl());
                if (!OfflineDownloadManager.supports(result)) {
                    unsupported++;
                    continue;
                }
                OfflineDownloadMetadata metadata = new OfflineDownloadMetadata(vodName, offlineVodPic, siteKey, vodId, flag.getShow(), episode.getName(), episode.getUrl(), result.getHeader());
                DownloadRequest request = OfflineDownloadManager.createRequest(result, metadata);
                DownloadService.sendAddDownload(context, OfflineDownloadService.class, request, true);
                added++;
            } catch (Exception ignored) {
                unsupported++;
            }
        }
        if (added == 0) {
            OfflineArtworkManager.deleteIfUnused(new OfflineDownloadMetadata(vodName, offlineVodPic, siteKey, vodId, flag.getShow(), "", "", null));
        }
        int success = added;
        int failed = unsupported;
        App.post(() -> {
            if (success == 0) Notify.show(R.string.download_unsupported);
            else if (failed == 0) Notify.show(context.getString(R.string.download_added, success));
            else Notify.show(context.getString(R.string.download_added_partial, success, failed));
        });
    }
}

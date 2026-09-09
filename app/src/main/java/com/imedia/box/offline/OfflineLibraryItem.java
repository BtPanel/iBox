package com.imedia.box.offline;

import androidx.media3.exoplayer.offline.Download;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OfflineLibraryItem(String key, String name, String pic, List<OfflineDownloadItem> episodes) {

    public static List<OfflineLibraryItem> fromDownloads(List<Download> downloads) {
        Map<String, List<OfflineDownloadItem>> groups = new LinkedHashMap<>();
        for (Download download : downloads) {
            if (download.state != Download.STATE_COMPLETED) continue;
            OfflineDownloadItem item = OfflineDownloadItem.from(download);
            String key = item.metadata().getSiteKey() + '\u001f' + item.metadata().getVodId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        List<OfflineLibraryItem> result = new ArrayList<>();
        for (Map.Entry<String, List<OfflineDownloadItem>> entry : groups.entrySet()) {
            List<OfflineDownloadItem> items = entry.getValue();
            items.sort(Comparator.comparingLong(item -> item.download().updateTimeMs));
            OfflineDownloadMetadata metadata = items.get(0).metadata();
            result.add(new OfflineLibraryItem(entry.getKey(), metadata.getVodName(), metadata.getVodPic(), List.copyOf(items)));
        }
        result.sort(Comparator.comparingLong(OfflineLibraryItem::updatedAt).reversed());
        return result;
    }

    public long bytes() {
        return episodes.stream().mapToLong(item -> Math.max(0, item.download().getBytesDownloaded())).sum();
    }

    public long updatedAt() {
        return episodes.stream().mapToLong(item -> item.download().updateTimeMs).max().orElse(0);
    }
}

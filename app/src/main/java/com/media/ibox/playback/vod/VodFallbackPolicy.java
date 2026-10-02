package com.media.ibox.playback.vod;

import com.media.ibox.api.config.VodConfig;
import com.media.ibox.bean.Flag;
import com.media.ibox.bean.Result;
import com.media.ibox.bean.Site;
import com.media.ibox.bean.Vod;
import com.media.ibox.setting.Setting;

import java.util.ArrayList;
import java.util.List;

class VodFallbackPolicy {

    private final VodPlaybackController controller;
    private final VodPlaybackState state;
    private final VodPlaybackHost host;

    VodFallbackPolicy(VodPlaybackController controller, VodPlaybackState state, VodPlaybackHost host) {
        this.controller = controller;
        this.state = state;
        this.host = host;
    }

    void playbackError() {
        fallbackToNextLineOrSource();
    }

    void emptyFlag() {
        fallbackToNextLineOrSource();
    }

    void emptyDetail() {
        fallbackToNextSource(false);
    }

    void manualSwitchSource() {
        fallbackToNextSource(true);
    }

    void search(String keyword, boolean autoFallback) {
        state.setSearchKeyword(keyword);
        state.setAutoFallback(autoFallback);
        state.setSelectFirstSource(autoFallback);
        host.onSearchStarted(keyword, autoFallback);
        host.requestSearch(getSearchableSites(), keyword);
    }

    void onSearchResult(Result result) {
        List<Vod> items = new ArrayList<>(result.getList());
        items.removeIf(this::mismatch);
        state.setSources(items);
        host.renderSources(state.getSources());
        host.onSearchResult();
        if (items.isEmpty()) {
            if (state.isAutoFallback()) host.showFallbackExhausted();
            return;
        }
        if (state.isSelectFirstSource()) nextSource();
    }

    private void fallbackToNextLineOrSource() {
        if (!host.isSiteChangeable()) return;
        if (Setting.isAutoFallbackLine() && fallbackToNextLine()) return;
        if (Setting.isAutoFallbackSource()) fallbackToNextSource(false);
    }

    private boolean fallbackToNextLine() {
        if (!state.hasFlags()) return false;
        state.addFailedFlag(state.getFlag().getFlag());
        int start = state.getFlagPosition();
        for (int offset = 1; offset < state.getFlags().size(); offset++) {
            Flag flag = state.getFlags().get((start + offset) % state.getFlags().size());
            if (state.hasFailedFlag(flag.getFlag())) continue;
            host.showSwitchLine(flag);
            controller.selectFlag(flag);
            return true;
        }
        return false;
    }

    private void fallbackToNextSource(boolean force) {
        if (!force && !Setting.isAutoFallbackSource()) return;
        if (!state.hasSources()) search(host.getVodName(), true);
        else {
            if (!force) state.setAutoFallback(true);
            nextSource();
        }
    }

    private void nextSource() {
        if (!state.hasSources()) return;
        Vod item = state.removeFirstSource();
        host.renderSources(state.getSources());
        host.showSwitchSource(item);
        state.addFailedSource(host.getVodKey(), host.getVodId());
        state.setSelectFirstSource(false);
        controller.fallbackSource(item);
    }

    private List<Site> getSearchableSites() {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (isPass(site)) sites.add(site);
        return sites;
    }

    private boolean isPass(Site item) {
        if (state.isAutoFallback() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private boolean mismatch(Vod item) {
        if (host.getVodKey().equals(item.getSiteKey()) && host.getVodId().equals(item.getId())) return true;
        if (state.hasFailedSource(item.getSiteKey(), item.getId())) return true;
        if (state.isAutoFallback()) return !item.getName().equals(state.getSearchKeyword());
        return !item.getName().contains(state.getSearchKeyword());
    }
}

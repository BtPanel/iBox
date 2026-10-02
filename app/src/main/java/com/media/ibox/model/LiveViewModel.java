package com.media.ibox.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.media3.common.C;

import com.media.ibox.Constant;
import com.media.ibox.api.LiveApi;
import com.media.ibox.bean.Channel;
import com.media.ibox.bean.Epg;
import com.media.ibox.bean.Live;
import com.media.ibox.bean.Result;
import com.media.ibox.exception.ExtractException;
import com.media.ibox.playback.PlaybackResult;
import com.media.ibox.playback.live.LivePlayRequest;
import com.media.ibox.playback.live.LivePlaybackController;
import com.media.ibox.playback.live.LivePlaybackHost;
import com.media.ibox.playback.live.LivePlaybackState;

import java.time.ZoneId;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class LiveViewModel extends ViewModel {

    private final MutableLiveData<PlaybackResult<LivePlayRequest>> playback;
    private final MutableLiveData<String> error;
    private final MutableLiveData<XmlResult> xml;
    private final MutableLiveData<Live> live;
    private final MutableLiveData<Epg> epg;
    private final MutableLiveData<Channel> epgGuide;

    private final ViewModelTaskRunner<TaskType> tasks;
    private final LivePlaybackState playbackState;
    private volatile boolean xmlLoading;
    private volatile long xmlRequestId;
    private volatile ZoneId zoneId;

    public LiveViewModel() {
        this.epg = new MutableLiveData<>();
        this.epgGuide = new MutableLiveData<>();
        this.xml = new MutableLiveData<>();
        this.live = new MutableLiveData<>();
        this.error = new MutableLiveData<>();
        this.playback = new MutableLiveData<>();
        this.playbackState = new LivePlaybackState();
        this.tasks = new ViewModelTaskRunner<>(TaskType.class);
        this.xmlLoading = false;
        this.zoneId = ZoneId.systemDefault();
    }

    public LiveData<PlaybackResult<LivePlayRequest>> playback() {
        return playback;
    }

    public LiveData<String> error() {
        return error;
    }

    public LiveData<XmlResult> xml() {
        return xml;
    }

    public boolean isCurrentXmlResult(XmlResult result) {
        return result != null && result.requestId() == xmlRequestId;
    }

    public void invalidateXml() {
        ++xmlRequestId;
        xmlLoading = false;
    }

    public LiveData<Epg> epg() {
        return epg;
    }

    public LiveData<Channel> epgGuide() {
        return epgGuide;
    }

    public LiveData<Live> live() {
        return live;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public LivePlaybackController createPlaybackController(LivePlaybackHost host) {
        return new LivePlaybackController(host, playbackState);
    }

    public void parse(Live item) {
        error.setValue(null);
        execute(TaskType.LIVE, () -> {
            LiveApi.parse(item);
            return item;
        }, result -> {
            setTimeZone(result);
            live.postValue(result);
        }, this::handleParseError);
    }

    public long parseXml(Live item) {
        return parseXml(item, false);
    }

    public long parseXml(Live item, boolean forceRefresh) {
        long requestId = ++xmlRequestId;
        xmlLoading = true;
        execute(TaskType.XML, () -> LiveApi.parseXml(item, forceRefresh), result -> {
            xmlLoading = false;
            xml.postValue(new XmlResult(requestId, result));
        }, error -> {
            xmlLoading = false;
            xml.postValue(new XmlResult(requestId, false));
        });
        return requestId;
    }

    public static final class XmlResult {

        private final long requestId;
        private final boolean success;

        public XmlResult(long requestId, boolean success) {
            this.requestId = requestId;
            this.success = success;
        }

        public long requestId() {
            return requestId;
        }

        public boolean success() {
            return success;
        }
    }

    public void getEpg(Channel item) {
        if (xmlLoading) return;
        execute(TaskType.EPG, () -> LiveApi.getEpg(item, zoneId), epg::postValue, error -> epg.postValue(new Epg()));
    }

    public void getEpgGuide(Channel item) {
        getEpgGuide(item, LiveApi.GUIDE_START_OFFSET, LiveApi.GUIDE_END_OFFSET);
    }

    public void getEpgGuide(Channel item, int startOffset, int endOffset) {
        execute(TaskType.EPG_GUIDE, () -> {
            LiveApi.getEpgGuide(item, zoneId, startOffset, endOffset);
            return item;
        }, epgGuide::postValue, error -> epgGuide.postValue(item));
    }

    public void getUrl(LivePlayRequest request) {
        execute(TaskType.URL, () -> getUrlResult(request), result -> postUrl(request, result), error -> handleUrlError(request, error));
    }

    private Result getUrlResult(LivePlayRequest request) throws Exception {
        return request.isCatchup() ? LiveApi.getUrl(request.getChannel(), request.getCatchupData()) : LiveApi.getUrl(request.getChannel());
    }

    private void postUrl(LivePlayRequest request, Result result) {
        if (request.getPosition() != C.TIME_UNSET) result.setPosition(request.getPosition());
        playback.postValue(new PlaybackResult<>(request, result));
    }

    private void handleParseError(Throwable t) {
        String message = t.getMessage();
        error.postValue(message == null ? "" : message);
    }

    private void handleUrlError(LivePlayRequest request, Throwable t) {
        if (t instanceof ExtractException) postUrl(request, Result.error(t.getMessage()));
        else postUrl(request, new Result());
    }

    private void setTimeZone(Live live) {
        this.zoneId = live.getZoneId();
    }

    private <T> void execute(TaskType type, Callable<T> callable, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        tasks.execute(type, type.timeout, callable, onSuccess, onError);
    }

    @Override
    protected void onCleared() {
        tasks.cancelAll();
        playbackState.reset();
    }

    private enum TaskType {

        LIVE(Constant.TIMEOUT_LIVE),
        EPG(Constant.TIMEOUT_EPG),
        EPG_GUIDE(Constant.TIMEOUT_XML),
        XML(Constant.TIMEOUT_XML),
        URL(Constant.TIMEOUT_PARSE_LIVE);

        final long timeout;

        TaskType(long timeout) {
            this.timeout = timeout;
        }
    }
}

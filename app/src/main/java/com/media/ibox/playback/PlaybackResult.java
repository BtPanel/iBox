package com.media.ibox.playback;

import com.media.ibox.bean.Result;

public record PlaybackResult<T>(T request, Result result) {
}

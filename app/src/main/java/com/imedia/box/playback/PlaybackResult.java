package com.imedia.box.playback;

import com.imedia.box.bean.Result;

public record PlaybackResult<T>(T request, Result result) {
}

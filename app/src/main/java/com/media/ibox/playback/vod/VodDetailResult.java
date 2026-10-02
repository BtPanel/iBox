package com.media.ibox.playback.vod;

import android.text.TextUtils;

import com.media.ibox.bean.Result;

public record VodDetailResult(String key, String id, Result result) {

    public boolean matches(String key, String id) {
        return TextUtils.equals(this.key, key) && TextUtils.equals(this.id, id);
    }
}

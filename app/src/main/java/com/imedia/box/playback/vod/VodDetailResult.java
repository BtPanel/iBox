package com.imedia.box.playback.vod;

import android.text.TextUtils;

import com.imedia.box.bean.Result;

public record VodDetailResult(String key, String id, Result result) {

    public boolean matches(String key, String id) {
        return TextUtils.equals(this.key, key) && TextUtils.equals(this.id, id);
    }
}

package com.imedia.box.player.extractor;

import android.net.Uri;
import android.os.SystemClock;

import com.imedia.box.App;
import com.imedia.box.ui.activity.VideoActivity;
import com.imedia.box.utils.UrlUtil;

public class Push implements Source.Extractor {

    @Override
    public boolean match(Uri uri) {
        return "push".equals(UrlUtil.scheme(uri));
    }

    @Override
    public String fetch(String url) throws Exception {
        if (App.activity() != null) VideoActivity.start(App.activity(), url.substring(7));
        SystemClock.sleep(500);
        return "";
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}

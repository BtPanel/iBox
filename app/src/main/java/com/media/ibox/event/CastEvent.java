package com.media.ibox.event;

import com.media.ibox.bean.Config;
import com.media.ibox.bean.Device;
import com.media.ibox.bean.History;

import org.greenrobot.eventbus.EventBus;

public record CastEvent(Config config, Device device, History history) {

    public static void post(Config config, Device device, History history) {
        EventBus.getDefault().post(new CastEvent(config, device, history));
    }
}

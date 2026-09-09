package com.imedia.box.event;

import com.imedia.box.bean.Config;
import com.imedia.box.bean.Device;
import com.imedia.box.bean.History;

import org.greenrobot.eventbus.EventBus;

public record CastEvent(Config config, Device device, History history) {

    public static void post(Config config, Device device, History history) {
        EventBus.getDefault().post(new CastEvent(config, device, history));
    }
}

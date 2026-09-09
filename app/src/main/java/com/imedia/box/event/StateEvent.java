package com.imedia.box.event;

import org.greenrobot.eventbus.EventBus;

public record StateEvent(Type type, String message) {

    public static void empty() {
        EventBus.getDefault().post(new StateEvent(Type.EMPTY, ""));
    }

    public static void progress() {
        EventBus.getDefault().post(new StateEvent(Type.PROGRESS, ""));
    }

    public static void error(String message) {
        EventBus.getDefault().post(new StateEvent(Type.ERROR, message == null ? "" : message));
    }

    public enum Type {
        EMPTY, PROGRESS, ERROR
    }
}

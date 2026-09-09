package com.imedia.box.service;

import androidx.annotation.NonNull;

final class MediaControllerAccessPolicy {

    enum Access {
        FULL,
        TRUSTED,
        REJECTED
    }

    private MediaControllerAccessPolicy() {
    }

    @NonNull
    static Access decide(@NonNull String appPackage, int appUid,
                         @NonNull String controllerPackage, int controllerUid,
                         boolean trusted, boolean mediaNotificationController) {
        boolean self = appUid == controllerUid && appPackage.equals(controllerPackage);
        if (self || mediaNotificationController) return Access.FULL;
        if (trusted) return Access.TRUSTED;
        return Access.REJECTED;
    }
}

package com.media.ibox.service;

import static com.media.ibox.service.MediaControllerAccessPolicy.Access.FULL;
import static com.media.ibox.service.MediaControllerAccessPolicy.Access.REJECTED;
import static com.media.ibox.service.MediaControllerAccessPolicy.Access.TRUSTED;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MediaControllerAccessPolicyTest {

    private static final String APP_PACKAGE = "com.media.ibox";
    private static final int APP_UID = 10001;

    @Test
    public void grantsFullAccessOnlyToSamePackageAndUid() {
        assertEquals(FULL, decide(APP_PACKAGE, APP_UID, false, false));
        assertEquals(REJECTED, decide(APP_PACKAGE, APP_UID + 1, false, false));
        assertEquals(REJECTED, decide("com.example.controller", APP_UID, false, false));
    }

    @Test
    public void grantsStandardAccessToTrustedControllers() {
        assertEquals(TRUSTED, decide("com.google.android.projection.gearhead", 10020, true, false));
    }

    @Test
    public void grantsFullAccessToMediaNotificationController() {
        assertEquals(FULL, decide("androidx.media3.session.notification", 10020, false, true));
    }

    @Test
    public void rejectsUnknownExternalControllers() {
        assertEquals(REJECTED, decide("com.example.controller", 10020, false, false));
    }

    private MediaControllerAccessPolicy.Access decide(String packageName, int uid,
                                                       boolean trusted, boolean notification) {
        return MediaControllerAccessPolicy.decide(
                APP_PACKAGE, APP_UID, packageName, uid, trusted, notification);
    }
}

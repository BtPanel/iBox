package com.media.ibox.ui.overlay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FloatingWindowEntryPolicyTest {

    @Test
    public void buttonShowsOverlayOrRequestsPermissionOrFallsBack() {
        assertEquals(FloatingWindowEntryPolicy.Action.SHOW_OVERLAY,
                FloatingWindowEntryPolicy.onButtonPressed(true, true));
        assertEquals(FloatingWindowEntryPolicy.Action.REQUEST_PERMISSION,
                FloatingWindowEntryPolicy.onButtonPressed(false, true));
        assertEquals(FloatingWindowEntryPolicy.Action.NATIVE_PIP,
                FloatingWindowEntryPolicy.onButtonPressed(false, false));
    }

    @Test
    public void permissionReturnShowsOverlayOrFallsBack() {
        assertEquals(FloatingWindowEntryPolicy.Action.SHOW_OVERLAY,
                FloatingWindowEntryPolicy.afterPermissionScreen(true));
        assertEquals(FloatingWindowEntryPolicy.Action.NATIVE_PIP,
                FloatingWindowEntryPolicy.afterPermissionScreen(false));
    }
}

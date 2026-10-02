package com.media.ibox.ui.overlay;

public final class FloatingWindowEntryPolicy {

    public enum Action {
        SHOW_OVERLAY,
        REQUEST_PERMISSION,
        NATIVE_PIP
    }

    private FloatingWindowEntryPolicy() {
    }

    public static Action onButtonPressed(boolean canDrawOverlays, boolean permissionScreenAvailable) {
        if (canDrawOverlays) return Action.SHOW_OVERLAY;
        return permissionScreenAvailable ? Action.REQUEST_PERMISSION : Action.NATIVE_PIP;
    }

    public static Action afterPermissionScreen(boolean canDrawOverlays) {
        return canDrawOverlays ? Action.SHOW_OVERLAY : Action.NATIVE_PIP;
    }
}

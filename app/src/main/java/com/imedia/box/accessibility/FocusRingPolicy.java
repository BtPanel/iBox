package com.imedia.box.accessibility;

final class FocusRingPolicy {

    private FocusRingPolicy() {
    }

    static boolean shouldDraw(boolean shown, boolean focused, boolean hasIndicator, boolean excluded) {
        return shown && focused && !hasIndicator && !excluded;
    }
}

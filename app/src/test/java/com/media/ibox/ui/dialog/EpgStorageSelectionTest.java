package com.media.ibox.ui.dialog;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpgStorageSelectionTest {

    @Test
    public void externalStorageRequiresPermissionAndActualAccessOnScopedStorage() {
        assertFalse(EpgStorageSelection.canUseExternal(false, true, false));
        assertFalse(EpgStorageSelection.canUseExternal(true, true, false));
        assertTrue(EpgStorageSelection.canUseExternal(true, true, true));
        assertTrue(EpgStorageSelection.canUseExternal(true, false, false));
    }
}

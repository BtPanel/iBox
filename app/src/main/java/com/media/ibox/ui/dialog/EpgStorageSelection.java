package com.media.ibox.ui.dialog;

final class EpgStorageSelection {

    static boolean canUseExternal(boolean permissionGranted, boolean scopedStorage, boolean allFilesAccess) {
        return permissionGranted && (!scopedStorage || allFilesAccess);
    }
}

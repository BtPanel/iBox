package com.imedia.box.ui.dialog;

final class EpgStorageSelection {

    static boolean canUseExternal(boolean permissionGranted, boolean scopedStorage, boolean allFilesAccess) {
        return permissionGranted && (!scopedStorage || allFilesAccess);
    }
}

package com.media.ibox.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.ActivityCrashBinding;
import com.media.ibox.ui.base.BaseActivity;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;

public class CrashActivity extends BaseActivity {

    private ActivityCrashBinding mBinding;

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCrashBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCrash();
    }

    @Override
    protected void initEvent() {
        mBinding.details.setOnClickListener(v -> showError());
        mBinding.restart.setOnClickListener(v -> CustomActivityOnCrash.restartApplication(this, Objects.requireNonNull(CustomActivityOnCrash.getConfigFromIntent(getIntent()))));
    }

    private void setCrash() {
        saveCrashDetails();
        String log = CustomActivityOnCrash.getActivityLogFromIntent(getIntent());
        if (TextUtils.isEmpty(log)) return;
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].isEmpty()) continue;
            if (lines[i].contains(HomeActivity.class.getSimpleName())) Prefers.put("crash", true);
            break;
        }
    }

    private void saveCrashDetails() {
        String details = CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent());
        if (TextUtils.isEmpty(details)) return;
        File target = new File(getFilesDir(), "last_crash.txt");
        try (FileOutputStream stream = new FileOutputStream(target, false)) {
            stream.write(details.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private void showError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_details_title)
                .setMessage(CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent()))
                .setPositiveButton(R.string.crash_details_close, null)
                .show();
    }
}

package com.media.ibox.ui.dialog;

import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.media.ibox.R;
import com.media.ibox.databinding.DialogDownloadPolicyBinding;
import com.media.ibox.offline.DownloadPolicy;
import com.media.ibox.offline.OfflineDownloadManager;
import com.media.ibox.setting.DownloadSetting;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.utils.Util;

public class DownloadPolicyDialog extends BaseAlertDialog {

    private static final String TAG = "download-policy";

    private DialogDownloadPolicyBinding binding;
    private Runnable listener;

    public static void show(FragmentActivity activity, Runnable listener) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        DownloadPolicyDialog dialog = new DownloadPolicyDialog();
        dialog.listener = listener;
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogDownloadPolicyBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        int padding = ResUtil.dp2px(Util.isLeanback() ? 32 : 24);
        binding.getRoot().setPadding(padding, padding, padding, padding);
        DownloadPolicy policy = DownloadSetting.get();
        binding.wifiOnly.setChecked(policy.wifiOnly());
        binding.parallel.check(parallelId(policy.parallelDownloads()));
        binding.segments.check(segmentId(policy.segmentThreads()));
        binding.retry.check(retryId(policy.retryCount()));
        compactOptions(binding.parallel);
        compactOptions(binding.segments);
        compactOptions(binding.retry);
    }

    @Override
    protected void initEvent() {
        binding.wifiOnly.setOnCheckedChangeListener((button, checked) -> save());
        binding.parallel.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) save();
        });
        binding.segments.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) save();
        });
        binding.retry.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) save();
        });
    }

    private void save() {
        DownloadPolicy policy = new DownloadPolicy(binding.wifiOnly.isChecked(), parallelValue(), segmentValue(), retryValue());
        DownloadSetting.put(policy);
        OfflineDownloadManager.applyPolicy(policy);
        if (listener != null) listener.run();
    }

    private void compactOptions(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setMinimumWidth(0);
            child.setPadding(0, child.getPaddingTop(), 0, child.getPaddingBottom());
        }
    }

    private int parallelId(int value) {
        return switch (value) {
            case 1 -> R.id.parallelOne;
            case 3 -> R.id.parallelThree;
            case 4 -> R.id.parallelFour;
            case 8 -> R.id.parallelEight;
            default -> R.id.parallelTwo;
        };
    }

    private int retryId(int value) {
        return switch (value) {
            case 0 -> R.id.retryNone;
            case 1 -> R.id.retryOnce;
            case 3 -> R.id.retryThree;
            default -> R.id.retryFive;
        };
    }

    private int segmentId(int value) {
        return switch (value) {
            case 1 -> R.id.segmentOne;
            case 2 -> R.id.segmentTwo;
            case 8 -> R.id.segmentEight;
            case 16 -> R.id.segmentSixteen;
            default -> R.id.segmentFour;
        };
    }

    private int parallelValue() {
        int id = binding.parallel.getCheckedButtonId();
        if (id == R.id.parallelOne) return 1;
        if (id == R.id.parallelThree) return 3;
        if (id == R.id.parallelFour) return 4;
        if (id == R.id.parallelEight) return 8;
        return 2;
    }

    private int retryValue() {
        int id = binding.retry.getCheckedButtonId();
        if (id == R.id.retryNone) return 0;
        if (id == R.id.retryOnce) return 1;
        if (id == R.id.retryThree) return 3;
        return 5;
    }

    private int segmentValue() {
        int id = binding.segments.getCheckedButtonId();
        if (id == R.id.segmentOne) return 1;
        if (id == R.id.segmentTwo) return 2;
        if (id == R.id.segmentEight) return 8;
        if (id == R.id.segmentSixteen) return 16;
        return 4;
    }
}

package com.imedia.box.ui.dialog;

import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.databinding.DialogTimerBinding;
import com.imedia.box.utils.Timer;
import com.imedia.box.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TimerDialog extends BaseAlertDialog implements Timer.Callback {

    private final StringBuilder builder = new StringBuilder();
    private final Formatter formatter = new Formatter(builder, Locale.getDefault());
    private DialogTimerBinding binding;

    public static TimerDialog create() {
        return new TimerDialog();
    }

    public void show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof TimerDialog) return;
        }
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogTimerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.play_timer).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        onTick(Timer.get().getTick());
        boolean running = Timer.get().isRunning();
        binding.list.setVisibility(running ? View.GONE : View.VISIBLE);
        binding.timer.setVisibility(running ? View.VISIBLE : View.GONE);
        View focus = running ? binding.delay : binding.time1;
        focus.post(focus::requestFocus);
    }

    @Override
    protected void initEvent() {
        Timer.get().setCallback(this);
        binding.delay.setOnClickListener(view -> Timer.get().delay());
        binding.reset.setOnClickListener(view -> {
            Timer.get().reset();
            dismiss();
        });
        binding.time1.setOnClickListener(this::setTimer);
        binding.time2.setOnClickListener(this::setTimer);
        binding.time3.setOnClickListener(this::setTimer);
        binding.time4.setOnClickListener(this::setTimer);
        binding.customStart.setOnClickListener(this::setCustomTimer);
        binding.customMinutes.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            setCustomTimer(view);
            return true;
        });
    }

    private void setTimer(View view) {
        Timer.get().set(TimeUnit.MINUTES.toMillis(Integer.parseInt(view.getTag().toString())));
        dismiss();
    }

    private void setCustomTimer(View view) {
        String value = binding.customMinutes.getText() == null ? "" : binding.customMinutes.getText().toString().trim();
        int minutes;
        try {
            minutes = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            minutes = 0;
        }
        if (minutes < 1 || minutes > 1440) {
            binding.customInput.setError(getString(R.string.timer_invalid));
            return;
        }
        binding.customInput.setError(null);
        Timer.get().set(TimeUnit.MINUTES.toMillis(minutes));
        dismiss();
    }

    @Override
    public void onTick(long tick) {
        if (binding != null) binding.tick.setText(Util.format(builder, formatter, tick));
    }

    @Override
    public void onFinish() {
        dismiss();
    }

    @Override
    public void onDismiss(android.content.DialogInterface dialog) {
        Timer.get().setCallback(null);
        super.onDismiss(dialog);
    }
}

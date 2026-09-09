package com.imedia.box.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.databinding.DialogTimerBinding;
import com.imedia.box.utils.Timer;
import com.imedia.box.utils.Util;

import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TimerDialog extends BaseBottomSheetDialog implements Timer.Callback {

    private final StringBuilder builder;
    private final Formatter formatter;
    private DialogTimerBinding binding;

    public TimerDialog() {
        builder = new StringBuilder();
        formatter = new Formatter(builder, Locale.getDefault());
    }

    public static TimerDialog create() {
        return new TimerDialog();
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof TimerDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogTimerBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        onTick(Timer.get().getTick());
        binding.list.setVisibility(Timer.get().isRunning() ? View.GONE : View.VISIBLE);
        binding.timer.setVisibility(Timer.get().isRunning() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void initEvent() {
        Timer.get().setCallback(this);
        binding.delay.setOnClickListener(this::onDelay);
        binding.reset.setOnClickListener(this::onReset);
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
        int minutes = Integer.parseInt(view.getTag().toString());
        Timer.get().set(TimeUnit.MINUTES.toMillis(minutes));
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
            binding.customInput.setError(getString(com.imedia.box.R.string.timer_invalid));
            return;
        }
        binding.customInput.setError(null);
        Timer.get().set(TimeUnit.MINUTES.toMillis(minutes));
        dismiss();
    }

    private void onDelay(View view) {
        Timer.get().delay();
    }

    private void onReset(View view) {
        Timer.get().reset();
        dismiss();
    }

    @Override
    public void onTick(long tick) {
        binding.tick.setText(Util.format(builder, formatter, tick));
    }

    @Override
    public void onFinish() {
        dismiss();
    }

    @Override
    public void dismiss() {
        Timer.get().setCallback(null);
        super.dismiss();
    }
}

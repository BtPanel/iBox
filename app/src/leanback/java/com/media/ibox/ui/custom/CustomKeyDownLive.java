package com.media.ibox.ui.custom;

import android.content.Context;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.media.ibox.App;
import com.media.ibox.Constant;
import com.media.ibox.utils.KeyUtil;
import com.media.ibox.utils.ResUtil;

public class CustomKeyDownLive extends GestureDetector.SimpleOnGestureListener {

    private final GestureDetector detector;
    private final StringBuilder text;
    private final Listener listener;
    private long holdTime;
    private boolean menuTriggered;

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            listener.onFind(text.toString());
            text.setLength(0);
        }
    };

    public static CustomKeyDownLive create(Context context) {
        return new CustomKeyDownLive(context);
    }

    private CustomKeyDownLive(Context context) {
        this.text = new StringBuilder();
        this.listener = (Listener) context;
        this.detector = new GestureDetector(context, this);
    }

    public boolean onTouchEvent(MotionEvent e) {
        return detector.onTouchEvent(e);
    }

    public boolean hasEvent(KeyEvent event) {
        return KeyUtil.isEnterKey(event) || KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) || KeyUtil.isDigitKey(event) || KeyUtil.isMenuKey(event) || event.isLongPress();
    }

    public void onKeyDown(KeyEvent event) {
        if (listener.dispatch(true)) check(event);
    }

    private void check(KeyEvent event) {
        if (KeyUtil.isActionDown(event) && KeyUtil.isLeftKey(event)) {
            listener.onSeeking(subTime());
        } else if (KeyUtil.isActionDown(event) && KeyUtil.isRightKey(event)) {
            listener.onSeeking(addTime());
        } else if (KeyUtil.isActionDown(event) && KeyUtil.isUpKey(event)) {
            listener.onKeyUp();
        } else if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event)) {
            listener.onKeyDown();
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isLeftKey(event)) {
            listener.onKeyLeft(holdTime);
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isRightKey(event)) {
            listener.onKeyRight(holdTime);
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isDigitKey(event)) {
            onKeyDown(event.getKeyCode());
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isEnterKey(event)) {
            if (menuTriggered) menuTriggered = false;
            else listener.onKeyCenter();
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isMenuKey(event)) {
            listener.onMenu();
        } else if (event.isLongPress() && KeyUtil.isEnterKey(event)) {
            menuTriggered = true;
            listener.onMenu();
        }
    }

    private void onKeyDown(int keyCode) {
        if (text.length() >= 4) return;
        text.append(getNumber(keyCode));
        listener.onShow(text.toString());
        App.post(runnable, 2000);
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        if (listener.dispatch(false)) listener.onDoubleTap();
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        if (listener.dispatch(false)) listener.onSingleTap(e.getX() < ResUtil.getScreenWidth() / 2f);
        return true;
    }

    @Override
    public void onLongPress(@NonNull MotionEvent e) {
        if (e.getX() >= ResUtil.getScreenWidth() / 2f && listener.dispatch(false)) listener.onMenu();
    }

    private int getNumber(int keyCode) {
        return keyCode >= 144 ? keyCode - 144 : keyCode - 7;
    }

    private long addTime() {
        return holdTime = holdTime + Constant.INTERVAL_SEEK;
    }

    private long subTime() {
        return holdTime = holdTime - Constant.INTERVAL_SEEK;
    }

    public void reset() {
        holdTime = 0;
    }

    public interface Listener {

        boolean dispatch(boolean check);

        void onShow(String number);

        void onFind(String number);

        void onSeeking(long time);

        void onKeyUp();

        void onKeyDown();

        void onKeyLeft(long time);

        void onKeyRight(long time);

        void onKeyCenter();

        void onMenu();

        void onSingleTap(boolean left);

        void onDoubleTap();
    }
}

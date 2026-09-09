package com.imedia.box.utils;

import android.annotation.SuppressLint;
import android.os.Build;
import android.widget.Toast;

import com.imedia.box.App;
import com.imedia.box.setting.Setting;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class SubscriptionToastGuard {

    private static final ThreadLocal<Boolean> APP_TOAST = new ThreadLocal<>();
    private static boolean installed;

    private SubscriptionToastGuard() {
    }

    @SuppressLint("SoonBlockedPrivateApi")
    public static synchronized void install() {
        if (installed) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("Landroid/widget/Toast;");
            }
            Field serviceField = Toast.class.getDeclaredField("sService");
            serviceField.setAccessible(true);
            Object service = serviceField.get(null);
            if (service == null) {
                Method getService = Toast.class.getDeclaredMethod("getService");
                getService.setAccessible(true);
                service = getService.invoke(null);
            }
            if (service == null) return;
            Object delegate = service;
            Object proxy = Proxy.newProxyInstance(
                    SubscriptionToastGuard.class.getClassLoader(),
                    service.getClass().getInterfaces(),
                    (object, method, args) -> invoke(delegate, method, args));
            serviceField.set(null, proxy);
            installed = true;
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void show(Toast toast) {
        Boolean previous = APP_TOAST.get();
        APP_TOAST.set(true);
        try {
            toast.show();
        } finally {
            if (previous == null) APP_TOAST.remove();
            else APP_TOAST.set(previous);
        }
    }

    private static Object invoke(Object delegate, Method method, Object[] args) throws Throwable {
        if (method.getName().startsWith("enqueue") && !Boolean.TRUE.equals(APP_TOAST.get())) {
            CharSequence text = Setting.isConfigNoticeEnabled() ? getText(method, args) : null;
            if (text != null) {
                App.post(() -> Notify.showCompact(text.toString()));
            }
            return defaultValue(method.getReturnType());
        }
        try {
            Object value = method.invoke(delegate, args);
            return value != null ? value : defaultValue(method.getReturnType());
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static CharSequence getText(Method method, Object[] args) {
        if (!method.getName().contains("TextToast") || args == null) return null;
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length && i < args.length; i++) {
            if (types[i] == CharSequence.class && args[i] instanceof CharSequence) return (CharSequence) args[i];
        }
        return null;
    }

    static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == Void.TYPE) return null;
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Character.TYPE) return '\0';
        if (returnType == Byte.TYPE) return (byte) 0;
        if (returnType == Short.TYPE) return (short) 0;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Long.TYPE) return 0L;
        if (returnType == Float.TYPE) return 0F;
        if (returnType == Double.TYPE) return 0D;
        return null;
    }
}

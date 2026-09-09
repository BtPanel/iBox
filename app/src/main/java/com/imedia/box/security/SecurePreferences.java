package com.imedia.box.security;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.github.catvod.utils.Prefers;
import com.imedia.box.App;
import com.imedia.box.BuildConfig;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Set;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class SecurePreferences {

    private static final String STORE = "secure_preferences";
    private static final String KEY_ALIAS = BuildConfig.APPLICATION_ID + ".secure_preferences.v1";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "webdav_password",
            "subtitle_search_token",
            "remote_access_pin",
            "remote_access_token",
            "remote_stream_token");

    private SecurePreferences() {
    }

    public static synchronized String getString(String key) {
        SharedPreferences preferences = preferences();
        if (!preferences.contains(key)) return migrateLegacyValue(key);
        try {
            byte[] payload = Base64.decode(preferences.getString(key, ""), Base64.NO_WRAP);
            return EncryptedValueCodec.decrypt(getOrCreateKey(), key, payload);
        } catch (Exception e) {
            preferences.edit().remove(key).commit();
            return "";
        }
    }

    public static synchronized void putString(String key, String value) {
        if (!isSensitiveKey(key)) throw new IllegalArgumentException("Key is not registered as sensitive");
        if (value == null || value.isEmpty()) {
            preferences().edit().remove(key).commit();
            Prefers.remove(key);
            return;
        }
        try {
            byte[] payload = EncryptedValueCodec.encrypt(getOrCreateKey(), key, value, new SecureRandom());
            String encoded = Base64.encodeToString(payload, Base64.NO_WRAP);
            if (!preferences().edit().putString(key, encoded).commit()) {
                throw new IllegalStateException("Unable to persist encrypted preference");
            }
            Prefers.remove(key);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt preference", e);
        }
    }

    public static boolean isSensitiveKey(String key) {
        return SENSITIVE_KEYS.contains(key);
    }

    private static String migrateLegacyValue(String key) {
        if (!isSensitiveKey(key)) throw new IllegalArgumentException("Key is not registered as sensitive");
        String legacy = Prefers.getString(key, "");
        if (legacy.isEmpty()) {
            Prefers.remove(key);
            return "";
        }
        putString(key, legacy);
        return legacy;
    }

    private static SharedPreferences preferences() {
        return App.get().getSharedPreferences(STORE, 0);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}

package com.imedia.box.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class EncryptedValueCodec {

    private static final byte VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private EncryptedValueCodec() {
    }

    static byte[] encrypt(SecretKey key, String associatedData, String value, SecureRandom random)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, random);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length != IV_LENGTH) throw new GeneralSecurityException("Invalid generated IV");
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(1 + iv.length + ciphertext.length)
                .put(VERSION).put(iv).put(ciphertext).array();
    }

    static String decrypt(SecretKey key, String associatedData, byte[] payload)
            throws GeneralSecurityException {
        if (payload == null || payload.length < 1 + IV_LENGTH + TAG_LENGTH_BITS / 8 || payload[0] != VERSION) {
            throw new GeneralSecurityException("Invalid encrypted value");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.get();
        byte[] iv = new byte[IV_LENGTH];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}

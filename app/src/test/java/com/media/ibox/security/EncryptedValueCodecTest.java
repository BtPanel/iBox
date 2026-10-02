package com.media.ibox.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class EncryptedValueCodecTest {

    @Test
    public void encryptsAndDecryptsWithKeyBoundAssociatedData() throws Exception {
        SecretKey key = key();
        byte[] encrypted = EncryptedValueCodec.encrypt(key, "webdav_password", "secret", new SecureRandom());
        assertFalse(Arrays.equals("secret".getBytes(), encrypted));
        assertEquals("secret", EncryptedValueCodec.decrypt(key, "webdav_password", encrypted));
        assertThrows(GeneralSecurityException.class,
                () -> EncryptedValueCodec.decrypt(key, "subtitle_search_token", encrypted));
    }

    @Test
    public void rejectsTamperedCiphertext() throws Exception {
        SecretKey key = key();
        byte[] encrypted = EncryptedValueCodec.encrypt(key, "remote_access_token", "secret", new SecureRandom());
        encrypted[encrypted.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class,
                () -> EncryptedValueCodec.decrypt(key, "remote_access_token", encrypted));
    }

    @Test
    public void generatesANewIvForEachEncryption() throws Exception {
        SecretKey key = key();
        byte[] first = EncryptedValueCodec.encrypt(key, "remote_access_pin", "123456", new SecureRandom());
        byte[] second = EncryptedValueCodec.encrypt(key, "remote_access_pin", "123456", new SecureRandom());

        assertFalse(Arrays.equals(first, second));
        assertEquals("123456", EncryptedValueCodec.decrypt(key, "remote_access_pin", first));
        assertEquals("123456", EncryptedValueCodec.decrypt(key, "remote_access_pin", second));
    }

    private SecretKey key() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }
}

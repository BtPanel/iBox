package com.imedia.box.api.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

final class JarTrustPolicy {

    private static final String SHA256_MARKER = ";sha256;";
    private static final String MD5_MARKER = ";md5;";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern MD5 = Pattern.compile("[0-9a-fA-F]{32}");

    private final String location;
    private final String algorithm;
    private final String digest;

    private JarTrustPolicy(String location, String algorithm, String digest) {
        this.location = location;
        this.algorithm = algorithm;
        this.digest = digest;
    }

    static JarTrustPolicy parse(String declaration) {
        String value = declaration == null ? "" : declaration.trim();
        int marker = value.lastIndexOf(SHA256_MARKER);
        if (marker >= 0) return create(value, marker, SHA256_MARKER, "SHA-256");
        marker = value.lastIndexOf(MD5_MARKER);
        if (marker >= 0) return create(value, marker, MD5_MARKER, "MD5");
        return new JarTrustPolicy(value, "", "");
    }

    private static JarTrustPolicy create(String value, int marker, String separator, String algorithm) {
        String location = value.substring(0, marker).trim();
        String digest = value.substring(marker + separator.length()).trim().toLowerCase(Locale.ROOT);
        return new JarTrustPolicy(location, algorithm, digest);
    }

    String location() {
        return location;
    }

    boolean trustedRemote() {
        return isHttps() && validDigest();
    }

    boolean allowedLocal() {
        return location.startsWith("file:") || location.startsWith("assets");
    }

    boolean matches(byte[] bytes) {
        if (!validDigest()) return false;
        return MessageDigest.isEqual(hex(digest), hash(algorithm, bytes));
    }

    boolean matches(File file) {
        if (!validDigest() || file == null || !file.isFile()) return false;
        byte[] actual = hash(algorithm, file);
        if (actual == null || !MessageDigest.isEqual(hex(digest), actual)) return false;
        return !"MD5".equals(algorithm) || verifyOrCreateSha256Pin(file);
    }

    private boolean verifyOrCreateSha256Pin(File file) {
        byte[] hash = hash("SHA-256", file);
        if (hash == null) return false;
        String actual = toHex(hash);
        File pin = new File(file.getAbsolutePath() + ".sha256");
        synchronized (JarTrustPolicy.class) {
            if (pin.isFile()) return actual.equals(readPin(pin));
            return writePin(pin, actual);
        }
    }

    private static String readPin(File pin) {
        if (pin.length() > 128) return "";
        try (FileInputStream input = new FileInputStream(pin)) {
            byte[] bytes = new byte[(int) pin.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) return "";
                offset += count;
            }
            return new String(bytes, StandardCharsets.US_ASCII).trim().toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean writePin(File pin, String digest) {
        File temp = new File(pin.getAbsolutePath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(digest.getBytes(StandardCharsets.US_ASCII));
            output.getFD().sync();
        } catch (IOException e) {
            temp.delete();
            return false;
        }
        if (temp.renameTo(pin)) return true;
        temp.delete();
        return pin.isFile() && digest.equals(readPin(pin));
    }

    private boolean validDigest() {
        if ("SHA-256".equals(algorithm)) return SHA256.matcher(digest).matches();
        if ("MD5".equals(algorithm)) return MD5.matcher(digest).matches();
        return false;
    }

    private boolean isHttps() {
        return location.startsWith("https://");
    }

    private static byte[] hash(String algorithm, byte[] bytes) {
        try {
            return MessageDigest.getInstance(algorithm).digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hash(String algorithm, File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) messageDigest.update(buffer, 0, count);
            return messageDigest.digest();
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) builder.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return builder.toString();
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
}

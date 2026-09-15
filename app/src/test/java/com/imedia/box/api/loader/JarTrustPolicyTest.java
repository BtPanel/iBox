package com.imedia.box.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class JarTrustPolicyTest {

    private static final String SHA256 = "3a6eb0790f39ac87c94f3856b2dd2c5d110e6811602261a9a923d3bb23adc8b7";

    @Test
    public void remotePluginSupportsSha256AndLegacyMd5OverHttps() {
        assertFalse(JarTrustPolicy.parse("https://example.com/spider.jar").trustedRemote());
        assertTrue(JarTrustPolicy.parse("https://example.com/spider.jar;md5;8d777f385d3dfec8815d20f7496026dc").trustedRemote());
        assertTrue(JarTrustPolicy.parse("https://example.com/spider.jar;sha256;" + SHA256).trustedRemote());
        assertFalse(JarTrustPolicy.parse("http://example.com/spider.jar;md5;8d777f385d3dfec8815d20f7496026dc").trustedRemote());
        assertFalse(JarTrustPolicy.parse("http://example.com/spider.jar;sha256;" + SHA256).trustedRemote());
    }

    @Test
    public void sha256MustBeExactlySixtyFourHexCharacters() {
        assertFalse(JarTrustPolicy.parse("https://example.com/spider.jar;sha256;abc").trustedRemote());
        assertFalse(JarTrustPolicy.parse("https://example.com/spider.jar;sha256;" + "g".repeat(64)).trustedRemote());
        assertTrue(JarTrustPolicy.parse("https://example.com/spider.jar;sha256;" + SHA256.toUpperCase()).trustedRemote());
    }

    @Test
    public void digestVerificationUsesDownloadedBytes() {
        JarTrustPolicy policy = JarTrustPolicy.parse("https://example.com/spider.jar;sha256;" + SHA256);
        assertTrue(policy.matches("data".getBytes(StandardCharsets.UTF_8)));
        assertFalse(policy.matches("tampered".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void legacyMd5VerificationUsesDownloadedBytes() {
        JarTrustPolicy policy = JarTrustPolicy.parse("https://example.com/spider.jar;md5;8d777f385d3dfec8815d20f7496026dc");
        assertTrue(policy.matches("data".getBytes(StandardCharsets.UTF_8)));
        assertFalse(policy.matches("tampered".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void legacyMd5CreatesAndEnforcesLocalSha256Pin() throws Exception {
        File jar = Files.createTempFile("spider", ".jar").toFile();
        File pin = new File(jar.getAbsolutePath() + ".sha256");
        try {
            Files.writeString(jar.toPath(), "data", StandardCharsets.UTF_8);
            JarTrustPolicy policy = JarTrustPolicy.parse("https://example.com/spider.jar;md5;8d777f385d3dfec8815d20f7496026dc");

            assertTrue(policy.matches(jar));
            assertTrue(pin.isFile());
            assertEquals(SHA256, Files.readString(pin.toPath(), StandardCharsets.UTF_8).trim());

            Files.writeString(pin.toPath(), "0".repeat(64), StandardCharsets.UTF_8);
            assertFalse(policy.matches(jar));
        } finally {
            Files.deleteIfExists(pin.toPath());
            Files.deleteIfExists(jar.toPath());
        }
    }

    @Test
    public void localAndBundledPluginsDoNotRequireRemoteDigest() {
        assertTrue(JarTrustPolicy.parse("file:///sdcard/spider.jar").allowedLocal());
        assertTrue(JarTrustPolicy.parse("assets://spider.jar").allowedLocal());
        assertFalse(JarTrustPolicy.parse("https://example.com/spider.jar").allowedLocal());
    }
}

package com.media.ibox.net;

import static org.junit.Assert.assertEquals;

import com.github.catvod.net.GitHubUrl;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class GitHubUrlTest {

    private static final String RAW = "https://raw.githubusercontent.com/owner/repository/main/config.json";

    @After
    public void restoreDefault() {
        GitHubUrl.setAccelerationEnabled(true);
        GitHubUrl.setCustomAccelerators(Collections.emptyList());
    }

    @Test
    public void withoutCustomAcceleratorsOnlyTheOriginalUrlIsUsed() {
        assertEquals(Collections.singletonList(RAW), GitHubUrl.candidates(RAW));
        assertEquals(RAW, GitHubUrl.accelerateIfNeeded(RAW));
    }

    @Test
    public void preservesAnExistingProxyAsTheFirstCandidate() {
        String proxied = "https://ghfast.top/" + RAW;
        assertEquals(proxied, GitHubUrl.candidates(proxied).get(0));
    }

    @Test
    public void disablesAllAutomaticAcceleration() {
        GitHubUrl.setAccelerationEnabled(false);
        GitHubUrl.setCustomAccelerators("https://my-proxy.example/");
        assertEquals(Collections.singletonList(RAW), GitHubUrl.candidates(RAW));
        assertEquals(RAW, GitHubUrl.accelerateIfNeeded(RAW));
    }

    @Test
    public void customAcceleratorsAreTriedBeforeTheOriginalUrl() {
        GitHubUrl.setCustomAccelerators("https://my-proxy.example/");
        assertEquals(Arrays.asList("https://my-proxy.example/" + RAW, RAW), GitHubUrl.candidates(RAW));
        assertEquals("https://my-proxy.example/" + RAW, GitHubUrl.accelerateIfNeeded(RAW));
    }

    @Test
    public void customAcceleratorsAreNormalizedAndBlankLinesIgnored() {
        assertEquals(Arrays.asList("https://a.example/", "http://b.example/"),
                GitHubUrl.parse("  a.example  \n\n# a comment\nhttp://b.example\n   \n"));
        assertEquals(Collections.emptyList(), GitHubUrl.parse(null));
        assertEquals(Collections.emptyList(), GitHubUrl.parse(""));
        assertEquals(Collections.emptyList(), GitHubUrl.parse("\n  \n"));
    }

    @Test
    public void customAcceleratorsKeepTheirOrderAndDropDuplicates() {
        GitHubUrl.setCustomAccelerators("https://a.example/\nhttps://b.example/\na.example");
        assertEquals(Arrays.asList(
                        "https://a.example/" + RAW,
                        "https://b.example/" + RAW,
                        RAW),
                GitHubUrl.candidates(RAW));
    }

    @Test
    public void customPrefixIsRecognizedAsAlreadyAccelerated() {
        GitHubUrl.setCustomAccelerators("https://my-proxy.example/");
        String proxied = "https://my-proxy.example/" + RAW;
        assertEquals(proxied, GitHubUrl.candidates(proxied).get(0));
        assertEquals(proxied, GitHubUrl.accelerateIfNeeded(proxied));
    }
}

package com.imedia.box.net;

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
    }

    @Test
    public void providesGlobalFallbacksForGitHubResources() {
        assertEquals(Arrays.asList(
                "https://gh-proxy.com/" + RAW,
                "https://ghfast.top/" + RAW,
                RAW,
                "https://cdn.jsdelivr.net/gh/owner/repository@main/config.json"),
                GitHubUrl.candidates(RAW));
    }

    @Test
    public void preservesAnExistingProxyAsTheFirstCandidate() {
        String proxied = "https://ghfast.top/" + RAW;
        assertEquals(proxied, GitHubUrl.candidates(proxied).get(0));
    }

    @Test
    public void disablesAllAutomaticAcceleration() {
        GitHubUrl.setAccelerationEnabled(false);
        assertEquals(Collections.singletonList(RAW), GitHubUrl.candidates(RAW));
        assertEquals(RAW, GitHubUrl.accelerateIfNeeded(RAW));
    }
}

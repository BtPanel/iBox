# Live Playback Stability Phase One Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build typed live playback failures, per-line health ranking, bounded recovery, and unified mobile/TV connection status without changing live source formats or VOD fallback behavior.

**Architecture:** `PlayerManager` emits a typed `PlaybackFailure` through `PlaybackService` and `PlaybackActivity`. Pure live-domain helpers classify failures, score persisted line health, and return recovery decisions; `LivePlaybackController` remains the session coordinator and publishes immutable `LiveConnectionState` values that both Activities render through the existing progress overlay.

**Tech Stack:** Java 17 records, Android View/ViewBinding, Media3 `PlaybackException`, Gson, SharedPreferences through `Prefers`, JUnit 4, Gradle Android product flavors.

**Spec:** `docs/superpowers/specs/2026-09-10-live-playback-stability-design.md`

## Global Constraints

- Do not change live source formats, add settings, or change VOD fallback behavior.
- A recovery cycle may try each line once, plus at most one explicit same-line retry for `NETWORK`, `TIMEOUT`, or `PLAYBACK`.
- `SOURCE` and exhausted `DECODE` failures switch lines without a controller-level same-line retry.
- Manual line selection bypasses health and cooldown for its first attempt and is never left solely because another line scores higher.
- Catch-up requests do not read or write live line health.
- Persist only a SHA-256 URL digest, never the clear playback URL.
- Keep `live_failed_<sourceKey>` for channel-level display only; store health under `live_line_health_v2_<sourceKey>`.
- Render status in the existing progress area with short alpha-only motion; add no dialog or translation animation.
- All delayed work and callbacks must be rejected after channel change, manual line change, refresh, reset, or Activity destruction.

## File Map

**Create:**

- `app/src/main/java/com/media/ibox/player/PlaybackFailure.java` - typed player/service failure contract.
- `app/src/main/java/com/media/ibox/playback/live/LiveFailureKind.java` - live policy failure categories.
- `app/src/main/java/com/media/ibox/playback/live/LiveFailureClassifier.java` - maps typed player failures and resolution failures to live categories.
- `app/src/main/java/com/media/ibox/live/LiveLineKey.java` - privacy-safe source/channel/URL identity.
- `app/src/main/java/com/media/ibox/live/LiveLineHealth.java` - score, cooldown, and rolling startup metrics.
- `app/src/main/java/com/media/ibox/live/LiveLineHealthStore.java` - versioned persistence, pruning, ranking, and production/fake backends.
- `app/src/main/java/com/media/ibox/playback/live/LiveRecoveryDecision.java` - immutable retry/switch/fail result.
- `app/src/main/java/com/media/ibox/playback/live/LiveRecoveryPolicy.java` - pure recovery decision matrix.
- `app/src/main/java/com/media/ibox/playback/live/LiveConnectionState.java` - immutable connection UI state.
- `app/src/main/java/com/media/ibox/playback/live/LivePlaybackScheduler.java` - cancellable `App.post` adapter for deterministic tests.
- `app/src/test/java/com/media/ibox/player/PlaybackFailureTest.java`
- `app/src/test/java/com/media/ibox/playback/live/LiveFailureClassifierTest.java`
- `app/src/test/java/com/media/ibox/live/LiveLineHealthTest.java`
- `app/src/test/java/com/media/ibox/live/LiveLineHealthStoreTest.java`
- `app/src/test/java/com/media/ibox/playback/live/LiveRecoveryPolicyTest.java`

**Modify:**

- `app/src/main/java/com/media/ibox/player/PlayerManager.java` - emit typed failures after internal recovery is exhausted.
- `app/src/main/java/com/media/ibox/service/PlaybackService.java` - forward typed failures while preserving string callback compatibility.
- `app/src/main/java/com/media/ibox/ui/activity/PlaybackActivity.java` - expose typed protected callback and type pre-play validation failures.
- `app/src/main/java/com/media/ibox/live/LiveChannelStore.java` - share the current source key with the health store.
- `app/src/main/java/com/media/ibox/playback/live/LivePlayRequest.java` - add a request ID for strict stale-result rejection.
- `app/src/main/java/com/media/ibox/playback/live/LivePlaybackHost.java` - add connection-state rendering.
- `app/src/main/java/com/media/ibox/playback/live/LivePlaybackController.java` - coordinate health, recovery, cancellation, and status.
- `app/src/test/java/com/media/ibox/playback/live/LivePlaybackControllerTest.java` - expand controller state-machine coverage.
- `app/src/main/res/values/strings.xml` - English connection strings.
- `app/src/main/res/values-zh-rCN/strings.xml` - Simplified Chinese connection strings.
- `app/src/main/res/values-zh-rTW/strings.xml` - Traditional Chinese connection strings.
- `app/src/mobile/java/com/media/ibox/ui/activity/LiveActivity.java` - render state and pass typed errors.
- `app/src/leanback/java/com/media/ibox/ui/activity/LiveActivity.java` - render the same state and pass typed errors.

No layout file changes are planned: both `view_progress.xml` variants already expose `progress.status`.

---

### Task 1: Typed Playback Failure Contract

**Files:**

- Create: `app/src/main/java/com/media/ibox/player/PlaybackFailure.java`
- Modify: `app/src/main/java/com/media/ibox/player/PlayerManager.java`
- Modify: `app/src/main/java/com/media/ibox/service/PlaybackService.java`
- Modify: `app/src/main/java/com/media/ibox/ui/activity/PlaybackActivity.java`
- Test: `app/src/test/java/com/media/ibox/player/PlaybackFailureTest.java`

**Interfaces:**

- Produces: `PlaybackFailure(Kind kind, String message, int errorCode)` with `Kind.SOURCE`, `NETWORK`, `TIMEOUT`, `DECODE`, and `PLAYBACK`.
- Produces: `PlaybackFailure.fromErrorCode(String message, int errorCode)`, `source(String)`, `timeout(String)`, and `decode(String, int)`.
- Produces: `PlaybackService.PlayerCallback.onError(PlaybackFailure failure)`; its default implementation delegates to the existing `onError(String message)`.
- Preserves: existing subclasses overriding `PlaybackActivity.onError(String)` and existing VOD behavior.

- [ ] **Step 1: Write classification and compatibility tests**

Create `PlaybackFailureTest` with named Media3 error-code cases:

```java
@Test
public void classifiesStableMedia3ErrorCodesWithoutReadingMessage() {
    assertEquals(Kind.TIMEOUT, PlaybackFailure.fromErrorCode("任意文案", PlaybackException.ERROR_CODE_TIMEOUT).kind());
    assertEquals(Kind.NETWORK, PlaybackFailure.fromErrorCode("任意文案", PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED).kind());
    assertEquals(Kind.SOURCE, PlaybackFailure.fromErrorCode("任意文案", PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED).kind());
    assertEquals(Kind.DECODE, PlaybackFailure.fromErrorCode("任意文案", PlaybackException.ERROR_CODE_DECODING_FAILED).kind());
    assertEquals(Kind.PLAYBACK, PlaybackFailure.fromErrorCode("任意文案", PlaybackException.ERROR_CODE_UNSPECIFIED).kind());
}

@Test
public void factoriesPreserveMessageAndUnsetCode() {
    PlaybackFailure failure = PlaybackFailure.source("bad url");
    assertEquals(Kind.SOURCE, failure.kind());
    assertEquals("bad url", failure.message());
    assertEquals(PlaybackException.ERROR_CODE_UNSPECIFIED, failure.errorCode());
}
```

- [ ] **Step 2: Run the focused test and confirm the red state**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.media.ibox.player.PlaybackFailureTest" -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: FAIL because `PlaybackFailure` does not exist.

- [ ] **Step 3: Implement the immutable failure type and numeric classifier**

Implement the public contract:

```java
public record PlaybackFailure(Kind kind, String message, int errorCode) {
    public enum Kind { SOURCE, NETWORK, TIMEOUT, DECODE, PLAYBACK }

    public PlaybackFailure {
        kind = kind == null ? Kind.PLAYBACK : kind;
        message = message == null ? "" : message;
    }

    public static PlaybackFailure source(String message) {
        return new PlaybackFailure(Kind.SOURCE, message, PlaybackException.ERROR_CODE_UNSPECIFIED);
    }

    public static PlaybackFailure timeout(String message) {
        return new PlaybackFailure(Kind.TIMEOUT, message, PlaybackException.ERROR_CODE_TIMEOUT);
    }

    public static PlaybackFailure decode(String message, int errorCode) {
        return new PlaybackFailure(Kind.DECODE, message, errorCode);
    }

    public static PlaybackFailure fromErrorCode(String message, int errorCode) {
        Kind kind = switch (errorCode) {
            case PlaybackException.ERROR_CODE_TIMEOUT,
                 PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> Kind.TIMEOUT;
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                 PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                 PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                 PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                 PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                 PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                 PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
                 PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> Kind.NETWORK;
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                 PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                 PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                 PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> Kind.SOURCE;
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                 PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                 PlaybackException.ERROR_CODE_DECODING_FAILED,
                 PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                 PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                 PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
                 PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                 PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                 PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED,
                 PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED -> Kind.DECODE;
            default -> Kind.PLAYBACK;
        };
        return new PlaybackFailure(kind, message, errorCode);
    }
}
```

The `fromErrorCode` switch must classify both `ERROR_CODE_TIMEOUT` and `ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT` as `TIMEOUT`; all other `ERROR_CODE_IO_*` cases as `NETWORK`; manifest/container parsing cases as `SOURCE`; decoding, decoder-init/query, and audio-track init/write cases as `DECODE`; default as `PLAYBACK`.

- [ ] **Step 4: Propagate typed failures through player and service**

Change final failure sites in `PlayerManager`:

```java
callback.onError(PlaybackFailure.decode(engine.getErrorMessage(e), e.errorCode));
callback.onError(PlaybackFailure.timeout(ResUtil.getString(R.string.error_play_timeout)));
callback.onError(PlaybackFailure.source(ResUtil.getString(R.string.error_play_parse)));
callback.onError(PlaybackFailure.fromErrorCode(engine.getErrorMessage(e), e.errorCode));
```

Change `PlayerManager.Callback` and `PlaybackService` to use the typed method. In `PlaybackService.PlayerCallback`, keep both overloads exactly as a compatibility bridge:

```java
default void onError(PlaybackFailure failure) {
    onError(failure.message());
}

default void onError(String message) {
}
```

In `PlaybackActivity`, add `protected void onError(PlaybackFailure failure)` that delegates to `onError(failure.message())`; make the internal service callback invoke the typed overload. Convert `getPlaybackError(Result)` into `getPlaybackFailure(Result)` so pre-play message, empty URL, and unsupported DRM return `PlaybackFailure.source(...)`.

- [ ] **Step 5: Run focused and playback regression tests**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.media.ibox.player.PlaybackFailureTest" -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: both tasks PASS; `VideoActivity` and other string-only subclasses compile unchanged.

- [ ] **Step 6: Commit the typed error contract**

```powershell
git add app/src/main/java/com/media/ibox/player/PlaybackFailure.java app/src/main/java/com/media/ibox/player/PlayerManager.java app/src/main/java/com/media/ibox/service/PlaybackService.java app/src/main/java/com/media/ibox/ui/activity/PlaybackActivity.java app/src/test/java/com/media/ibox/player/PlaybackFailureTest.java
git commit -m "refactor: preserve playback failure types"
```

---

### Task 2: Per-Line Health Model and Versioned Store

**Files:**

- Create: `app/src/main/java/com/media/ibox/playback/live/LiveFailureKind.java`
- Create: `app/src/main/java/com/media/ibox/playback/live/LiveFailureClassifier.java`
- Create: `app/src/main/java/com/media/ibox/live/LiveLineKey.java`
- Create: `app/src/main/java/com/media/ibox/live/LiveLineHealth.java`
- Create: `app/src/main/java/com/media/ibox/live/LiveLineHealthStore.java`
- Modify: `app/src/main/java/com/media/ibox/live/LiveChannelStore.java`
- Test: `app/src/test/java/com/media/ibox/playback/live/LiveFailureClassifierTest.java`
- Test: `app/src/test/java/com/media/ibox/live/LiveLineHealthTest.java`
- Test: `app/src/test/java/com/media/ibox/live/LiveLineHealthStoreTest.java`

**Interfaces:**

- Consumes: `PlaybackFailure.kind()` from Task 1.
- Produces: `LiveFailureClassifier.from(PlaybackFailure)` and `source()`.
- Produces: `LiveLineKey.create(String sourceKey, String group, String channel, String rawUrl)`.
- Produces: `LiveLineHealth.score()`, `isCoolingDown(long)`, `recordFailure(LiveFailureKind, long)`, and `recordSuccess(long startupMs, long nowMs)`.
- Produces: `LiveLineHealthStore.current()`, `rank(Channel, Set<Integer>)`, `recordFailure(Channel, int, LiveFailureKind)`, and `recordSuccess(Channel, int, long)`.
- Produces for tests: package-private `LiveLineHealthStore(String sourceKey, Backend backend, LongSupplier clock)`.

- [ ] **Step 1: Write failure mapping, scoring, cooldown, and privacy tests**

Add focused tests for the exact formulas:

```java
@Test
public void scoreUsesBoundedCountsConsecutiveFailuresAndStartupPenalty() {
    LiveLineHealth health = new LiveLineHealth(5, 5, 2, 0, 0, 12_000, 0);
    assertEquals(40, health.score());
}

@Test
public void sourceCooldownDoublesAndCapsExponent() {
    LiveLineHealth health = LiveLineHealth.empty();
    health = health.recordFailure(LiveFailureKind.SOURCE, 1_000);
    assertEquals(1_000 + TimeUnit.MINUTES.toMillis(30), health.cooldownUntilMs());
    for (int i = 0; i < 5; i++) health = health.recordFailure(LiveFailureKind.SOURCE, 1_000);
    assertEquals(1_000 + TimeUnit.HOURS.toMillis(4), health.cooldownUntilMs());
}

@Test
public void persistedJsonContainsDigestButNotClearUrl() {
    store.recordFailure(channelWith("https://private/live.m3u8?token=secret$主线"), 0, LiveFailureKind.NETWORK);
    String json = backend.value();
    assertFalse(json.contains("private/live"));
    assertFalse(json.contains("secret"));
    assertTrue(json.contains(LiveLineKey.sha256("https://private/live.m3u8?token=secret")));
}
```

Also cover source/group/channel/URL separation, the same source channel reached through Recent/Favorite aliases, unknown version, partially malformed records, 30-day expiry, 500-record pruning, all-cooling fallback, stable source-order ties, count cap 20, and EMA startup calculation.

- [ ] **Step 2: Run the three new test classes and confirm the red state**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.media.ibox.playback.live.LiveFailureClassifierTest" --tests "com.media.ibox.live.LiveLineHealthTest" --tests "com.media.ibox.live.LiveLineHealthStoreTest" -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: FAIL because the live failure and health types do not exist.

- [ ] **Step 3: Implement failure mapping and line identity**

Implement `LiveFailureKind` with the five spec values. Implement one-to-one mapping from `PlaybackFailure.Kind` in `LiveFailureClassifier`; `source()` returns `LiveFailureKind.SOURCE` for resolver failures.

Implement `LiveLineKey` as a record of `sourceKey`, `group`, `channel`, and `urlDigest`. Normalize the URL with `rawUrl.split("\\$", 2)[0]`, encode with UTF-8, hash with `MessageDigest.getInstance("SHA-256")`, and output lowercase two-digit hex per byte. Empty/null text becomes an empty string before hashing.

Expose the existing source identity without changing its algorithm:

```java
static String sourceKey() {
    return Integer.toHexString(Objects.hash(
            LiveConfig.get().getHome().getName(),
            LiveConfig.get().getConfig().getUrl()));
}
```

Keep it package-private so `LiveLineHealthStore` in `com.media.ibox.live` can use it without expanding the public API.

Change `LiveChannelStore.resolve(Channel)` and its `ChannelRef` record from private to package-private. `LiveLineHealthStore` must call this resolver and use the returned canonical source group/channel names instead of `channel.getGroup()` directly. This makes a channel selected through Recent or Favorite resolve to the same health key as the original source group.

- [ ] **Step 4: Implement immutable health calculations**

Use this public shape:

```java
public record LiveLineHealth(
        int successCount,
        int failureCount,
        int consecutiveFailures,
        long lastSuccessMs,
        long lastFailureMs,
        long averageStartupMs,
        long cooldownUntilMs) {

    public static LiveLineHealth empty() {
        return new LiveLineHealth(0, 0, 0, 0, 0, 0, 0);
    }

    public int score() {
        int startupPenalty = averageStartupMs <= 2_000
                ? 0
                : (int) Math.min(20, (averageStartupMs - 2_000) / 1_000);
        return 100
                + Math.min(successCount, 5) * 4
                - Math.min(failureCount, 5) * 4
                - Math.min(consecutiveFailures, 3) * 25
                - startupPenalty;
    }

    public boolean isCoolingDown(long nowMs) { return cooldownUntilMs > nowMs; }
    public long lastActivityMs() { return Math.max(lastSuccessMs, lastFailureMs); }

    public LiveLineHealth recordFailure(LiveFailureKind kind, long nowMs) {
        int nextConsecutive = Math.min(20, consecutiveFailures + 1);
        long baseMs = switch (kind) {
            case SOURCE -> TimeUnit.MINUTES.toMillis(30);
            case NETWORK, TIMEOUT -> TimeUnit.MINUTES.toMillis(2);
            case DECODE -> TimeUnit.MINUTES.toMillis(10);
            case PLAYBACK -> TimeUnit.MINUTES.toMillis(5);
        };
        int exponent = Math.min(nextConsecutive - 1, 3);
        long cooldownMs = Math.min(TimeUnit.HOURS.toMillis(24), baseMs * (1L << exponent));
        return new LiveLineHealth(successCount, Math.min(20, failureCount + 1),
                nextConsecutive, lastSuccessMs, nowMs, averageStartupMs, nowMs + cooldownMs);
    }

    public LiveLineHealth recordSuccess(long startupMs, long nowMs) {
        long sample = Math.max(0, startupMs);
        long average = averageStartupMs == 0
                ? sample
                : Math.round(averageStartupMs * 0.7d + sample * 0.3d);
        return new LiveLineHealth(Math.min(20, successCount + 1), failureCount,
                0, nowMs, lastFailureMs, average, 0);
    }
}
```

For `startupPenalty`, subtract zero through 2,000 ms, then one point per full additional second using `(averageStartupMs - 2_000) / 1_000`, capped at 20. Cooldown bases are 30/2/2/10/5 minutes in enum order; exponent is `min(consecutiveFailures - 1, 3)` and the result is capped at 24 hours.

- [ ] **Step 5: Implement versioned persistence and candidate ranking**

`LiveLineHealthStore` must serialize a versioned envelope:

```java
record Envelope(int version, List<Entry> entries) {}
record Entry(LiveLineKey key, LiveLineHealth health) {}

interface Backend {
    String get(String key);
    void put(String key, String value);
}
```

The production backend delegates to `Prefers.getString` and `Prefers.put`. The preference key is `live_line_health_v2_` plus the supplied source key. Load entries independently so null keys, null health, negative numeric fields, unknown envelope versions, and Gson exceptions are ignored. Before every save, remove records whose `lastActivityMs()` is older than `now - 30 days`, sort oldest first, and remove enough to retain the newest 500.

`rank(channel, excluded)` returns zero-based indices. It excludes attempted indices, filters cooling entries when at least one non-cooling candidate remains, sorts descending by score, and uses the original index ascending as the tie-breaker.

Remove the current `clearFailed(channel)` call from `LiveChannelStore.recordRecent`. Entering READY records recent viewing immediately, but only the controller's 30-second stable-success runnable may clear the channel failure marker.

- [ ] **Step 6: Run health and existing channel-store tests**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.media.ibox.playback.live.LiveFailureClassifierTest" --tests "com.media.ibox.live.LiveLineHealthTest" --tests "com.media.ibox.live.LiveLineHealthStoreTest" -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: PASS with no Android Context dependency in the fake-backend tests.

- [ ] **Step 7: Commit the health subsystem**

```powershell
git add app/src/main/java/com/media/ibox/playback/live/LiveFailureKind.java app/src/main/java/com/media/ibox/playback/live/LiveFailureClassifier.java app/src/main/java/com/media/ibox/live/LiveLineKey.java app/src/main/java/com/media/ibox/live/LiveLineHealth.java app/src/main/java/com/media/ibox/live/LiveLineHealthStore.java app/src/main/java/com/media/ibox/live/LiveChannelStore.java app/src/test/java/com/media/ibox/playback/live/LiveFailureClassifierTest.java app/src/test/java/com/media/ibox/live/LiveLineHealthTest.java app/src/test/java/com/media/ibox/live/LiveLineHealthStoreTest.java
git commit -m "feat: track live line health"
```

---

### Task 3: Pure Recovery Policy

**Files:**

- Create: `app/src/main/java/com/media/ibox/playback/live/LiveRecoveryDecision.java`
- Create: `app/src/main/java/com/media/ibox/playback/live/LiveRecoveryPolicy.java`
- Test: `app/src/test/java/com/media/ibox/playback/live/LiveRecoveryPolicyTest.java`

**Interfaces:**

- Consumes: `LiveFailureKind` and already health-ranked zero-based candidate indices.
- Produces: `LiveRecoveryDecision.retryCurrent(int lineIndex, long delayMs)`, `switchLine(int targetIndex)`, and `fail()`.
- Produces: `LiveRecoveryPolicy.decide(LiveFailureKind failure, int currentLine, boolean retriedCurrent, List<Integer> candidates, boolean reconnectEnabled, boolean changeLineEnabled, long reconnectDelayMs)`.

- [ ] **Step 1: Write the full recovery decision matrix**

Tests must explicitly assert:

```java
@Test
public void networkRetriesOnceThenSwitches() {
    assertEquals(Action.RETRY_CURRENT, decide(NETWORK, false, List.of(1)).action());
    assertEquals(1, decide(NETWORK, true, List.of(1)).lineIndex());
    assertEquals(Action.SWITCH_LINE, decide(NETWORK, true, List.of(1)).action());
}

@Test
public void sourceAndDecodeSkipSameLineRetry() {
    assertEquals(Action.SWITCH_LINE, decide(SOURCE, false, List.of(1)).action());
    assertEquals(Action.SWITCH_LINE, decide(DECODE, false, List.of(1)).action());
}

@Test
public void disabledSettingsEndRecoveryAtTheirBoundary() {
    assertEquals(Action.SWITCH_LINE, decide(NETWORK, false, List.of(1), false, true).action());
    assertEquals(Action.FAIL, decide(NETWORK, true, List.of(1), true, false).action());
    assertEquals(Action.FAIL, decide(SOURCE, false, List.of(), true, true).action());
}
```

Also cover `TIMEOUT` and `PLAYBACK`, empty candidates, negative delay normalized to zero, and the first ranked candidate being selected without reordering.

- [ ] **Step 2: Run the focused test and confirm the red state**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.media.ibox.playback.live.LiveRecoveryPolicyTest" -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: FAIL because policy types do not exist.

- [ ] **Step 3: Implement the decision record and policy**

Use exact shapes:

```java
public record LiveRecoveryDecision(Action action, int lineIndex, long delayMs) {
    public enum Action { RETRY_CURRENT, SWITCH_LINE, FAIL }
    public static LiveRecoveryDecision retryCurrent(int lineIndex, long delayMs) {
        return new LiveRecoveryDecision(Action.RETRY_CURRENT, lineIndex, Math.max(0, delayMs));
    }
    public static LiveRecoveryDecision switchLine(int targetIndex) {
        return new LiveRecoveryDecision(Action.SWITCH_LINE, targetIndex, 0);
    }
    public static LiveRecoveryDecision fail() {
        return new LiveRecoveryDecision(Action.FAIL, -1, 0);
    }
}
```

The policy first checks whether the failure permits one same-line retry and reconnect is enabled. If not, it switches to `candidates.get(0)` only when line change is enabled and a candidate exists; otherwise it fails. It must not know about `Channel`, `App`, `Prefers`, or Android Views.

```java
public LiveRecoveryDecision decide(
        LiveFailureKind failure,
        int currentLine,
        boolean retriedCurrent,
        List<Integer> candidates,
        boolean reconnectEnabled,
        boolean changeLineEnabled,
        long reconnectDelayMs) {
    boolean retryable = failure == LiveFailureKind.NETWORK
            || failure == LiveFailureKind.TIMEOUT
            || failure == LiveFailureKind.PLAYBACK;
    if (retryable && reconnectEnabled && !retriedCurrent) {
        return LiveRecoveryDecision.retryCurrent(currentLine, reconnectDelayMs);
    }
    if (changeLineEnabled && candidates != null && !candidates.isEmpty()) {
        return LiveRecoveryDecision.switchLine(candidates.get(0));
    }
    return LiveRecoveryDecision.fail();
}
```

- [ ] **Step 4: Run the policy and health tests**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.media.ibox.playback.live.LiveRecoveryPolicyTest" --tests "com.media.ibox.live.LiveLineHealth*" -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: PASS.

- [ ] **Step 5: Commit the recovery policy**

```powershell
git add app/src/main/java/com/media/ibox/playback/live/LiveRecoveryDecision.java app/src/main/java/com/media/ibox/playback/live/LiveRecoveryPolicy.java app/src/test/java/com/media/ibox/playback/live/LiveRecoveryPolicyTest.java
git commit -m "feat: add bounded live recovery policy"
```

---

### Task 4: Controller Recovery State Machine

**Files:**

- Create: `app/src/main/java/com/media/ibox/playback/live/LiveConnectionState.java`
- Create: `app/src/main/java/com/media/ibox/playback/live/LivePlaybackScheduler.java`
- Modify: `app/src/main/java/com/media/ibox/playback/live/LivePlayRequest.java`
- Modify: `app/src/main/java/com/media/ibox/playback/live/LivePlaybackHost.java`
- Modify: `app/src/main/java/com/media/ibox/playback/live/LivePlaybackController.java`
- Modify: `app/src/test/java/com/media/ibox/playback/live/LivePlaybackControllerTest.java`

**Interfaces:**

- Consumes: Tasks 1-3 failure, health, ranking, and policy APIs.
- Produces: `LiveConnectionState(Type type, int fromLine, int line, int total, int attempt, int delaySeconds, String message)` with factory methods for all five states.
- Produces: `LivePlaybackHost.renderConnectionState(LiveConnectionState connectionState)`.
- Produces: `LivePlaybackController.playbackError(PlaybackFailure failure)` while retaining `playbackError(String message)` as a `SOURCE` compatibility wrapper for resolver results.
- Produces: request identity through `LivePlayRequest.getRequestId()` and exact request matching.

- [ ] **Step 1: Replace the two existing controller tests with a deterministic fake host harness and add failing recovery tests**

The fake harness must provide:

```java
final class FakeScheduler implements LivePlaybackScheduler {
    final List<Runnable> pending = new ArrayList<>();
    @Override public void post(Runnable runnable, long delayMs) { pending.add(runnable); }
    @Override public void remove(Runnable runnable) { pending.remove(runnable); }
    void runNext() { pending.remove(0).run(); }
}

final class FakeHost implements LivePlaybackHost {
    final List<LiveConnectionState> states = new ArrayList<>();
    final List<LivePlayRequest> requests = new ArrayList<>();
    @Override public void renderConnectionState(LiveConnectionState state) { states.add(state); }
    @Override public void requestUrl(LivePlayRequest request) { requests.add(request); }
    // Return fixed groups, service-ready true, no active player session, UTC zone.
}
```

Add tests for initial best-line choice, manual cooldown override, one retry then best-line switch, no revisits, final channel mark, 30-second stable success, READY-shorter-than-30-seconds failure, refresh cancellation, channel/line/reset cancellation, stale result rejection, and catch-up isolation. Preserve the two existing catch-up assertions.

- [ ] **Step 2: Run the controller test and confirm the red state**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.media.ibox.playback.live.LivePlaybackControllerTest" -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: FAIL because connection state, injectable scheduler, and typed recovery are not implemented.

- [ ] **Step 3: Add request IDs and immutable connection states**

Extend request factories without breaking current tests:

```java
private LivePlayRequest(Channel channel, EpgData data, long position, long requestId) {
    this.channel = channel;
    this.data = data;
    this.group = channel.getGroup() == null ? "" : channel.getGroup().getName();
    this.position = position;
    this.line = channel.getIndex();
    this.requestId = requestId;
}

public static LivePlayRequest live(Channel channel, long position) {
    return live(channel, position, 0);
}

public static LivePlayRequest live(Channel channel, long position, long requestId) {
    return new LivePlayRequest(channel, null, position, requestId);
}

public static LivePlayRequest catchup(Channel channel, EpgData data, long position) {
    return catchup(channel, data, position, 0);
}

public static LivePlayRequest catchup(Channel channel, EpgData data, long position, long requestId) {
    return new LivePlayRequest(channel, data, position, requestId);
}

public long getRequestId() { return requestId; }
```

`matches(LivePlayRequest)` must include `requestId`; channel matching remains based on captured group/channel/line. Create `LiveConnectionState`, keeping line indices zero-based until rendering:

```java
public record LiveConnectionState(
        Type type,
        int fromLine,
        int line,
        int total,
        int attempt,
        int delaySeconds,
        String message) {
    public enum Type { CONNECTING, RECONNECTING, SWITCHING, PLAYING, FAILED }

    public LiveConnectionState {
        message = message == null ? "" : message;
    }

    public static LiveConnectionState connecting(int line, int total) {
        return new LiveConnectionState(Type.CONNECTING, -1, line, total, 0, 0, "");
    }

    public static LiveConnectionState reconnecting(int line, int total, int delaySeconds) {
        return new LiveConnectionState(Type.RECONNECTING, -1, line, total, 1,
                Math.max(0, delaySeconds), "");
    }

    public static LiveConnectionState switching(int fromLine, int line, int total) {
        return new LiveConnectionState(Type.SWITCHING, fromLine, line, total, 0, 0, "");
    }

    public static LiveConnectionState playing(int line, int total) {
        return new LiveConnectionState(Type.PLAYING, -1, line, total, 0, 0, "");
    }

    public static LiveConnectionState failed(String message) {
        return new LiveConnectionState(Type.FAILED, -1, -1, 0, 0, 0, message);
    }
}
```

- [ ] **Step 4: Add the production scheduler and injectable controller constructor**

Implement:

```java
public interface LivePlaybackScheduler {
    void post(Runnable runnable, long delayMs);
    void remove(Runnable runnable);

    static LivePlaybackScheduler app() {
        return new LivePlaybackScheduler() {
            public void post(Runnable runnable, long delayMs) { App.post(runnable, delayMs); }
            public void remove(Runnable runnable) { App.removeCallbacks(runnable); }
        };
    }
}
```

Keep the public two-argument controller constructor. Add a package-private constructor accepting `LiveLineHealthStore`, `LiveRecoveryPolicy`, `LivePlaybackScheduler`, and `LongSupplier clock` so unit tests own time and queued work.

- [ ] **Step 5: Implement recovery-cycle state in the controller**

Replace `MAX_RECONNECT_ATTEMPTS`, `reconnectAttempts`, and static `App.post` use with:

```java
private final Set<Integer> attemptedLines = new LinkedHashSet<>();
private long requestSequence;
private long recoveryGeneration;
private long connectStartedAtMs;
private boolean retriedCurrent;
private Runnable pendingRecovery;
private Runnable stablePlayback;
```

Create helpers with these exact signatures: `beginCycle(boolean manual, boolean chooseHealthyLine)`, `cancelPendingRecovery()`, `requestLiveInCycle()`, `handleLiveFailure(PlaybackFailure failure, LivePlayRequest request)`, `applyDecision(LiveRecoveryDecision decision, PlaybackFailure failure, LivePlayRequest request)`, and `isCurrent(long generation, long requestId)`. `selectChannel` chooses the highest-ranked healthy line; `selectLine` starts a manual cycle on the requested index; `refresh` starts a cycle on the current index. Every live request increments `requestSequence`, publishes `CONNECTING`, and stores `connectStartedAtMs`.

On failure, classify before clearing the pending request. A permitted retry publishes `RECONNECTING`, schedules one generation-bound runnable, and does not write cooldown yet. A switch writes one line failure, adds the old index to `attemptedLines`, updates `Channel.index`, publishes `SWITCHING`, renders the selected line, and requests the next line. `FAIL` writes the current failure, marks the channel failed only for non-catch-up playback, and publishes `FAILED` before calling `resetPlaybackForError`.

- [ ] **Step 6: Implement stable success and stale-work rejection**

`onPlaybackReady()` must publish `PLAYING` immediately. For live requests, schedule a generation/request-bound 30-second runnable that calls `recordSuccess(channel, capturedLine, max(0, readyTime - connectStartedAtMs))`, clears `LiveChannelStore`'s channel failure flag, refreshes channel status, clears the attempted set, and resets retry state. Catch-up publishes `PLAYING` but schedules no health write.

`selectChannel`, `selectLine`, `refresh`, `reset`, and a new playback request must remove both pending recovery and stable-success runnables. `onPlaybackResult` must require the exact request ID as well as channel and captured line. A delayed runnable rechecks its captured generation before mutating state.

- [ ] **Step 7: Run controller and all new policy tests**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.media.ibox.playback.live.*" --tests "com.media.ibox.live.LiveLineHealth*" -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: PASS; fake scheduler has no pending tasks after reset tests.

- [ ] **Step 8: Commit the controller state machine**

```powershell
git add app/src/main/java/com/media/ibox/playback/live/LiveConnectionState.java app/src/main/java/com/media/ibox/playback/live/LivePlaybackScheduler.java app/src/main/java/com/media/ibox/playback/live/LivePlayRequest.java app/src/main/java/com/media/ibox/playback/live/LivePlaybackHost.java app/src/main/java/com/media/ibox/playback/live/LivePlaybackController.java app/src/test/java/com/media/ibox/playback/live/LivePlaybackControllerTest.java
git commit -m "feat: recover live playback with healthy lines"
```

---

### Task 5: Unified Mobile and TV Connection Status

**Files:**

- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/mobile/java/com/media/ibox/ui/activity/LiveActivity.java`
- Modify: `app/src/leanback/java/com/media/ibox/ui/activity/LiveActivity.java`

**Interfaces:**

- Consumes: `PlaybackActivity.onError(PlaybackFailure)` and `LiveConnectionState` from Tasks 1 and 4.
- Implements: `LivePlaybackHost.renderConnectionState(LiveConnectionState state)` identically on mobile and TV.
- Uses: existing `mBinding.progress.status`; no layout or dialog additions.

- [ ] **Step 1: Add localized status resources**

Add these resource names to all three string files:

```xml
<string name="live_connecting">Connecting to line %1$d/%2$d</string>
<string name="live_reconnecting">Playback interrupted. Reconnecting in %1$d seconds</string>
<string name="live_switching_line">Line %1$d unavailable. Switching to line %2$d/%3$d</string>
```

Simplified Chinese values:

```xml
<string name="live_connecting">正在连接线路 %1$d/%2$d</string>
<string name="live_reconnecting">播放中断，%1$d 秒后重连</string>
<string name="live_switching_line">线路 %1$d 不可用，正在切换到线路 %2$d/%3$d</string>
```

Traditional Chinese values use `正在連接線路`、`播放中斷，%1$d 秒後重新連線`、`線路 %1$d 無法使用，正在切換到線路 %2$d/%3$d`.

- [ ] **Step 2: Implement one renderer shape in both Activities**

Override typed player failure handling:

```java
@Override
protected void onError(PlaybackFailure failure) {
    mLive.playbackError(failure);
}
```

Implement `renderConnectionState` with the same switch on both variants. `CONNECTING`, `RECONNECTING`, and `SWITCHING` call `showProgress()`, set `progress.status` text, and show it. `PLAYING` clears/hides `progress.status` and calls `hideProgress()`. `FAILED` clears/hides the status; the controller invokes the existing final-error UI.

Only animate `progress.status` when it changes from `GONE` to `VISIBLE`: cancel a previous property animation, set alpha to zero, show it, then animate alpha to one using `AppAccessibility.duration(getResources().getInteger(R.integer.ibox_motion_short))`. Text changes while visible update in place with no animation. Hiding cancels animation, sets `GONE`, and restores alpha to one. Do not translate or scale the view.

- [ ] **Step 3: Fix READY/reset integration on both variants**

The TV Activity already calls `mLive.onPlaybackReady()`; add the same call to the mobile `Player.STATE_READY` branch before recent-channel persistence. Ensure both `onDestroy()` paths call `mLive.reset()` before `super.onDestroy()` so scheduled recovery and stable-success tasks are removed. Keep current traffic, controls, recent list, PIP, and TV focus behavior intact.

- [ ] **Step 4: Compile both primary variants and inspect resource errors**

Run:

```powershell
.\gradlew.bat :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebug -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: both APKs build; ViewBinding resolves `progress.status` on mobile and TV.

- [ ] **Step 5: Run mobile and TV lint**

Run:

```powershell
.\gradlew.bat :app:lintMobileArm64_v8aDebug :app:lintLeanbackArm64_v8aDebug -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: BUILD SUCCESSFUL with no new accessibility, translation, or resource-format errors.

- [ ] **Step 6: Commit dual-platform status rendering**

```powershell
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/values-zh-rTW/strings.xml app/src/mobile/java/com/media/ibox/ui/activity/LiveActivity.java app/src/leanback/java/com/media/ibox/ui/activity/LiveActivity.java
git commit -m "feat: show live recovery status"
```

---

### Task 6: Full Regression and Device Acceptance

**Files:**

- Modify only files required to fix failures caused by Tasks 1-5.
- Do not add generated APKs, reports, logs, screenshots, or local configuration to Git.

**Interfaces:**

- Verifies all phase-one interfaces and the existing four Android variants.
- Produces a clean, reviewable branch with passing automated checks and a recorded manual test result.

- [ ] **Step 1: Run all four variant unit-test tasks**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest :app:testMobileArmeabi_v7aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest :app:testLeanbackArmeabi_v7aDebugUnitTest -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: BUILD SUCCESSFUL and all existing plus new tests pass.

- [ ] **Step 2: Build all four Debug APKs**

Run:

```powershell
.\gradlew.bat :app:assembleMobileArm64_v8aDebug :app:assembleMobileArmeabi_v7aDebug :app:assembleLeanbackArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
```

Expected: BUILD SUCCESSFUL with four Debug APKs under `app/build/outputs/apk`.

- [ ] **Step 3: Re-run dual-platform lint and static checks**

Run:

```powershell
.\gradlew.bat :app:lintMobileArm64_v8aDebug :app:lintLeanbackArm64_v8aDebug -PchaquopyBuildPython="C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe"
git diff --check
git status --short
```

Expected: Gradle succeeds, `git diff --check` prints nothing, and status contains only intentional source/test changes not yet committed.

- [ ] **Step 4: Run the live recovery device matrix**

On one mobile device/emulator and one Android TV target, use a temporary two-line source containing a deliberately empty/broken first line and a working second line. Verify each exact scenario:

1. Empty/parse failure switches immediately without a same-line retry.
2. Timeout/network failure retries the same line once and then switches.
3. Manually choosing a cooled line attempts it and does not switch until a real failure.
4. Switching channel during a reconnect delay prevents the old line from reopening.
5. Disabling auto reconnect skips same-line retry; disabling auto line change ends on the current line.
6. Re-entering the channel prefers the stable, faster line.
7. Catch-up playback does not change live line ordering.
8. Status text is legible in day/night themes, mobile portrait/landscape, and TV; it only fades and never jumps or flashes the error panel before recovery finishes.
9. TV focus stays on the existing channel/control target during automatic recovery.

Capture private logs only outside Git, redact source URLs/tokens, and inspect for `FATAL EXCEPTION`, ANR, repeated request loops, and stale callbacks.

- [ ] **Step 5: Commit any verification-only fixes, then verify a clean tree**

For a required fix, stage only its source and test files and use:

```powershell
git commit -m "fix: harden live recovery edge cases"
```

Then run:

```powershell
git status --short --branch
git log -6 --oneline
```

Expected: clean working tree and the phase-one commits visible above the design/plan commits.

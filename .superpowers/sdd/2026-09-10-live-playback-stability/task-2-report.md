# Task 2 Report: Per-Line Health Model and Versioned Store

## Implementation Summary

- Added `LiveFailureKind` and `LiveFailureClassifier` to translate the typed `PlaybackFailure` contract into live-specific failure kinds, including resolver failures as `SOURCE`.
- Added `LiveLineKey`, which normalizes a raw line URL before its first `$` and persists only a lowercase SHA-256 digest together with source, canonical group, and channel identity.
- Added immutable `LiveLineHealth` score, cooldown, count cap, and startup EMA calculations using the specified constants.
- Added `LiveLineHealthStore` with a version-2 envelope, `Prefers` backend, defensive per-entry loading, 30-day expiry, 500-entry newest-record retention, and deterministic line ranking.
- Exposed the existing `LiveChannelStore` resolver and source key at package scope so source/recent/favorite aliases resolve to the same health key. `recordRecent` no longer clears the channel failure marker.

## TDD Evidence

### RED

Command:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -classpath 'gradle\wrapper\gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain :app:testMobileArm64_v8aDebugUnitTest --tests 'com.imedia.box.playback.live.LiveFailureClassifierTest' --tests 'com.imedia.box.live.LiveLineHealthTest' --tests 'com.imedia.box.live.LiveLineHealthStoreTest' -PchaquopyBuildPython='C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe'
```

Result: failed during `:app:compileMobileArm64_v8aDebugUnitTestJavaWithJavac` with 39 expected missing-symbol errors for `LiveFailureKind`, `LiveFailureClassifier`, `LiveLineKey`, `LiveLineHealth`, and `LiveLineHealthStore`. No production implementation for the requested behavior existed.

### GREEN

Command:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -classpath 'gradle\wrapper\gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain :app:testMobileArm64_v8aDebugUnitTest --tests 'com.imedia.box.playback.live.LiveFailureClassifierTest' --tests 'com.imedia.box.live.LiveLineHealthTest' --tests 'com.imedia.box.live.LiveLineHealthStoreTest' -PchaquopyBuildPython='C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe'
```

Result: `BUILD SUCCESSFUL`; 11 focused tests passed.

### Regression

Command:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -classpath 'gradle\wrapper\gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain :app:testMobileArm64_v8aDebugUnitTest --tests 'com.imedia.box.playback.live.LivePlaybackControllerTest' --tests 'com.imedia.box.playback.live.LiveFailureClassifierTest' --tests 'com.imedia.box.live.LiveLineHealthTest' --tests 'com.imedia.box.live.LiveLineHealthStoreTest' -PchaquopyBuildPython='C:/Users/Admin/AppData/Local/Programs/Python/Python310/python.exe'
```

Result: `BUILD SUCCESSFUL`.

## Files Changed

- `app/src/main/java/com/imedia/box/playback/live/LiveFailureKind.java`
- `app/src/main/java/com/imedia/box/playback/live/LiveFailureClassifier.java`
- `app/src/main/java/com/imedia/box/live/LiveLineKey.java`
- `app/src/main/java/com/imedia/box/live/LiveLineHealth.java`
- `app/src/main/java/com/imedia/box/live/LiveLineHealthStore.java`
- `app/src/main/java/com/imedia/box/live/LiveChannelStore.java`
- `app/src/test/java/com/imedia/box/playback/live/LiveFailureClassifierTest.java`
- `app/src/test/java/com/imedia/box/live/LiveLineHealthTest.java`
- `app/src/test/java/com/imedia/box/live/LiveLineHealthStoreTest.java`

## Self-Review

- Confirmed the source-key algorithm was not changed.
- Confirmed resolver-derived source group and channel names are used by the health store.
- Confirmed persisted JSON contains no clear URL and uses the normalized URL digest.
- Confirmed unknown versions, malformed entries, null keys/health, and negative metrics are ignored without discarding other valid records.
- Confirmed expiry is applied before every save and ranking preserves source order on score ties, including when every candidate is cooling down.
- Ran `git diff --check`; it reported no whitespace errors.

## Concerns

- The Gradle runtime emits its pre-existing restricted-native-access and deprecated-Gradle-feature warnings. The targeted and regression test tasks still completed successfully.
- The pure unit test for canonical aliases uses a narrowly scoped reflection fixture to set the existing `LiveConfig` singleton's private home value, avoiding Android-context access. Production code does not use reflection.

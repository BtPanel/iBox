package com.imedia.box.player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PlaybackDiagnostics(
        String title,
        String url,
        Map<String, String> headers,
        Map<String, String> values,
        List<String> events) {

    public PlaybackDiagnostics {
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        events = List.copyOf(events);
    }
}

package com.imedia.box.search;

import com.imedia.box.R;
import com.imedia.box.bean.Vod;
import com.imedia.box.impl.Diffable;
import com.imedia.box.utils.ResUtil;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchResultGroup implements Diffable<SearchResultGroup> {

    private static final Pattern QUALITY = Pattern.compile("(?i)(8K|4K|2160P|1080P|720P|UHD|FHD|HD|藍光|蓝光|超清|高清|標清|标清|杜比|原盤|原盘)");
    private static final Pattern NON_TITLE = Pattern.compile("[^\\p{L}\\p{N}]");

    private final String key;
    private final List<Vod> variants;
    private final Vod primary;

    private SearchResultGroup(String key, List<Vod> variants) {
        this.key = key;
        this.variants = List.copyOf(variants);
        this.primary = variants.stream().filter(item -> !item.getPic().isEmpty()).findFirst().orElse(variants.get(0));
    }

    public static List<SearchResultGroup> aggregate(List<Vod> items) {
        Map<String, List<Vod>> groups = new LinkedHashMap<>();
        for (Vod item : items) {
            String year = SearchFilter.year(item);
            String title = normalize(item.getName());
            String key = !title.isEmpty() && !year.isEmpty()
                    ? title + '\u001f' + year
                    : item.getSiteKey() + '\u001f' + item.getId();
            List<Vod> variants = groups.computeIfAbsent(key, ignored -> new ArrayList<>());
            boolean exists = false;
            for (Vod value : variants) {
                if (value.getSiteKey().equals(item.getSiteKey()) && value.getId().equals(item.getId())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) variants.add(item);
        }
        List<SearchResultGroup> result = new ArrayList<>(groups.size());
        for (Map.Entry<String, List<Vod>> entry : groups.entrySet()) {
            result.add(new SearchResultGroup(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public String key() {
        return key;
    }

    public Vod primary() {
        return primary;
    }

    public List<Vod> variants() {
        return variants;
    }

    public String name() {
        return primary.getName();
    }

    public String pic() {
        return primary.getPic();
    }

    public String year() {
        return SearchFilter.year(primary);
    }

    public String sourceSummary() {
        Set<String> sources = new LinkedHashSet<>();
        for (Vod item : variants) sources.add(item.getSiteKey());
        if (sources.size() == 1) return primary.getSiteName();
        return ResUtil.getString(R.string.search_source_count, sources.size());
    }

    public String qualitySummary() {
        Set<String> values = new LinkedHashSet<>();
        for (Vod item : variants) {
            String quality = quality(item);
            if (!quality.isEmpty()) values.add(quality);
        }
        return String.join(" / ", values);
    }

    public String choiceLabel(Vod item) {
        String detail = quality(item);
        if (detail.isEmpty()) detail = item.getRemarks();
        if (detail.isEmpty()) return item.getSiteName();
        return item.getSiteName() + " · " + detail;
    }

    private static String quality(Vod item) {
        Matcher matcher = QUALITY.matcher(item.getRemarks());
        if (!matcher.find()) return "";
        return matcher.group().toUpperCase(Locale.ROOT);
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return NON_TITLE.matcher(normalized).replaceAll("");
    }

    @Override
    public boolean isSameItem(SearchResultGroup other) {
        return other != null && key.equals(other.key);
    }

    @Override
    public boolean isSameContent(SearchResultGroup other) {
        return other != null && variants.equals(other.variants) && sourceSummary().equals(other.sourceSummary()) && qualitySummary().equals(other.qualitySummary());
    }
}

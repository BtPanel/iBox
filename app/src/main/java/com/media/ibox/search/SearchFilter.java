package com.media.ibox.search;

import com.media.ibox.R;
import com.media.ibox.bean.Vod;
import com.media.ibox.utils.ResUtil;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchFilter {

    static final String TYPE_MOVIE = "@movie";
    static final String TYPE_SERIES = "@series";
    static final String TYPE_VARIETY = "@variety";
    static final String TYPE_ANIME = "@anime";
    static final String TYPE_DOCUMENTARY = "@documentary";
    static final String TYPE_SHORT_DRAMA = "@short_drama";
    static final String TYPE_OTHER = "@other";

    private static final Pattern YEAR_PATTERN = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern TYPE_SEPARATOR = Pattern.compile("[\\s,，/|·]+");
    private static final Pattern DOCUMENTARY = pattern("\\u7eaa\\u5f55|\\u7d00\\u9304|\\u7eaa\\u5b9e|\\u7d00\\u5be6|documentary");
    private static final Pattern ANIME = pattern("\\u52a8\\u6f2b|\\u52d5\\u6f2b|\\u52a8\\u753b|\\u52d5\\u756b|\\u5361\\u901a|\\u756a\\u5267|\\u56fd\\u6f2b|\\u570b\\u6f2b|\\u5c11\\u513f|\\u5c11\\u5152|anime");
    private static final Pattern VARIETY = pattern("\\u7efc\\u827a|\\u7d9c\\u85dd|\\u771f\\u4eba\\u79c0|\\u8131\\u53e3\\u79c0|\\u812b\\u53e3\\u79c0|\\u665a\\u4f1a|\\u665a\\u6703|\\u6f14\\u5531\\u4f1a|\\u6f14\\u5531\\u6703|variety");
    private static final Pattern SHORT_DRAMA = pattern("\\u77ed\\u5267|\\u77ed\\u5287|\\u5fae\\u5267|\\u5fae\\u5287|\\u7ad6\\u5c4f|\\u8c4e\\u5c4f");
    private static final Pattern SERIES = pattern("\\u7535\\u89c6\\u5267|\\u96fb\\u8996\\u5287|\\u8fde\\u7eed\\u5267|\\u9023\\u7e8c\\u5287|\\u5267\\u96c6|\\u7f8e\\u5267|\\u97e9\\u5267|\\u97d3\\u5287|\\u65e5\\u5267|\\u6cf0\\u5267|\\u6e2f\\u5267|\\u53f0\\u5267|\\u53f0\\u5287|\\u82f1\\u5267|\\u9646\\u5267|\\u9678\\u5287|\\u7b2c.{0,4}\\u5b63|season|\\u5168\\d+\\u96c6|\\u66f4\\u65b0\\u81f3?\\d+\\u96c6|\\u7b2c\\d+\\u96c6|\\u5b8c\\u7ed3|\\u5b8c\\u7d50");
    private static final Pattern MOVIE = pattern("\\u7535\\u5f71|\\u96fb\\u5f71|\\u5f71\\u7247|\\u5f71\\u7248|\\u6b63\\u7247|movie|\\u7247(?:\\s|$)");
    private static final List<String> BUILT_IN_TYPES = List.of(TYPE_MOVIE, TYPE_SERIES, TYPE_VARIETY, TYPE_ANIME, TYPE_DOCUMENTARY, TYPE_SHORT_DRAMA, TYPE_OTHER);

    private SearchFilter() {
    }

    public static List<Vod> apply(List<Vod> items, String type, String year) {
        String selectedType = value(type);
        String selectedYear = value(year);
        return items.stream()
                .filter(item -> selectedType.isEmpty() || typeValues(item).contains(selectedType))
                .filter(item -> selectedYear.isEmpty() || selectedYear.equals(year(item)))
                .toList();
    }

    public static List<String> types(List<Vod> items) {
        Set<String> values = new LinkedHashSet<>();
        for (Vod item : items) values.addAll(typeValues(item));
        return values.stream().sorted(Comparator.comparingInt(SearchFilter::typeOrder).thenComparing(String::compareTo)).toList();
    }

    public static String typeLabel(String type) {
        return switch (value(type)) {
            case TYPE_MOVIE -> ResUtil.getString(R.string.search_filter_movie);
            case TYPE_SERIES -> ResUtil.getString(R.string.search_filter_series);
            case TYPE_VARIETY -> ResUtil.getString(R.string.search_filter_variety);
            case TYPE_ANIME -> ResUtil.getString(R.string.search_filter_anime);
            case TYPE_DOCUMENTARY -> ResUtil.getString(R.string.search_filter_documentary);
            case TYPE_SHORT_DRAMA -> ResUtil.getString(R.string.search_filter_short_drama);
            case TYPE_OTHER -> ResUtil.getString(R.string.search_filter_other);
            default -> value(type);
        };
    }

    public static List<String> years(List<Vod> items) {
        Set<String> values = new LinkedHashSet<>();
        for (Vod item : items) {
            String year = year(item);
            if (!year.isEmpty()) values.add(year);
        }
        return values.stream().sorted(Comparator.reverseOrder()).toList();
    }

    public static String year(Vod item) {
        Matcher matcher = YEAR_PATTERN.matcher(item.getYear() + " " + item.getName() + " " + item.getRemarks());
        return matcher.find() ? matcher.group() : "";
    }

    private static List<String> typeValues(Vod item) {
        return classify(item.getTypeName(), item.getName(), item.getRemarks(), item.isFolder());
    }

    static List<String> classify(String typeName, String name, String remarks, boolean folder) {
        if (folder) return List.of(TYPE_OTHER);
        String explicit = value(typeName).trim();
        String metadata = explicit + " " + value(name) + " " + value(remarks);
        if (DOCUMENTARY.matcher(metadata).find()) return List.of(TYPE_DOCUMENTARY);
        if (ANIME.matcher(metadata).find()) return List.of(TYPE_ANIME);
        if (VARIETY.matcher(metadata).find()) return List.of(TYPE_VARIETY);
        if (SHORT_DRAMA.matcher(metadata).find()) return List.of(TYPE_SHORT_DRAMA);
        if (SERIES.matcher(metadata).find()) return List.of(TYPE_SERIES);
        if (MOVIE.matcher(metadata).find()) return List.of(TYPE_MOVIE);
        if (explicit.isEmpty()) return List.of(TYPE_MOVIE);
        return TYPE_SEPARATOR.splitAsStream(explicit)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static int typeOrder(String type) {
        int index = BUILT_IN_TYPES.indexOf(type);
        return index < 0 ? BUILT_IN_TYPES.size() : index;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}

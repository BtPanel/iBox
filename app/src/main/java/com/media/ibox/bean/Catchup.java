package com.media.ibox.bean;


import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Catchup {

    private static boolean blank(String value) {
        return value == null || value.isEmpty();
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\$?\\{[^}]*\\})");
    private static final Pattern TAG_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    @SerializedName("type")
    private String type;
    @SerializedName("days")
    private String days;
    @SerializedName("regex")
    private String regex;
    @SerializedName("source")
    private String source;
    @SerializedName("replace")
    private String replace;

    public static Catchup PLTV() {
        Catchup item = new Catchup();
        item.setDays("7");
        item.setType("append");
        item.setRegex("/PLTV/");
        item.setReplace("/PLTV/,/TVOD/");
        item.setSource("?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}");
        return item;
    }

    public static Catchup create() {
        return new Catchup();
    }

    /** 酷9 txt 分组行 PB= 的回看模板: 完整地址按 default, 参数片段按 append。 */
    public static Catchup playback(String source) {
        Catchup item = Catchup.create();
        item.setType(source.startsWith("http") ? "default" : "append");
        item.setSource(source);
        return item;
    }

    public static Catchup decide(Catchup major, Catchup minor) {
        if (!major.isEmpty()) return major;
        if (!minor.isEmpty()) return minor;
        return null;
    }

    public String getType() {
        return blank(type) ? "" : type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDays() {
        return blank(days) ? "" : days;
    }

    public void setDays(String days) {
        this.days = days;
    }

    public boolean hasDays() {
        try {
            return Integer.parseInt(getDays()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String getRegex() {
        return blank(regex) ? "" : regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getReplace() {
        return blank(replace) ? "" : replace;
    }

    public void setReplace(String replace) {
        this.replace = replace;
    }

    public String getSource() {
        return blank(source) ? "" : source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean match(String url) {
        return url.contains(getRegex()) || Pattern.compile(getRegex()).matcher(url).find();
    }

    public boolean isEmpty() {
        return getSource().isEmpty();
    }

    private boolean isDefault() {
        return getType().equals("default");
    }

    private String append(String url, String result) {
        String[] splits = getReplace().split(",", 2);
        if (splits.length == 2) url = url.replaceAll(splits[0], splits[1]);
        if (!blank(URI.create(url).getQuery())) result = result.replace("?", "&");
        return url + result;
    }

    public String format(String url, EpgData data) {
        String result = getSource();
        // {eid} / ${eid}: DIYP 风格的节目 ID, EPG 接口提供 id 字段时才有值
        result = result.replace("{eid}", data.getEid()).replace("${eid}", data.getEid());
        Matcher matcher = TOKEN_PATTERN.matcher(result);
        while (matcher.find()) result = result.replace(matcher.group(1), format(matcher.group(1), data.getStartTime(), data.getEndTime()));
        return isDefault() ? result : append(url, result);
    }

    private String formatTime(long millis, String fmt) {
        return formatTime(millis, fmt, ZoneId.systemDefault());
    }

    static String formatTime(long millis, String fmt, ZoneId defaultZone) {
        // 酷9 兼容: ${(b)} 生成 13 位毫秒时间戳, ${(b)10}/${(b)13} 指定位数, "timestamp" 为 10 位秒
        if (fmt.isEmpty()) return String.valueOf(millis);
        if (fmt.equals("10")) return String.valueOf(millis / 1000);
        if (fmt.equals("13")) return String.valueOf(millis);
        if (fmt.equals("timestamp")) return String.valueOf(millis / 1000);
        ZoneId zoneId = defaultZone;
        int modifier = fmt.lastIndexOf('|');
        if (modifier >= 0) {
            String value = fmt.substring(modifier + 1);
            String pattern = fmt.substring(0, modifier);
            if (value.equalsIgnoreCase("LOCAL")) {
                fmt = pattern;
            } else if (value.equalsIgnoreCase("UTC")) {
                zoneId = ZoneOffset.UTC;
                fmt = pattern;
            } else {
                try {
                    zoneId = ZoneId.of(value);
                    fmt = pattern;
                } catch (DateTimeException ignored) {
                    // 不是时区标记时保留原样, 交给下面的格式化兜底
                }
            }
        }
        try {
            return DateTimeFormatter.ofPattern(fmt, Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(zoneId));
        } catch (Exception e) {
            return "";
        }
    }

    private String format(String group, long start, long end) {
        Matcher matcher = TAG_PATTERN.matcher(group);
        if (!matcher.find()) return "";
        String tag = matcher.group(1);
        int paren = tag.indexOf(')');
        if (tag.startsWith("(b") && paren >= 0) return formatTime(start, tag.substring(paren + 1));
        if (tag.startsWith("(e") && paren >= 0) return formatTime(end, tag.substring(paren + 1));
        if (tag.startsWith("utcend:")) return String.valueOf(end / 1000);
        if (tag.startsWith("utc:")) return String.valueOf(start / 1000);
        return "";
    }
}

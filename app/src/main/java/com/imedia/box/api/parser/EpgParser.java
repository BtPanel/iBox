package com.imedia.box.api.parser;

import android.util.Log;

import com.imedia.box.App;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.Epg;
import com.imedia.box.bean.EpgData;
import com.imedia.box.bean.Live;
import com.imedia.box.bean.Tv;
import com.imedia.box.utils.Download;
import com.imedia.box.utils.FileUtil;
import com.imedia.box.utils.Formatters;
import com.imedia.box.setting.LiveSetting;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EpgParser {

    private static final String TAG = EpgParser.class.getSimpleName();
    private static final java.util.regex.Pattern CHANNEL_KEY_SEPARATOR = java.util.regex.Pattern.compile("[^\\p{L}\\p{N}]");

    private static OffsetDateTime parseFull(String source, ZoneId zoneId) {
        String s = source.trim();
        try {
            String time = s.length() > 14 ? s.substring(0, 14) : s;
            String offset = s.length() > 14 ? s.substring(14).trim() : "";
            if (!offset.isEmpty()) return parseOffset(time + " " + offset);
            return LocalDateTime.parse(time, Formatters.EPG_FULL_NO_TZ).atZone(zoneId).toOffsetDateTime();
        } catch (Exception e) {
            Log.w(TAG, "parseFull failed: " + s + " -> " + e.getMessage());
            return OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
        }
    }

    private static OffsetDateTime parseOffset(String source) {
        try {
            return OffsetDateTime.parse(source, Formatters.EPG_FULL);
        } catch (Exception ignored) {
            return OffsetDateTime.parse(source, Formatters.EPG_FULL_COLON);
        }
    }

    public static void start(Live live, String url) throws Exception {
        start(live, url, false, LiveSetting.getEpgHeaders(url));
    }

    public static synchronized void start(Live live, String url, boolean forceRefresh) throws Exception {
        start(live, url, forceRefresh, LiveSetting.getEpgHeaders(url));
    }

    public static synchronized void start(Live live, String url, boolean forceRefresh, Map<String, String> headers) throws Exception {
        long t0 = System.currentTimeMillis();
        File file = cacheFile(url, headers);
        String reason = refreshReason(file);
        boolean refresh = forceRefresh || reason != null;
        Log.i(TAG, "start url=" + url + " file=" + file.getName() + " refresh=" + refresh + (refresh ? " reason=" + reason : ""));
        if (refresh) {
            try {
                downloadSafely(url, file, headers);
            } catch (Exception e) {
                if (!file.isFile() || file.length() == 0) throw e;
                Log.w(TAG, "EPG refresh failed, using cached file " + file.getName(), e);
            }
        }
        try {
            read(live, file);
        } catch (Exception e) {
            if (refresh) throw e;
            downloadSafely(url, file, headers);
            read(live, file);
        }
        Log.i(TAG, "start done elapsed=" + (System.currentTimeMillis() - t0) + "ms");
    }

    public static synchronized void cache(String url) throws Exception {
        cache(url, LiveSetting.getEpgHeaders(url));
    }

    public static synchronized void cache(String url, Map<String, String> headers) throws Exception {
        File file = cacheFile(url, headers);
        downloadSafely(url, file, headers);
        Log.i(TAG, "cache done url=" + url + " file=" + file.getName());
    }

    private static void read(Live live, File file) throws Exception {
        if (isGzip(file)) readGzip(live, file);
        else readXml(live, file);
    }

    private static void downloadSafely(String url, File file, Map<String, String> headers) throws Exception {
        File temporary = new File(file.getAbsolutePath() + ".download");
        long timeout = 30000;
        try {
            Download.create(url, temporary).headers(headers).timeout(timeout).get();
            if (!temporary.isFile() || temporary.length() == 0) throw new IOException("Empty EPG response");
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temporary.exists() && !temporary.delete()) Log.w(TAG, "Unable to delete " + temporary.getName());
        }
    }

    private static File cacheFile(String url, Map<String, String> headers) {
        String name = Util.md5(url + "\n" + App.gson().toJson(headers)) + ".epg";
        return LiveSetting.isEpgExternalCache() ? Path.iBox("epg", name) : Path.epg(name);
    }

    public static Epg getEpg(String xml, String key, ZoneId zoneId) {
        try {
            Tv tv = new Persister().read(Tv.class, xml, false);
            String rawDate = tv.getDate();
            String date = rawDate.isEmpty() ? LocalDate.now(zoneId).format(Formatters.DATE) : parseFull(rawDate, zoneId).atZoneSameInstant(zoneId).format(Formatters.DATE);
            Epg epg = Epg.create(key, date);
            tv.getProgramme().forEach(programme -> epg.getList().add(getEpgData(programme, zoneId)));
            return epg;
        } catch (Exception e) {
            Log.w(TAG, "getEpg parse failed key=" + key + ": " + e.getMessage());
            return new Epg();
        }
    }

    static String refreshReason(File file) {
        return file.isFile() && file.length() > 0 ? null : "file-missing";
    }

    private static boolean isGzip(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return (fis.read() | (fis.read() << 8)) == 0x8B1F;
        } catch (IOException e) {
            return false;
        }
    }

    private static void readGzip(Live live, File file) throws Exception {
        File xml = new File(file.getParentFile(), file.getName() + ".xml");
        if (!Path.exists(xml) || xml.lastModified() < file.lastModified()) FileUtil.gzipDecompress(file, xml);
        readXml(live, xml);
    }

    private static void readXml(Live live, File file) throws Exception {
        ZoneId zoneId = live.getZoneId();
        Map<String, Channel> liveChannelMap = prepareLiveChannels(live);
        XmlData xmlData = parseXmlData(file);
        ProgrammeResult result = processProgramme(xmlData, liveChannelMap, zoneId);
        bindResultsToLive(live, result);
    }

    private static Map<String, Channel> prepareLiveChannels(Live live) {
        Map<String, Channel> map = new HashMap<>();
        live.getGroups().stream()
                .flatMap(group -> group.getChannel().stream())
                .forEach(channel -> {
                    putChannelKey(map, channel.getTvgId(), channel);
                    putChannelKey(map, channel.getTvgName(), channel);
                    putChannelKey(map, channel.getName(), channel);
                    putChannelKey(map, channel.getShow(), channel);
                });
        return map;
    }

    private static void putChannelKey(Map<String, Channel> map, String key, Channel channel) {
        if (key == null || key.isEmpty()) return;
        map.putIfAbsent(key, channel);
        map.putIfAbsent(normalizeChannelKey(key), channel);
    }

    static String normalizeChannelKey(String key) {
        return key == null ? "" : CHANNEL_KEY_SEPARATOR.matcher(key.toLowerCase(java.util.Locale.ROOT)).replaceAll("");
    }

    private static XmlData parseXmlData(File file) throws Exception {
        Tv tv = new Persister().read(Tv.class, file, false);
        Map<String, List<Tv.Channel>> map = tv.getChannel().stream().collect(Collectors.groupingBy(Tv.Channel::getId));
        return new XmlData(tv, map);
    }

    private static ProgrammeResult processProgramme(XmlData data, Map<String, Channel> liveChannelMap, ZoneId zoneId) {
        Map<String, Map<String, Epg>> epgMap = new HashMap<>();
        Map<String, String> srcMap = new HashMap<>();
        Map<String, Channel> channelCache = new HashMap<>();
        Set<String> channelMiss = new HashSet<>();
        int offsetHours = LiveSetting.getEpgOffsetHours();
        int skipped = 0;
        Iterator<Tv.Programme> programmes = data.tv.getProgramme().iterator();
        while (programmes.hasNext()) {
            Tv.Programme programme = programmes.next();
            programmes.remove();
            String xmlChannelId = programme.getChannel();
            Channel targetChannel;
            if (channelCache.containsKey(xmlChannelId)) {
                targetChannel = channelCache.get(xmlChannelId);
            } else if (channelMiss.contains(xmlChannelId)) {
                targetChannel = null;
            } else {
                targetChannel = findTargetChannel(xmlChannelId, liveChannelMap, data.map);
                if (targetChannel != null) channelCache.put(xmlChannelId, targetChannel);
                else channelMiss.add(xmlChannelId);
            }
            if (targetChannel == null) {
                skipped++;
                continue;
            }
            OffsetDateTime startDate = parseFull(programme.getStart(), zoneId).plusHours(offsetHours);
            OffsetDateTime endDate = parseFull(programme.getStop(), zoneId).plusHours(offsetHours);
            String liveTvgId = targetChannel.getTvgId();
            String programmeDate = startDate.atZoneSameInstant(zoneId).format(Formatters.DATE);
            epgMap.computeIfAbsent(liveTvgId, k -> new HashMap<>())
                    .computeIfAbsent(programmeDate, d -> Epg.create(liveTvgId, d))
                    .getList().add(getEpgData(startDate, endDate, zoneId, programme));
            if (!srcMap.containsKey(liveTvgId)) {
                List<Tv.Channel> xmlChannels = data.map.get(xmlChannelId);
                if (xmlChannels != null) {
                    for (Tv.Channel ch : xmlChannels) {
                        if (ch.hasSrc()) {
                            srcMap.put(liveTvgId, ch.getSrc());
                            break;
                        }
                    }
                }
            }
        }
        Log.i(TAG, "processProgramme skipped(no match)=" + skipped + " matched channels=" + epgMap.size());
        return new ProgrammeResult(epgMap, srcMap);
    }

    private static Channel findTargetChannel(String xmlChannelId, Map<String, Channel> liveChannelMap, Map<String, List<Tv.Channel>> xmlChannelIdMap) {
        Channel targetChannel = findLiveChannel(xmlChannelId, liveChannelMap);
        if (targetChannel != null) return targetChannel;
        List<Tv.Channel> channels = xmlChannelIdMap.get(xmlChannelId);
        if (channels == null) return null;
        return channels.stream()
                .flatMap(xmlChannel -> xmlChannel.getDisplayName().stream())
                .map(Tv.DisplayName::getText)
                .map(name -> findLiveChannel(name, liveChannelMap))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Channel findLiveChannel(String key, Map<String, Channel> liveChannelMap) {
        Channel channel = liveChannelMap.get(key);
        return channel != null ? channel : liveChannelMap.get(normalizeChannelKey(key));
    }

    private static void bindResultsToLive(Live live, ProgrammeResult result) {
        List<Channel> channels = live.getGroups().stream()
                .flatMap(group -> group.getChannel().stream())
                .toList();
        Map<String, Map<String, Epg>> epgAliases = new HashMap<>();
        Map<String, String> srcAliases = new HashMap<>();
        for (Channel channel : channels) {
            Map<String, Epg> dateMap = result.epgMap.get(channel.getTvgId());
            if (dateMap != null) putChannelAliases(epgAliases, channel, dateMap);
            String src = result.srcMap.get(channel.getTvgId());
            if (src != null) putChannelAliases(srcAliases, channel, src);
        }
        int[] counts = {0, 0};
        for (Channel channel : channels) {
            Map<String, Epg> dateMap = result.epgMap.get(channel.getTvgId());
            if (dateMap == null) dateMap = findChannelAlias(epgAliases, channel);
            if (dateMap != null) {
                channel.setDataList(copyEpgDates(dateMap, channel.getTvgId()));
                counts[0]++;
            } else {
                counts[1]++;
            }
            if (channel.getLogo().isEmpty()) {
                String src = result.srcMap.get(channel.getTvgId());
                if (src == null) src = findChannelAlias(srcAliases, channel);
                if (src != null) channel.setLogo(src);
            }
        }
        Log.i(TAG, "bindResultsToLive with-epg=" + counts[0] + " without-epg=" + counts[1]);
    }

    private static List<Epg> copyEpgDates(Map<String, Epg> dateMap, String channelKey) {
        List<Epg> dates = new ArrayList<>();
        dateMap.values().stream().sorted(Comparator.comparing(Epg::getDate)).forEach(source -> {
            source.getList().sort(Comparator.comparingLong(EpgData::getStartTime));
            Epg copy = Epg.create(channelKey, source.getDate());
            copy.setList(source.getList());
            dates.add(copy);
        });
        return dates;
    }

    private static <T> void putChannelAliases(Map<String, T> aliases, Channel channel, T value) {
        putChannelAlias(aliases, channel.getTvgId(), value);
        putChannelAlias(aliases, channel.getTvgName(), value);
        putChannelAlias(aliases, channel.getName(), value);
        putChannelAlias(aliases, channel.getShow(), value);
    }

    private static <T> void putChannelAlias(Map<String, T> aliases, String key, T value) {
        String normalized = normalizeChannelKey(key);
        if (!normalized.isEmpty()) aliases.putIfAbsent(normalized, value);
    }

    private static <T> T findChannelAlias(Map<String, T> aliases, Channel channel) {
        for (String key : List.of(channel.getTvgId(), channel.getTvgName(), channel.getName(), channel.getShow())) {
            T value = aliases.get(normalizeChannelKey(key));
            if (value != null) return value;
        }
        return null;
    }

    private static EpgData getEpgData(Tv.Programme programme, ZoneId zoneId) {
        OffsetDateTime startDate = parseFull(programme.getStart(), zoneId).plusHours(LiveSetting.getEpgOffsetHours());
        OffsetDateTime endDate = parseFull(programme.getStop(), zoneId).plusHours(LiveSetting.getEpgOffsetHours());
        return getEpgData(startDate, endDate, zoneId, programme);
    }

    private static EpgData getEpgData(OffsetDateTime startDate, OffsetDateTime endDate, ZoneId zoneId, Tv.Programme programme) {
        try {
            EpgData epgData = new EpgData();
            epgData.setTitle(programme.getTitle());
            epgData.setStart(startDate.atZoneSameInstant(zoneId).format(Formatters.TIME));
            epgData.setEnd(endDate.atZoneSameInstant(zoneId).format(Formatters.TIME));
            epgData.setStartTime(startDate.toInstant().toEpochMilli());
            epgData.setEndTime(endDate.toInstant().toEpochMilli());
            epgData.trans();
            return epgData;
        } catch (Exception e) {
            return new EpgData();
        }
    }

    private static class XmlData {

        Tv tv;
        Map<String, List<Tv.Channel>> map;

        public XmlData(Tv tv, Map<String, List<Tv.Channel>> map) {
            this.tv = tv;
            this.map = map;
        }
    }

    private static class ProgrammeResult {

        Map<String, Map<String, Epg>> epgMap;
        Map<String, String> srcMap;

        public ProgrammeResult(Map<String, Map<String, Epg>> epgMap, Map<String, String> srcMap) {
            this.epgMap = epgMap;
            this.srcMap = srcMap;
        }
    }
}

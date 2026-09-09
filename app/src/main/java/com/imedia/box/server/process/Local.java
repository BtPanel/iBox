package com.imedia.box.server.process;

import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;
import static fi.iki.elonen.NanoHTTPD.getMimeTypeForFile;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import com.imedia.box.server.Nano;
import com.imedia.box.server.RootPathResolver;
import com.imedia.box.server.ServerAccessPolicy;
import com.imedia.box.server.impl.Process;
import com.imedia.box.utils.FileUtil;
import com.imedia.box.utils.Formatters;
import com.github.catvod.utils.Path;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Local implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return ServerAccessPolicy.matchesEndpoint(url, "/file") || ServerAccessPolicy.matchesEndpoint(url, "/upload")
                || ServerAccessPolicy.matchesEndpoint(url, "/newFolder") || ServerAccessPolicy.matchesEndpoint(url, "/delFolder")
                || ServerAccessPolicy.matchesEndpoint(url, "/delFile");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            if (ServerAccessPolicy.matchesEndpoint(url, "/file")) return getFile(session.getHeaders(), url);
            if (ServerAccessPolicy.matchesEndpoint(url, "/upload")) return upload(session.getParms(), files);
            if (ServerAccessPolicy.matchesEndpoint(url, "/newFolder")) return newFolder(session.getParms());
            if (ServerAccessPolicy.matchesEndpoint(url, "/delFolder") || ServerAccessPolicy.matchesEndpoint(url, "/delFile")) return delete(session.getParms());
            return null;
        } catch (SecurityException e) {
            return Nano.error(Status.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return Nano.error(Status.BAD_REQUEST, e.getMessage() == null ? "Invalid path" : e.getMessage());
        }
    }

    private Response getFile(Map<String, String> headers, String path) throws IOException {
        File file = RootPathResolver.resolve(Path.root(), path.substring(5));
        if (file.isDirectory()) return getFolder(file);
        if (file.isFile()) return getFile(headers, file, getMimeTypeForFile(path));
        throw new FileNotFoundException();
    }

    private Response upload(Map<String, String> params, Map<String, String> files) throws IOException {
        File directory = RootPathResolver.resolve(Path.root(), params.get("path"));
        for (String k : files.keySet()) {
            String fn = params.get(k);
            if (fn == null || fn.isBlank()) throw new IOException("Missing file name");
            File temp = new File(files.get(k));
            if (fn.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                if (!FileUtil.zipDecompress(temp, directory, 2000, 1024L * 1024 * 1024)) throw new IOException("Invalid archive");
            } else {
                Path.copy(temp, RootPathResolver.resolve(Path.root(), params.get("path"), fn));
            }
        }
        return Nano.ok();
    }

    private Response newFolder(Map<String, String> params) throws IOException {
        String name = params.get("name");
        if (name == null || name.isBlank()) throw new IOException("Missing folder name");
        RootPathResolver.resolve(Path.root(), params.get("path"), name).mkdirs();
        return Nano.ok();
    }

    private Response delete(Map<String, String> params) throws IOException {
        File root = Path.root().getCanonicalFile();
        File target = RootPathResolver.resolve(root, params.get("path"));
        if (target.equals(root)) throw new SecurityException("Storage root cannot be deleted");
        Path.clear(target);
        return Nano.ok();
    }

    private Response getFolder(File dir) {
        File rootDir = Path.root();
        String rootPath = rootDir.getAbsolutePath();
        JsonArray files = new JsonArray();
        for (File file : Path.list(dir)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", file.getName());
            obj.addProperty("path", relativeTo(file, rootPath));
            obj.addProperty("time", Formatters.LOCAL_DATETIME.format(Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault())));
            obj.addProperty("dir", file.isDirectory() ? 1 : 0);
            files.add(obj);
        }
        JsonObject info = new JsonObject();
        info.addProperty("parent", parentOf(dir, rootDir, rootPath));
        info.add("files", files);
        return Nano.ok(info.toString());
    }

    private Response getFile(Map<String, String> headers, File file, String mime) throws IOException {
        long fileLen = file.length();
        String etag = etag(file, fileLen);
        String ifNoneMatch = headers.get("if-none-match");
        if (ifNoneMatch != null && (ifNoneMatch.equals("*") || ifNoneMatch.equals(etag))) {
            return newFixedLengthResponse(Status.NOT_MODIFIED, mime, "");
        }
        HttpRange range = HttpRange.from(fileLen, headers, etag);
        if (!range.valid()) return createRangeNotSatisfiableResponse(fileLen);
        FileInputStream fis = new FileInputStream(file);
        skip(fis, range.start);
        Response res;
        if (range.isPartial(fileLen)) {
            res = newFixedLengthResponse(Status.PARTIAL_CONTENT, mime, fis, range.length);
            res.addHeader("Content-Range", "bytes " + range.start + "-" + range.end + "/" + fileLen);
        } else {
            res = newFixedLengthResponse(Status.OK, mime, fis, range.length);
        }
        res.addHeader("Content-Length", String.valueOf(range.length));
        res.addHeader("Accept-Ranges", "bytes");
        res.addHeader("ETag", etag);
        return res;
    }

    private String etag(File file, long fileLen) {
        CRC32 crc = new CRC32();
        crc.update((file.getAbsolutePath() + file.lastModified() + fileLen).getBytes());
        return Long.toHexString(crc.getValue());
    }

    private Response createRangeNotSatisfiableResponse(long fileLen) {
        Response res = newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
        res.addHeader("Content-Range", "bytes */" + fileLen);
        return res;
    }

    private void skip(InputStream is, long bytesToSkip) throws IOException {
        if (bytesToSkip <= 0) return;
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = is.skip(remaining);
            if (skipped <= 0) throw new IOException("Failed to skip desired number of bytes");
            remaining -= skipped;
        }
    }

    private static String relativeTo(File file, String rootPath) {
        String path = file.getAbsolutePath();
        return path.startsWith(rootPath) ? path.substring(rootPath.length()) : path;
    }

    private static String parentOf(File dir, File rootDir, String rootPath) {
        if (dir.equals(rootDir)) return ".";
        File parent = dir.getParentFile();
        if (parent == null || parent.equals(rootDir)) return "";
        return relativeTo(parent, rootPath);
    }

    private record HttpRange(long start, long end, long length, boolean valid) {

        public boolean isPartial(long total) {
            return length < total;
        }

        public static HttpRange invalid() {
            return new HttpRange(0, 0, 0, false);
        }

        public static HttpRange from(long fileLen, Map<String, String> headers, String etag) {
            long start = 0;
            long end = fileLen - 1;
            String rangeHeader = headers.get("range");
            String ifRange = headers.get("if-range");
            if (ifRange != null && !ifRange.equals(etag)) rangeHeader = null;
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                try {
                    String[] parts = rangeHeader.substring(6).split("-", 2);
                    if (!parts[0].isEmpty()) start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) end = Long.parseLong(parts[1]);
                    if (start >= fileLen || start > end) return invalid();
                } catch (NumberFormatException e) {
                    return invalid();
                }
            }
            if (end >= fileLen) end = fileLen - 1;
            return new HttpRange(start, end, end - start + 1, true);
        }
    }
}

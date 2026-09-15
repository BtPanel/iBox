package com.imedia.box.server;

import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.bean.Device;
import com.imedia.box.server.impl.Process;
import com.imedia.box.server.process.Action;
import com.imedia.box.server.process.Cache;
import com.imedia.box.server.process.Local;
import com.imedia.box.server.process.Manage;
import com.imedia.box.server.process.Media;
import com.imedia.box.server.process.Parse;
import com.imedia.box.server.process.Proxy;
import com.github.catvod.utils.Asset;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class Nano extends NanoHTTPD {

    private static final String INDEX = "index.html";

    private List<Process> process;

    public Nano(int port) {
        super(port);
        addProcess();
    }

    private void addProcess() {
        process = new ArrayList<>();
        process.add(new Action());
        process.add(new Manage());
        process.add(new Cache());
        process.add(new Local());
        process.add(new Media());
        process.add(new Parse());
        process.add(new Proxy());
    }

    public static Response ok() {
        return ok("OK");
    }

    public static Response ok(String text) {
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, text);
    }

    public static Response error(String text) {
        return error(Response.Status.INTERNAL_ERROR, text);
    }

    public static Response error(Response.Status status, String text) {
        return newFixedLengthResponse(status, MIME_PLAINTEXT, text);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String url = session.getUri().trim();
        Map<String, String> files = new HashMap<>();
        if (session.getMethod() == Method.POST) parse(session, files);
        Response denied = authorize(session, url);
        if (denied != null) return denied;
        if (ServerAccessPolicy.matchesEndpoint(url, "/tvbus")) return ok(LiveConfig.getResp());
        if (ServerAccessPolicy.matchesEndpoint(url, "/device")) return ok(Device.get().toString());
        for (Process process : process) if (process.isRequest(session, url)) return pairIfNeeded(session, process.doResponse(session, url, files));
        return pairIfNeeded(session, getAssets(url.substring(1)));
    }

    private Response authorize(IHTTPSession session, String url) {
        if (RemoteAccess.isLoopback(session)) return null;
        if (ServerAccessPolicy.isLoopbackOnly(url)) return error(Response.Status.FORBIDDEN, "Local access only");
        if (ServerAccessPolicy.isStreamRequest(url)) {
            if (RemoteAccess.isStreamAuthorized(session) || RemoteAccess.isAuthorized(session)) return null;
            return error(Response.Status.UNAUTHORIZED, "Stream authorization required");
        }
        if (ServerAccessPolicy.requiresRemoteAuthorization(url) && !RemoteAccess.isAuthorized(session)) return remoteDenied(session);
        return null;
    }

    private Response remoteDenied(IHTTPSession session) {
        if (!RemoteAccess.isEnabled()) return error(Response.Status.FORBIDDEN, "Remote control is disabled");
        if (!RemoteAccess.isPrivateNetwork(session)) return error(Response.Status.FORBIDDEN, "Private network only");
        if (ServerAccessPolicy.isAssetRequest(session.getUri())) return pairPage(RemoteAccess.hasPairingAttempt(session));
        return error(Response.Status.UNAUTHORIZED, "Pairing required");
    }

    private Response pairIfNeeded(IHTTPSession session, Response response) {
        if (RemoteAccess.pairedWithPin(session)) response.addHeader("Set-Cookie", RemoteAccess.cookieHeader(session));
        return response;
    }

    private Response pairPage(boolean invalid) {
        String message = invalid ? "<p style='color:#b3261e'>配对码不正确</p>" : "";
        String html = "<!doctype html><html lang='zh-CN'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>设备配对</title><body style='font-family:sans-serif;max-width:360px;margin:18vh auto;padding:24px'>"
                + "<h2>局域网控制配对</h2><p>请输入电视或手机设置中显示的 6 位配对码。</p>" + message
                + "<form method='post'><input name='pin' inputmode='numeric' maxlength='6' autofocus style='font-size:24px;letter-spacing:8px;width:100%;padding:12px;box-sizing:border-box'>"
                + "<button style='margin-top:16px;width:100%;padding:12px'>连接</button></form></body></html>";
        return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_HTML, html);
    }

    private void parse(IHTTPSession session, Map<String, String> files) {
        try {
            String ct = session.getHeaders().get("content-type");
            if (ct != null) session.getHeaders().put("content-type", ct.replace("multipart/form-data", "multipart/form-data; charset=utf-8"));
            session.parseBody(files);
        } catch (Exception ignored) {
        }
    }

    private Response getAssets(String path) {
        try {
            if (path.isEmpty()) path = INDEX;
            InputStream is = Asset.open(path);
            return newFixedLengthResponse(Response.Status.OK, getMimeTypeForFile(path), is, -1);
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, null, 0);
        }
    }
}

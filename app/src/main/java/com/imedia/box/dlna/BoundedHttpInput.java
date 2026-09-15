package com.imedia.box.dlna;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BoundedHttpInput {

    private BoundedHttpInput() {
    }

    static String readLine(InputStream input, int maxBytes) throws IOException, HttpInputException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 256));
        int previous = -1;
        int value;
        while ((value = input.read()) != -1) {
            if (previous == '\r' && value == '\n') {
                byte[] bytes = output.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.ISO_8859_1);
            }
            output.write(value);
            if (output.size() > maxBytes) throw new HttpInputException(413, "Request line or header too large");
            previous = value;
        }
        throw new HttpInputException(400, "Truncated HTTP line");
    }

    static Map<String, List<String>> readHeaders(InputStream input, int maxLineBytes, int maxTotalBytes) throws IOException, HttpInputException {
        Map<String, List<String>> headers = new HashMap<>();
        int total = 0;
        while (true) {
            String line = readLine(input, maxLineBytes);
            total += line.length() + 2;
            if (total > maxTotalBytes) throw new HttpInputException(413, "Headers too large");
            if (line.isEmpty()) return headers;
            int colon = line.indexOf(':');
            if (colon <= 0) throw new HttpInputException(400, "Malformed header");
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) throw new HttpInputException(400, "Malformed header");
            headers.computeIfAbsent(name, key -> new ArrayList<>()).add(line.substring(colon + 1).trim());
        }
    }

    static byte[] readBody(InputStream input, String contentLength, int maxBodyBytes) throws IOException, HttpInputException {
        if (contentLength == null || contentLength.isBlank()) return new byte[0];
        final long length;
        try {
            length = Long.parseLong(contentLength.trim());
        } catch (NumberFormatException e) {
            throw new HttpInputException(400, "Invalid Content-Length");
        }
        if (length < 0) throw new HttpInputException(400, "Invalid Content-Length");
        if (length > maxBodyBytes) throw new HttpInputException(413, "Request body too large");
        byte[] body = new byte[(int) length];
        int offset = 0;
        while (offset < body.length) {
            int count = input.read(body, offset, body.length - offset);
            if (count == -1) throw new HttpInputException(400, "Truncated request body");
            offset += count;
        }
        return body;
    }

    static final class HttpInputException extends Exception {
        private final int statusCode;

        HttpInputException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        int statusCode() {
            return statusCode;
        }
    }
}

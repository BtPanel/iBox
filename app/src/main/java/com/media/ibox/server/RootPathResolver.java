package com.media.ibox.server;

import java.io.File;
import java.io.IOException;

public final class RootPathResolver {

    private RootPathResolver() {
    }

    public static File resolve(File root, String path) throws IOException {
        if (root == null) throw new IOException("Storage root is unavailable");
        File canonicalRoot = root.getCanonicalFile();
        String value = path == null ? "" : path.trim().replace('\\', '/');
        value = value.replaceFirst("^file:/+", "/");
        String normalizedRoot = canonicalRoot.getPath().replace('\\', '/');

        File candidate;
        if (value.equals(normalizedRoot) || value.startsWith(normalizedRoot + "/")) {
            candidate = new File(value);
        } else {
            candidate = new File(canonicalRoot, stripLeadingSeparators(value));
        }

        File canonical = candidate.getCanonicalFile();
        if (!contains(canonicalRoot, canonical)) throw new SecurityException("Path is outside storage root");
        return canonical;
    }

    public static File resolve(File root, String parent, String child) throws IOException {
        File directory = resolve(root, parent);
        File candidate = new File(directory, child == null ? "" : child).getCanonicalFile();
        if (!contains(root.getCanonicalFile(), candidate)) throw new SecurityException("Path is outside storage root");
        return candidate;
    }

    private static boolean contains(File root, File candidate) {
        String rootPath = root.getPath();
        String candidatePath = candidate.getPath();
        return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath + File.separator);
    }

    private static String stripLeadingSeparators(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == '/') index++;
        return value.substring(index);
    }
}

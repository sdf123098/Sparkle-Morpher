package com.micaftic.morpher.peer;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Platform-independent network paths. No normalization that silently merges files. */
public final class PeerPaths {
    private static final Set<String> DEVICES = Set.of("CON", "PRN", "AUX", "NUL", "CLOCK$",
            "COM1","COM2","COM3","COM4","COM5","COM6","COM7","COM8","COM9",
            "LPT1","LPT2","LPT3","LPT4","LPT5","LPT6","LPT7","LPT8","LPT9");
    private PeerPaths() {}
    public static String validate(String path) {
        if (path == null || path.isEmpty() || path.getBytes(StandardCharsets.UTF_8).length > PeerLimits.PATH_BYTES
                || !Normalizer.isNormalized(path, Normalizer.Form.NFC) || path.startsWith("/")
                || path.indexOf('\\') >= 0 || path.chars().anyMatch(c -> c < 32 || c == 127 || ":*?\"<>|".indexOf(c) >= 0))
            throw new IllegalArgumentException("Invalid Peer resource path");
        for (int i=0;i<path.length();i++) {
            char c=path.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= path.length() || !Character.isLowSurrogate(path.charAt(i)))
                    throw new IllegalArgumentException("Invalid Peer path UTF-16");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Invalid Peer path UTF-16");
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                    || DEVICES.contains(part.split("\\.",2)[0].toUpperCase(Locale.ROOT).replace('\u00B9','1').replace('\u00B2','2').replace('\u00B3','3')))
                throw new IllegalArgumentException("Unsafe Peer resource path");
        }
        return path;
    }
    public static String aliasKey(String path) {
        return Normalizer.normalize(validate(path).toUpperCase(Locale.ROOT), Normalizer.Form.NFC);
    }
}

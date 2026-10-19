package com.micaftic.morpher.client.gui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Java's platform font manager includes Windows machine and per-user installed fonts. */
public final class SystemFontCatalog {
    public static final String NAMESPACE="spm_system_font";
    public record Entry(String id,String family) {}
    private static final class Cache {
        static final List<Entry> ENTRIES=discover();
    }
    private SystemFontCatalog() {}
    public static List<Entry> entries(){return Cache.ENTRIES;}
    public static String id(String family){return NAMESPACE+":"+HexFormat.of().formatHex(family.getBytes(StandardCharsets.UTF_8));}
    public static Optional<String> family(String id){return entries().stream().filter(e->e.id().equals(id)).map(Entry::family).findFirst();}
    public static Optional<Font> font(String id){return family(id).map(name->new Font(name,Font.PLAIN,18));}
    private static List<Entry> discover(){
        try {
            return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.getDefault()))
                    .filter(s->!s.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER)
                    .map(s->new Entry(id(s),s)).toList();
        } catch (RuntimeException|LinkageError unavailable) {return List.of();}
    }
}

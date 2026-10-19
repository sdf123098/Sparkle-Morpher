package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.core.architectury.platform.Platform;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;

/** Display names supplement existing resource URLs without changing source identity or routing. */
final class ResourceSourceLabels {
    private static final Properties LABELS = new Properties();
    private static Path file() { return Platform.getConfigFolder().resolve("sparkle_morpher/resource_source_names.properties"); }
    static { try { if(Files.isRegularFile(file()))try(var in=Files.newInputStream(file())){LABELS.load(in);} }catch(Exception e){com.micaftic.morpher.YesSteveModel.LOGGER.debug("Resource source names could not be loaded",e);} }
    static String get(String url) {
        String label=LABELS.getProperty(url);if(label!=null&&!label.isBlank())return label;
        try{String host=java.net.URI.create(url).getHost();return host==null?url:host;}catch(Exception e){return url;}
    }
    static void put(String url,String label) {
        if(label==null||label.isBlank())LABELS.remove(url);else LABELS.setProperty(url,label.strip());
        try{Files.createDirectories(file().getParent());try(var out=Files.newOutputStream(file())){LABELS.store(out,"Resource source display names");}}
        catch(Exception e){com.micaftic.morpher.YesSteveModel.LOGGER.warn("Resource source name could not be saved",e);}
    }
}

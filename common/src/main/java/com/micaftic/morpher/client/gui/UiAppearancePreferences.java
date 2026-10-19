package com.micaftic.morpher.client.gui;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Client-only appearance preferences. GUI scale continues to belong to Minecraft. */
public final class UiAppearancePreferences {
    private static UiAppearancePreferences shared;
    /** All application pages and standalone editors use the same live preferences. */
    public static synchronized UiAppearancePreferences current() {
        Path file=com.micaftic.morpher.core.storage.ModelStoragePaths.folder().resolve("ui.properties");
        if(shared==null||!shared.file.equals(file))shared=new UiAppearancePreferences(file);
        return shared;
    }
    public int backgroundColor(){return (Math.max(0,Math.min(100,opacity))*255/100<<24)|0x171717;}
    public int blurRadius(){return Math.max(0,Math.min(16,blur));}
    public int blurTint(){return (Math.max(0,Math.min(100,opacity))*16/100<<24)|0xFFFFFF;}
    int opacity=72,blur=10;
    String font="minecraft:default";
    private final Path file;
    UiAppearancePreferences(Path file){
        this.file=file;
        if(Files.isRegularFile(file))try(var in=Files.newInputStream(file)){
            var values=new Properties();values.load(in);
            opacity=Math.max(0,Math.min(100,Integer.parseInt(values.getProperty("opacity","72"))));
            blur=Math.max(0,Math.min(16,Integer.parseInt(values.getProperty("blur","10"))));
            String saved=values.getProperty("font","minecraft:default");
            if(saved.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))font=saved;
        }catch(IOException|IllegalArgumentException ignored){opacity=72;blur=10;font="minecraft:default";}
    }
    boolean save(){
        try{
            Files.createDirectories(file.toAbsolutePath().getParent());
            var values=new Properties();values.setProperty("opacity",Integer.toString(opacity));
            values.setProperty("blur",Integer.toString(blur));values.setProperty("font",font);
            Path temp=Files.createTempFile(file.toAbsolutePath().getParent(),"ui-",".tmp");
            try(var out=Files.newOutputStream(temp)){values.store(out,"Sparkle Morpher UI appearance");}
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            finally{Files.deleteIfExists(temp);}
            return true;
        }catch(IOException ignored){return false;}
    }
}

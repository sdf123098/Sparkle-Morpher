package com.micaftic.morpher.core.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local display choices, independent of server authorization and transport. */
public final class LocalDisplayPreferencesStore {
    private static final int MAX_BYTES=262144;
    public record Preferences(Set<String> hiddenModels,int soundMode){
        public Preferences{
            hiddenModels=Set.copyOf(hiddenModels);
            if(soundMode<0||soundMode>2||hiddenModels.size()>256||hiddenModels.stream().anyMatch(id->id.isBlank()||id.length()>512||id.chars().anyMatch(Character::isISOControl)))
                throw new IllegalArgumentException("Invalid local display preferences");
        }
        public boolean visible(String modelId){return !hiddenModels.contains(modelId);}
        public boolean acceptsSound(long encodedBytes,long durationSamples,int sampleRate){
            return soundMode==0||soundMode==1&&encodedBytes>=0&&encodedBytes<40*1024&&durationSamples>0&&sampleRate>0&&(double)durationSamples/sampleRate<4;
        }
    }
    public static final Preferences DEFAULT=new Preferences(Set.of(),0);
    private LocalDisplayPreferencesStore(){}
    public static Preferences loadOrSeed(Path file,Preferences legacy)throws IOException{
        if(Files.exists(file))return read(file);save(file,legacy);return legacy;
    }
    public static Preferences read(Path file)throws IOException{
        if(Files.size(file)>MAX_BYTES)throw new IOException("Local display preferences exceed size limit; file retained");
        byte[] bytes;try(var stream=Files.newInputStream(file)){bytes=stream.readNBytes(MAX_BYTES+1);}
        if(bytes.length>MAX_BYTES)throw new IOException("Local display preferences exceed size limit; file retained");
        String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        try(var reader=new JsonReader(new StringReader(text))){
            Set<String> fields=new HashSet<>(),hidden=new HashSet<>();int mode=-1,version=-1;
            reader.beginObject();while(reader.hasNext()){
                String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate local display setting");
                switch(key){
                    case "schema_version"->{require(reader,JsonToken.NUMBER);version=new java.math.BigDecimal(reader.nextString()).intValueExact();}
                    case "sound_mode"->{require(reader,JsonToken.NUMBER);mode=new java.math.BigDecimal(reader.nextString()).intValueExact();}
                    case "hidden_models"->{reader.beginArray();while(reader.hasNext()){
                        require(reader,JsonToken.STRING);if(!hidden.add(reader.nextString())||hidden.size()>256)throw new IOException("Invalid hidden model list");
                    }reader.endArray();}
                    default->throw new IOException("Unknown local display setting");
                }
            }reader.endObject();
            if(reader.peek()!=JsonToken.END_DOCUMENT||version!=1||!fields.equals(Set.of("schema_version","hidden_models","sound_mode")))throw new IOException("Invalid local display preferences schema");
            return new Preferences(hidden,mode);
        }catch(RuntimeException invalid){throw new IOException("Unreadable local display preferences; file retained",invalid);}
    }
    private static void require(JsonReader reader,JsonToken token)throws IOException{if(reader.peek()!=token)throw new IOException("Wrong local display setting type");}
    public static void save(Path file,Preferences preferences)throws IOException{
        JsonObject json=new JsonObject();json.addProperty("schema_version",1);json.addProperty("sound_mode",preferences.soundMode());
        JsonArray hidden=new JsonArray();preferences.hiddenModels().stream().sorted().forEach(hidden::add);json.add("hidden_models",hidden);
        byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Local display preferences exceed size limit");
        Path target=file.toAbsolutePath().normalize();Files.createDirectories(target.getParent());Path temporary=Files.createTempFile(target.getParent(),"local-display-",".tmp");
        try{Files.write(temporary,bytes);AtomicFileMover.moveWithRetry(temporary,target);}finally{Files.deleteIfExists(temporary);}
    }
}

package com.micaftic.morpher.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Logical layout from all 68 final handoff scenes; live controls supply their own data/actions. */
public final class FinalUiLayout {
    private static final JsonObject SCENES = read();
    private static final java.util.Map<String,JsonObject> MIDDLE=new java.util.HashMap<>();
    private FinalUiLayout() {}
    private static JsonObject read() {
        try (var in = FinalUiLayout.class.getResourceAsStream("/assets/sparkle_morpher/ui/final-layouts.json")) {
            if (in == null) throw new IllegalStateException("Missing final UI layout contract");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    public static Scene scene(String name, int width, int height) {
        String compact = name + "-gui4";
        if (width >= 560 && width < 800 && SCENES.has(compact) && SCENES.has(name)) {
            // GUI scale 3 at 1920x1080 is 640x360: interpolate the two authored layouts.
            // Use a stable reference so controls do not change size while resizing the window.
            return new Scene(name+"-gui3",middleScene(name,compact),width,height);
        }
        if (width < 560 && SCENES.has(compact)) name = compact;
        if (!SCENES.has(name)) throw new IllegalArgumentException("Unknown UI scene: " + name);
        return new Scene(name, SCENES.getAsJsonObject(name), width, height);
    }
    private static JsonObject middleScene(String name,String compact){return MIDDLE.computeIfAbsent(name,key->middle(SCENES.getAsJsonObject(compact),SCENES.getAsJsonObject(key)).getAsJsonObject());}
    private static com.google.gson.JsonElement middle(com.google.gson.JsonElement small,com.google.gson.JsonElement large){
        if(small.isJsonObject()&&large.isJsonObject()){
            var result=small.getAsJsonObject().deepCopy();
            for(var entry:result.entrySet())if(large.getAsJsonObject().has(entry.getKey()))
                entry.setValue(middle(entry.getValue(),large.getAsJsonObject().get(entry.getKey())));
            return result;
        }
        if(small.isJsonArray()&&large.isJsonArray()&&small.getAsJsonArray().size()==large.getAsJsonArray().size()){
            var result=new com.google.gson.JsonArray();
            for(int i=0;i<small.getAsJsonArray().size();i++)result.add(middle(small.getAsJsonArray().get(i),large.getAsJsonArray().get(i)));
            return result;
        }
        if(small.isJsonPrimitive()&&large.isJsonPrimitive()&&small.getAsJsonPrimitive().isNumber()&&large.getAsJsonPrimitive().isNumber())
            return new com.google.gson.JsonPrimitive(Math.round((small.getAsDouble()*2+large.getAsDouble())/3));
        return small.deepCopy();
    }
    public record Rect(int x, int y, int w, int h) {}
    public record Scene(String name, JsonObject data, int width, int height) {
        public int referenceWidth() { return data.get("width").getAsInt(); }
        public int referenceHeight() { return data.get("height").getAsInt(); }
        public int x(double x) {
            // Fixed native-size controls: only anchors on the right track a wider window.
            return (int)Math.round(x + (x > referenceWidth() * .65 ? width - referenceWidth() : 0));
        }
        public int y(double y) {
            return (int)Math.round(y + (y > referenceHeight() * .88 ? height - referenceHeight() : 0));
        }
        public Rect control(int index) {
            var c = data.getAsJsonArray("controls").get(index).getAsJsonArray();
            return new Rect(x(c.get(0).getAsDouble()), y(c.get(1).getAsDouble()),
                    (int)Math.round(c.get(2).getAsDouble()), (int)Math.round(c.get(3).getAsDouble()));
        }
        public Rect area(String key) {
            var c = data.getAsJsonObject("pageData").getAsJsonArray(key);
            int extra = c.get(0).getAsDouble() <= referenceWidth()*.65 && c.get(0).getAsDouble() + c.get(2).getAsDouble() >= referenceWidth()-16 ? width-referenceWidth() : 0;
            return new Rect(x(c.get(0).getAsDouble()), y(c.get(1).getAsDouble()),
                    Math.max(1,(int)Math.round(c.get(2).getAsDouble())+extra), (int)Math.round(c.get(3).getAsDouble()));
        }
    }
}

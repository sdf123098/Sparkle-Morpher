package net.minecraft.client.renderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
public class LightTexture extends AbstractTexture {
    public static int pack(int block, int sky) { return (block << 4) | sky; }
    public void turnOnLightLayer() {}
    public void turnOffLightLayer() {}
}

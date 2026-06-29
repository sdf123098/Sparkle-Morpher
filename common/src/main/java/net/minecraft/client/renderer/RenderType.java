package net.minecraft.client.renderer;
import net.minecraft.resources.Identifier;
import java.util.Optional;
public class RenderType {
    public static RenderType entityCutoutNoCull(Identifier id) { return new RenderType(); }
    public Optional<RenderType> outline() { return Optional.empty(); }
}

package net.minecraft.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.gui.Font;
import net.minecraft.world.level.Level;
import net.minecraft.client.multiplayer.ClientLevel;

public class Minecraft {
    public static Minecraft getInstance() { return null; }
    public net.minecraft.client.DeltaTracker getDeltaTracker() { return null; }
    public static void execute(Runnable r) { r.run(); }
    public static void submit(Runnable r) { r.run(); }
    public int getFps() { return 60; }
    public Font font;
    public net.minecraft.client.MouseHandler mouseHandler;
    public net.minecraft.client.renderer.GameRenderer gameRenderer;
    public SoundManager getSoundManager() { return null; }
    public TextureManager getTextureManager() { return null; }
    public LocalPlayer player;
    public ClientLevel level;
    public net.minecraft.client.gui.screens.Screen screen;
    public static boolean isRenderingShadowPass() { return false; }
    public void setScreen(net.minecraft.client.gui.screens.Screen s) {}
    public static boolean isLocalServer() { return false; }
    public net.minecraft.client.Window window;
    public com.mojang.blaze3d.platform.Window getWindow() { return null; }
    public net.minecraft.world.phys.HitResult hitResult;
    public net.minecraft.client.renderer.entity.EntityRenderDispatcher getEntityRenderDispatcher() { return null; }
    public Object getGameProfile() { return null; }
    public net.minecraft.client.User getUser() { return null; }
    public void setClipboard(String text) {}
    public net.minecraft.client.KeyboardHandler keyboardHandler;
    public net.minecraft.client.User user;
    public net.minecraft.client.Options options;
    public boolean isWindowActive() { return true; }
    public net.minecraft.client.particle.ParticleEngine particleEngine;
    public net.minecraft.client.renderer.RenderTarget getMainRenderTarget() { return new net.minecraft.client.renderer.RenderTarget(); }
    public net.minecraft.client.multiplayer.ClientPacketListener getConnection() { return null; }
    public net.minecraft.client.gui.screens.Overlay getOverlay() { return null; }
    public net.minecraft.world.scores.Scoreboard getScoreboard() { return null; }
    public boolean hasAltDown() { return false; }
    public boolean shouldEntityAppearGlowing(net.minecraft.world.entity.Entity e) { return false; }
    public net.minecraft.client.resources.language.LanguageManager getLanguageManager() { return null; }
    public net.minecraft.client.renderer.RenderBuffers renderBuffers() { return null; }
    public net.minecraft.world.entity.Entity getCameraEntity() { return null; }
    public net.minecraft.client.renderer.block.BlockRenderDispatcher getBlockRenderer() { return null; }
    public net.minecraft.client.renderer.entity.ItemRenderer getItemRenderer() { return null; }
    public net.minecraft.client.renderer.ItemInHandRenderer getItemInHandRenderer() { return null; }
    public net.minecraft.server.packs.resources.ResourceManager getResourceManager() { return null; }
    public net.minecraft.client.model.geom.EntityModelSet getEntityModels() { return null; }
    public InputType getLastInputType() { return InputType.NONE; }
    public static boolean renderNames() { return true; }
}

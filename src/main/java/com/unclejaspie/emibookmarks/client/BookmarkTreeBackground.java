package com.unclejaspie.emibookmarks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.unclejaspie.emibookmarks.Emibookmarks;
import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A screen-local post chain; does not replace the player's active GameRenderer effect. */
@Mod.EventBusSubscriber(modid = Emibookmarks.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BookmarkTreeBackground {
    private static PostChain blur;
    private static int width, height;
    private static boolean failed;
    public static void release() {
        if (blur != null) blur.close(); blur = null; width = height = 0; failed = false;
    }
    @SubscribeEvent public static void close(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof BoMScreen) release();
    }
    public static void render(GuiGraphics graphics, BoMScreen screen) {
        var mc = Minecraft.getInstance(); graphics.flush();
        if (BookmarkClientConfig.TREE_BLUR.get() && !failed) {
            var projection = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
            var sorting = RenderSystem.getVertexSorting();
            try {
                var target = mc.getMainRenderTarget();
                if (blur == null) blur = new PostChain(mc.getTextureManager(), mc.getResourceManager(), target,
                        new ResourceLocation(Emibookmarks.MODID, "shaders/post/tree_blur.json"));
                if (width != target.width || height != target.height) { width = target.width; height = target.height; blur.resize(width, height); }
                blur.process(mc.getFrameTime());
            } catch (Exception | LinkageError failure) {
                if (blur != null) blur.close(); blur = null; failed = true;
                com.mojang.logging.LogUtils.getLogger().warn("Tree blur unavailable; using transparent world background", failure);
            } finally {
                mc.getMainRenderTarget().bindWrite(true);
                RenderSystem.setProjectionMatrix(projection, sorting);
                RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest();
                graphics.setColor(1, 1, 1, 1);
                // PostChain writes a fullscreen quad into the main depth attachment.
                // Subsequent native EMI nodes must start with an empty GUI depth buffer.
                RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            }
        }
        int alpha = (int) Math.round(BookmarkClientConfig.TREE_OPACITY.get() * 255);
        if (alpha > 0) {
            graphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), 0, 0, screen.width, screen.height, alpha << 24);
            graphics.flush();
        }
    }
}

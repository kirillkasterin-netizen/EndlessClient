package dev.endless.util.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * Utility class for common framebuffer operations.
 * Provides methods for copying, configuring, and managing framebuffers used
 * in shader effects.
 */
public final class FramebufferUtils {
    
    /**
     * Copies the contents of the main framebuffer (color + depth) to the target framebuffer.
     * Uses a fast hardware blit operation.
     *
     * @param target The destination framebuffer
     * @param width  The width of the area to copy
     * @param height The height of the area to copy
     */
    public static void copyMainFramebuffer(Framebuffer target, int width, int height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getFramebuffer().fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.fbo);

        GL30.glBlitFramebuffer(
                0, 0, width, height,
                0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
        );

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        mc.getFramebuffer().beginWrite(true);
    }
    
    /**
     * Configures a depth texture for sampling in a shader.
     * Disables comparison mode and sets nearest filtering, which is required
     * for sampling depth as a regular texture.
     *
     * @param depthTex The depth texture ID
     */
    public static void configureDepthTexture(int depthTex) {
        RenderSystem.bindTexture(depthTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        RenderSystem.bindTexture(0);
    }
    
    /**
     * Sets linear filtering on a framebuffer's color attachment.
     * Used for smooth blur and bloom effects.
     *
     * @param fb The framebuffer to configure
     */
    public static void setLinearFiltering(Framebuffer fb) {
        RenderSystem.bindTexture(fb.getColorAttachment());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        RenderSystem.bindTexture(0);
    }
    
    /**
     * Clears the framebuffer to transparent black.
     *
     * @param fb The framebuffer to clear
     */
    public static void clearTransparent(Framebuffer fb) {
        fb.setClearColor(0f, 0f, 0f, 0f);
        fb.clear();
    }
    
    /**
     * Safely deletes a framebuffer if not null.
     *
     * @param fb The framebuffer to delete (may be null)
     */
    public static void safeDelete(Framebuffer fb) {
        if (fb != null) fb.delete();
    }
    
    private FramebufferUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}

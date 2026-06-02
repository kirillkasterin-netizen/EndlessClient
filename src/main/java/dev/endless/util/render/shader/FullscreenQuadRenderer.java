package dev.endless.util.render.shader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/**
 * Utility for drawing a fullscreen quad with the currently bound shader.
 * Used by post-processing effects like blur, glow, and overlays.
 */
public final class FullscreenQuadRenderer {
    
    /**
     * Draws a fullscreen quad covering the entire scaled window.
     * The quad uses POSITION_TEXTURE_COLOR vertex format with UV coordinates
     * (0,0) at top-left and (1,1) at bottom-right.
     */
    public static void draw() {
        MinecraftClient mc = MinecraftClient.getInstance();
        float scaledWidth = Math.max(mc.getWindow().getScaledWidth(), 1);
        float scaledHeight = Math.max(mc.getWindow().getScaledHeight(), 1);
        
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, 
                VertexFormats.POSITION_TEXTURE_COLOR
        );
        
        buffer.vertex(0, 0, 0).texture(0, 1).color(1f, 1f, 1f, 1f);
        buffer.vertex(0, scaledHeight, 0).texture(0, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(scaledWidth, scaledHeight, 0).texture(1, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(scaledWidth, 0, 0).texture(1, 1).color(1f, 1f, 1f, 1f);
        
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
    
    /**
     * Draws a fullscreen quad with custom dimensions.
     *
     * @param width  Width of the quad
     * @param height Height of the quad
     */
    public static void draw(float width, float height) {
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, 
                VertexFormats.POSITION_TEXTURE_COLOR
        );
        
        buffer.vertex(0, 0, 0).texture(0, 1).color(1f, 1f, 1f, 1f);
        buffer.vertex(0, height, 0).texture(0, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(width, height, 0).texture(1, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(width, 0, 0).texture(1, 1).color(1f, 1f, 1f, 1f);
        
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
    
    private FullscreenQuadRenderer() {
        throw new UnsupportedOperationException("Utility class");
    }
}

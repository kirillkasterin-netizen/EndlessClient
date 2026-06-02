package dev.endless.util.render.renderers.impl;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import dev.endless.util.render.builders.states.QuadColorState;
import dev.endless.util.render.builders.states.QuadRadiusState;
import dev.endless.util.render.builders.states.SizeState;
import dev.endless.util.render.providers.ResourceProvider;
import dev.endless.util.render.renderers.IRenderer;

public record BuiltLiquidGlass(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness
    ) implements IRenderer {

    private static final ShaderProgramKey LIQUID_GLASS_SHADER_KEY = new ShaderProgramKey(
        ResourceProvider.getShaderIdentifier("liquid_glass"),
        VertexFormats.POSITION_COLOR, 
        Defines.EMPTY
    );
    
    private static final Supplier<SimpleFramebuffer> TEMP_FBO_SUPPLIER = Suppliers
        .memoize(() -> new SimpleFramebuffer(1920, 1024, false));
    
    private static float mouseX = 0;
    private static float mouseY = 0;
    
    public static void setMousePosition(float x, float y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        SimpleFramebuffer fbo = TEMP_FBO_SUPPLIER.get();

        // Resize FBO if needed
        if (fbo.textureWidth != main.textureWidth || fbo.textureHeight != main.textureHeight) {
            fbo.resize(main.textureWidth, main.textureHeight);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        // Copy main framebuffer to temp FBO
        fbo.beginWrite(false);
        main.draw(fbo.textureWidth, fbo.textureHeight);

        // Switch back to main framebuffer
        main.beginWrite(false);

        // Set texture from FBO
        RenderSystem.setShaderTexture(0, fbo.getColorAttachment());

        float width = size.width();
        float height = size.height();

        // Set shader and uniforms
        ShaderProgram shader = RenderSystem.setShader(LIQUID_GLASS_SHADER_KEY);
        shader.getUniform("Size").set(width, height);
        shader.getUniform("Radius").set(
            radius.radius1(), 
            radius.radius2(),
            radius.radius3(), 
            radius.radius4()
        );
        shader.getUniform("Smoothness").set(smoothness);
        shader.getUniform("ScreenSize").set(
            (float) MinecraftClient.getInstance().getWindow().getScaledWidth(),
            (float) MinecraftClient.getInstance().getWindow().getScaledHeight()
        );
        shader.getUniform("MousePos").set(mouseX, mouseY);
        shader.getUniform("Time").set((System.currentTimeMillis() % 100000) / 1000.0f);

        // Build and render quad
        BufferBuilder builder = Tessellator.getInstance()
                .begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        builder.vertex(matrix, x, y, z).color(color.color1());
        builder.vertex(matrix, x, y + height, z).color(color.color2());
        builder.vertex(matrix, x + width, y + height, z).color(color.color3());
        builder.vertex(matrix, x + width, y, z).color(color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        // Cleanup
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}

package dev.endless.util.render.shader;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;

/**
 * Helper class for setting uniform variables on shader programs.
 * Provides safe null-checked methods for common uniform types.
 */
public final class UniformHelper {
    
    /**
     * Sets a single float uniform on the shader.
     *
     * @param shader The shader program
     * @param name   The uniform name
     * @param value  The float value
     */
    public static void setFloat(ShaderProgram shader, String name, float value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }
    
    /**
     * Sets a single integer uniform on the shader.
     *
     * @param shader The shader program
     * @param name   The uniform name
     * @param value  The integer value
     */
    public static void setInt(ShaderProgram shader, String name, int value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }
    
    /**
     * Sets a vec2 uniform on the shader.
     *
     * @param shader The shader program
     * @param name   The uniform name
     * @param x      X component
     * @param y      Y component
     */
    public static void setVec2(ShaderProgram shader, String name, float x, float y) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y);
    }
    
    /**
     * Sets a vec3 uniform on the shader.
     *
     * @param shader The shader program
     * @param name   The uniform name
     * @param x      X component
     * @param y      Y component
     * @param z      Z component
     */
    public static void setVec3(ShaderProgram shader, String name, float x, float y, float z) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y, z);
    }
    
    /**
     * Sets a vec4 uniform on the shader.
     *
     * @param shader The shader program
     * @param name   The uniform name
     * @param x      X component
     * @param y      Y component
     * @param z      Z component
     * @param w      W component
     */
    public static void setVec4(ShaderProgram shader, String name, float x, float y, float z, float w) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y, z, w);
    }
    
    /**
     * Sets a color uniform from a packed integer color (0xRRGGBB or 0xAARRGGBB format).
     * Only the RGB components are used.
     *
     * @param shader The shader program
     * @param name   The uniform name
     * @param color  The packed color
     */
    public static void setColor(ShaderProgram shader, String name, int color) {
        setVec3(shader, name, 
                ColorUtils.redf(color), 
                ColorUtils.greenf(color), 
                ColorUtils.bluef(color));
    }
    
    /**
     * Sets a vec4 color uniform from a packed integer color (0xAARRGGBB format).
     * Includes alpha component.
     *
     * @param shader The shader program
     * @param name   The uniform name
     * @param color  The packed ARGB color
     */
    public static void setColorWithAlpha(ShaderProgram shader, String name, int color) {
        setVec4(shader, name,
                ColorUtils.redf(color),
                ColorUtils.greenf(color),
                ColorUtils.bluef(color),
                ColorUtils.alphaf(color));
    }
    
    /**
     * Sets standard kawase blur uniforms on the shader.
     *
     * @param shader    The shader program
     * @param texWidth  The texture width
     * @param texHeight The texture height
     * @param offset    The blur offset
     */
    public static void setKawaseUniforms(ShaderProgram shader, int texWidth, int texHeight, float offset) {
        int safeWidth = Math.max(1, texWidth);
        int safeHeight = Math.max(1, texHeight);
        setVec2(shader, "uSize", safeWidth, safeHeight);
        setVec2(shader, "uOffset", offset, offset);
        setVec2(shader, "uHalfPixel", 0.5f / safeWidth, 0.5f / safeHeight);
    }
    
    private UniformHelper() {
        throw new UnsupportedOperationException("Utility class");
    }
}

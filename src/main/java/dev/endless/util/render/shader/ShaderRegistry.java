package dev.endless.util.render.shader;

import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Registry of shader program keys used throughout WraithClient.
 * Centralized location for all custom shaders to maintain consistency
 * and make it easier to add or modify shaders.
 */
public final class ShaderRegistry {
    
    private static final String NAMESPACE = "mre";
    
    // ── Hands shaders ──────────────────────────────────────────────────────
    public static final ShaderProgramKey HANDS_MASK_DIFF = register(
            "hands", "hands_mask_diff", VertexFormats.POSITION_TEXTURE_COLOR
    );
    
    public static final ShaderProgramKey HANDS_GLOW = register(
            "hands", "hands_glow", VertexFormats.POSITION_TEXTURE_COLOR
    );
    
    public static final ShaderProgramKey HANDS_OVERLAY = register(
            "hands", "hands_overlay", VertexFormats.POSITION_TEXTURE_COLOR
    );
    
    public static final ShaderProgramKey HANDS_KAWASE_DOWN = register(
            "hands", "hands_kawase_down", VertexFormats.POSITION_TEXTURE_COLOR
    );
    
    public static final ShaderProgramKey HANDS_KAWASE_UP = register(
            "hands", "hands_kawase_up", VertexFormats.POSITION_TEXTURE_COLOR
    );
    
    // ── Block overlay shader ───────────────────────────────────────────────
    public static final ShaderProgramKey BLOCK_OVERLAY = register(
            "blockoverlay", "block_overlay", VertexFormats.POSITION_TEXTURE_COLOR
    );
    
    // ── Kawase blur shaders (general purpose) ──────────────────────────────
    public static final ShaderProgramKey KAWASE_DOWN = register(
            "kawase_down", "kawase_down", VertexFormats.POSITION_TEXTURE_COLOR
    );
    
    public static final ShaderProgramKey KAWASE_UP = register(
            "kawase_up", "kawase_up", VertexFormats.POSITION_TEXTURE_COLOR
    );
    
    // ── Sky shader ──────────────────────────────────────────────────────────
    public static final ShaderProgramKey SKY_SHADER = register(
            "sky", "sky_shader", VertexFormats.POSITION_TEXTURE_COLOR
    );

    // ── Sonar scan effect ───────────────────────────────────────────────────
    public static final ShaderProgramKey SONAR_SCAN = register(
            "sonar", "scan_effect", VertexFormats.POSITION_TEXTURE
    );
    
    /**
     * Registers a shader by its package and name.
     *
     * @param shaderPackage The shader package directory (e.g. "hands", "blockoverlay")
     * @param shaderName    The shader file name (without extension)
     * @param vertexFormat  The vertex format used by this shader
     * @return A ShaderProgramKey for this shader
     */
    private static ShaderProgramKey register(String shaderPackage, String shaderName, VertexFormat vertexFormat) {
        return new ShaderProgramKey(
                Identifier.of(NAMESPACE, "core/" + shaderPackage + "/" + shaderName),
                vertexFormat,
                Defines.EMPTY
        );
    }
    
    private ShaderRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }
}

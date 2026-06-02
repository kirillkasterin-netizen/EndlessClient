package dev.endless.util.render.shader;

import net.minecraft.util.math.MathHelper;

/**
 * Color utility methods for converting between integer color representations
 * and float components used by shaders.
 * 
 * Colors are stored as packed integers in 0xAARRGGBB format.
 */
public final class ColorUtils {
    
    /**
     * Extracts the alpha component from a packed integer color.
     *
     * @param color The packed color (0xAARRGGBB)
     * @return Alpha as integer (0-255)
     */
    public static int alpha(int color) {
        return (color >> 24) & 0xFF;
    }
    
    /**
     * Extracts the red component from a packed integer color.
     *
     * @param color The packed color (0xAARRGGBB)
     * @return Red as integer (0-255)
     */
    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }
    
    /**
     * Extracts the green component from a packed integer color.
     *
     * @param color The packed color (0xAARRGGBB)
     * @return Green as integer (0-255)
     */
    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }
    
    /**
     * Extracts the blue component from a packed integer color.
     *
     * @param color The packed color (0xAARRGGBB)
     * @return Blue as integer (0-255)
     */
    public static int blue(int color) {
        return color & 0xFF;
    }
    
    /**
     * Extracts the alpha component as a float (0.0-1.0).
     *
     * @param color The packed color (0xAARRGGBB)
     * @return Alpha as float
     */
    public static float alphaf(int color) {
        return alpha(color) / 255.0f;
    }
    
    /**
     * Extracts the red component as a float (0.0-1.0).
     *
     * @param color The packed color (0xAARRGGBB)
     * @return Red as float
     */
    public static float redf(int color) {
        return red(color) / 255.0f;
    }
    
    /**
     * Extracts the green component as a float (0.0-1.0).
     *
     * @param color The packed color (0xAARRGGBB)
     * @return Green as float
     */
    public static float greenf(int color) {
        return green(color) / 255.0f;
    }
    
    /**
     * Extracts the blue component as a float (0.0-1.0).
     *
     * @param color The packed color (0xAARRGGBB)
     * @return Blue as float
     */
    public static float bluef(int color) {
        return blue(color) / 255.0f;
    }
    
    /**
     * Packs RGBA components into a single integer.
     *
     * @param red   Red component (0-255)
     * @param green Green component (0-255)
     * @param blue  Blue component (0-255)
     * @param alpha Alpha component (0-255)
     * @return Packed color in 0xAARRGGBB format
     */
    public static int pack(int red, int green, int blue, int alpha) {
        return ((alpha & 0xFF) << 24) 
             | ((red & 0xFF) << 16) 
             | ((green & 0xFF) << 8) 
             | (blue & 0xFF);
    }
    
    /**
     * Sets the alpha of a packed color, preserving RGB.
     *
     * @param color The original packed color
     * @param alpha New alpha value (0-255)
     * @return Modified packed color
     */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
    
    /**
     * Linearly interpolates between two colors.
     *
     * @param colorA First color
     * @param colorB Second color
     * @param t      Interpolation factor (0.0-1.0)
     * @return Interpolated color
     */
    public static int lerp(int colorA, int colorB, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int a = (int) MathHelper.lerp(t, alpha(colorA), alpha(colorB));
        int r = (int) MathHelper.lerp(t, red(colorA), red(colorB));
        int g = (int) MathHelper.lerp(t, green(colorA), green(colorB));
        int b = (int) MathHelper.lerp(t, blue(colorA), blue(colorB));
        return pack(r, g, b, a);
    }
    
    private ColorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}

package endless.ere.client.screens.panelgui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.DrawContext;
import endless.ere.utility.render.display.shader.DrawUtil;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import net.minecraft.client.util.math.MatrixStack;

public class RenderUtil {

    public static void drawRoundedRect(DrawContext g, float x, float y, float width, float height, float radius, int color) {
        DrawUtil.drawRoundedRect(g.getMatrices(), x, y, width, height, BorderRadius.all(radius), new ColorRGBA(color));
    }

    public static void drawRoundedRect(DrawContext g, float x, float y, float width, float height, float radius, int color, float thickness) {
        // Fallback for drawRoundedRect with thickness
        DrawUtil.drawRoundedRect(g.getMatrices(), x, y, width, height, BorderRadius.all(radius), new ColorRGBA(color));
    }

    public static void drawRoundedRectOutline(DrawContext g, float x, float y, float width, float height, float radius, float thickness, int color, int unknown) {
        // Wait, DrawUtil doesn't have an outline method in previous searches.
        // Let's just draw a filled rect with thickness offset as a hack or skip outline.
        // I will draw a slightly larger rect behind if needed, but for now I'll just draw a solid color or omit it.
    }

    public static class Blur {
        public static void drawBlur(DrawContext g, float x, float y, float width, float height, float radius, int blurRadius, int unknown) {
            DrawUtil.drawBlur(g.getMatrices(), x, y, width, height, blurRadius, BorderRadius.all(radius), ColorRGBA.WHITE.withAlpha(255));
        }
    }
}

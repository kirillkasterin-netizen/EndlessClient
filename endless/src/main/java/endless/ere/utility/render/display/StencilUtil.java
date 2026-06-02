package endless.ere.utility.render.display;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;

public class StencilUtil {

    private static int stencilLevel = 0;

    public static void initStencilToWrite() {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        RenderSystem.assertOnRenderThread();
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        
        if (stencilLevel == 0) {
            RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        } else {
            GL11.glStencilFunc(GL11.GL_EQUAL, stencilLevel, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
        }
        GL11.glStencilMask(0xFF);
        stencilLevel++;
    }

    public static void readStencilBuffer(int ignored) {
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        GL11.glStencilMask(0x00);
        GL11.glStencilFunc(GL11.GL_EQUAL, stencilLevel, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    public static void uninitStencil() {
        stencilLevel--;
        if (stencilLevel <= 0) {
            stencilLevel = 0;
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        } else {
            GL11.glStencilFunc(GL11.GL_EQUAL, stencilLevel, 0xFF);
            GL11.glStencilMask(0x00);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        }
    }
}

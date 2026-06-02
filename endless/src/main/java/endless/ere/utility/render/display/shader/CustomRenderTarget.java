package endless.ere.utility.render.display.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import endless.ere.utility.interfaces.IWindow;

public class CustomRenderTarget extends Framebuffer implements IWindow {
    private boolean linear;

    public CustomRenderTarget(boolean useDepth) {
        super(useDepth);
    }

    public CustomRenderTarget(int width, int height, boolean useDepth) {
        super(useDepth);
        this.resize(width, height);
    }

    public CustomRenderTarget setLinear() {
        this.linear = true;
        RenderSystem.recordRenderCall(() -> this.setTexFilter(GL11.GL_LINEAR));
        return this;
    }

    @Override
    public void setTexFilter(int framebufferFilterIn) {
        super.setTexFilter(this.linear ? GL11.GL_LINEAR : framebufferFilterIn);
    }

    private void resizeFramebuffer() {
        if (this.needsNewFramebuffer()) {
            this.initFbo(Math.max(mw.getWidth(), 1), Math.max(mw.getHeight(), 1));
        }
    }

    public void setup(boolean clear) {
        this.resizeFramebuffer();
        if (clear) this.clear();
        this.beginWrite(false);
    }

    public void setup() {
        setup(true);
    }

    public void stop() {
        this.endWrite();
        mc.getFramebuffer().beginWrite(true);
    }

    private boolean needsNewFramebuffer() {
        return this.textureWidth != mw.getWidth() || this.textureHeight != mw.getHeight();
    }
}

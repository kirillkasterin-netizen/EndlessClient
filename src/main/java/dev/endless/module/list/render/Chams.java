package dev.endless.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import dev.endless.event.list.EventWorldRender;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.render.providers.ColorProvider;

@ModuleInformation(moduleName = "Chams", moduleDesc = "Рисует игроков моделькой как в Totem Angel", moduleCategory = ModuleCategory.RENDER)
public class Chams extends Module {

    private final SliderSetting alpha      = new SliderSetting("Прозрачность",  0.8f, 0.1f, 1.0f, 0.05f);
    private final SliderSetting brightness = new SliderSetting("Яркость",       0.6f, 0.1f, 1.0f, 0.05f);
    private final SliderSetting lineWidth  = new SliderSetting("Толщина линий", 1.0f, 0.1f, 3.0f, 0.05f);
    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true);
    private final BooleanSetting glow         = new BooleanSetting("Свечение",     true);
    private final SliderSetting glowIntensity = new SliderSetting("Сила свечения", 1.5f, 0.5f, 4.0f, 0.1f);
    private final SliderSetting glowLayers    = new SliderSetting("Слои свечения", 3.0f, 1.0f, 6.0f, 1.0f);

    @Subscribe
    public void onRender(EventWorldRender event) {
        if (mc.world == null || mc.player == null) return;

        float tickDelta = event.getTickDelta();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        int color = ColorProvider.getColorClient();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof AbstractClientPlayerEntity player)) continue;
            if (!player.isAlive()) continue;
            if (player == mc.player && mc.options.getPerspective() == Perspective.FIRST_PERSON) continue;

            renderChams(event.getMatrixStack(), player, cam, tickDelta, color);
        }
    }

    private void renderChams(MatrixStack stack, AbstractClientPlayerEntity player,
                             Vec3d cam, float tickDelta, int color) {
        float a = alpha.getFloatValue();
        float bright = brightness.getFloatValue();
        float r = ((color >> 16) & 0xFF) / 255f * bright;
        float g = ((color >> 8)  & 0xFF) / 255f * bright;
        float b = (color & 0xFF)          / 255f * bright;

        double x = player.prevX + (player.getX() - player.prevX) * tickDelta;
        double y = player.prevY + (player.getY() - player.prevY) * tickDelta;
        double z = player.prevZ + (player.getZ() - player.prevZ) * tickDelta;

        float bodyYaw   = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float headYaw   = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
        float pitch     = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        float netHeadYaw = MathHelper.wrapDegrees(headYaw - bodyYaw);

        float limbPos   = player.limbAnimator.getPos(tickDelta);
        float limbSpeed = player.limbAnimator.getSpeed(tickDelta);
        float swing     = MathHelper.sin(limbPos * 0.6662f) * 0.6f * limbSpeed;

        float swingProgress = player.getHandSwingProgress(tickDelta);
        boolean mainRight   = player.getMainArm() == net.minecraft.util.Arm.RIGHT;
        net.minecraft.util.Hand swingHand = player.preferredHand;
        float swingAngle  = -(float)(Math.sin(Math.sqrt(swingProgress) * Math.PI) * 1.2f);
        float rightSwingX = (swingHand == net.minecraft.util.Hand.MAIN_HAND) == mainRight ? swingAngle : 0f;
        float leftSwingX  = (swingHand == net.minecraft.util.Hand.MAIN_HAND) != mainRight ? swingAngle : 0f;

        boolean slim   = player.getSkinTextures().model() == net.minecraft.client.util.SkinTextures.Model.SLIM;
        boolean sneak  = player.isInSneakingPose();
        boolean elytra = player.isGliding();
        boolean swim   = player.isSwimming();

        stack.push();
        stack.translate(x - cam.x, y - cam.y, z - cam.z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));

        if (elytra) {
            float ticks  = player.getGlidingTicks() + tickDelta;
            float factor = MathHelper.clamp(ticks * ticks / 100f, 0f, 1f);
            if (!player.isUsingRiptide())
                stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(factor * (-90f - pitch)));
        } else if (swim) {
            float swimPitch = player.isSubmergedInWater() ? -90f - pitch : -90f;
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swimPitch));
            stack.translate(0, -1.0, 0.3);
        }

        stack.scale(-1f, -1f, 1f);
        stack.scale(0.9375f, 0.9375f, 0.9375f);
        stack.translate(0, -1.501, 0);

        if (sneak) stack.translate(0, 0.2, 0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (throughWalls.getValue()) RenderSystem.disableDepthTest();

        float idleTime = (player.age + tickDelta) * 0.05f;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        drawBody(stack, buf, swing, rightSwingX, leftSwingX, netHeadYaw, pitch, slim, sneak, swim, limbPos, limbSpeed, idleTime, r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buf.end());

        float lw = lineWidth.getFloatValue();
        float la = MathHelper.clamp(a + 0.2f, 0f, 1f);
        float lr = MathHelper.clamp(r + 0.15f, 0f, 1f);
        float lg = MathHelper.clamp(g + 0.15f, 0f, 1f);
        float lb = MathHelper.clamp(b + 0.15f, 0f, 1f);

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(lw);

        if (glow.getValue()) {
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                    GlStateManager.SrcFactor.ONE,       GlStateManager.DstFactor.ZERO);
            int layers = Math.max(1, glowLayers.getIntValue());
            float intensity = glowIntensity.getFloatValue();
            for (int i = layers; i >= 1; i--) {
                float expand = i * 0.5f * intensity;
                float glowA  = MathHelper.clamp(la * (1f / (i + 1)) * 0.7f, 0f, 1f);
                buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                drawBodyLines(stack, buf, swing, rightSwingX, leftSwingX, netHeadYaw, pitch, slim, sneak, swim, limbPos, limbSpeed, idleTime, lr, lg, lb, glowA, expand);
                BufferRenderer.drawWithGlobalProgram(buf.end());
            }
            RenderSystem.defaultBlendFunc();
        }

        buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        drawBodyLines(stack, buf, swing, rightSwingX, leftSwingX, netHeadYaw, pitch, slim, sneak, swim, limbPos, limbSpeed, idleTime, lr, lg, lb, la, 0f);
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        if (throughWalls.getValue()) RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        stack.pop();
    }

    private void drawBody(MatrixStack m, BufferBuilder buf, float swing,
                          float rightSwingX, float leftSwingX,
                          float headYaw, float headPitch,
                          boolean slim, boolean sneak, boolean swim,
                          float limbPos, float limbSpeed, float idleTime,
                          float r, float g, float b, float a) {
        float u = 1f / 16f;
        float armW = slim ? 3 : 4;
        float armSwayZ = MathHelper.sin(idleTime) * 0.04f + 0.03f * limbSpeed;


        float swimPhase = limbPos * 0.6662f;
        float swimCycle = MathHelper.sin(swimPhase) * limbSpeed;
        float swimKick  = swim ? swimCycle * 0.4f : 0f;


        m.push();
        if (sneak) {
            m.translate(0, 12*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.64f));
            m.translate(0, -12*u, 0);
        }
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(headYaw));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(headPitch));
        box(buf, m.peek().getPositionMatrix(), -4*u, -8*u, -4*u, 8*u, 8*u, 8*u, r, g, b, a);
        m.pop();


        m.push();
        if (sneak) {
            m.translate(0, 12*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.64f));
            m.translate(0, -12*u, 0);
        }
        if (swim) {

            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(limbPos * 0.3331f) * 3f * limbSpeed));
        }
        box(buf, m.peek().getPositionMatrix(), -4*u, 0, -2*u, 8*u, 12*u, 4*u, r, g, b, a);
        m.pop();


        float swimArmX = swim ? swimCycle * 0.6f - (float)(Math.PI / 2f) : 0f;
        float rightArmX = swim ? swimArmX : swing;
        float leftArmX  = swim ? swimArmX : -swing;

        float swimSpread = swim ? MathHelper.clamp(swimCycle, 0f, 1f) * (float)(Math.PI / 4f) : 0f;
        float rightArmZ = swim ?  swimSpread : 0f;
        float leftArmZ  = swim ? -swimSpread : 0f;


        m.push();
        if (sneak) {
            m.translate(0, 12*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.64f));
            m.translate(0, -12*u, 0);
        }
        m.translate(-4*u, 0, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotation(rightArmX + (swim ? 0 : rightSwingX)));
        m.multiply(RotationAxis.POSITIVE_Z.rotation(swim ? rightArmZ : armSwayZ));
        box(buf, m.peek().getPositionMatrix(), -armW*u, 0, -2*u, armW*u, 12*u, 4*u, r, g, b, a);
        m.pop();


        m.push();
        if (sneak) {
            m.translate(0, 12*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.64f));
            m.translate(0, -12*u, 0);
        }
        m.translate(4*u, 0, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotation(leftArmX + (swim ? 0 : leftSwingX)));
        m.multiply(RotationAxis.POSITIVE_Z.rotation(swim ? leftArmZ : -armSwayZ));
        box(buf, m.peek().getPositionMatrix(), 0, 0, -2*u, armW*u, 12*u, 4*u, r, g, b, a);
        m.pop();


        m.push();
        m.translate(-2*u, 12*u, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotation(swim ? -swimKick : -swing));
        box(buf, m.peek().getPositionMatrix(), -2*u, 0, -2*u, 4*u, 12*u, 4*u, r, g, b, a);
        m.pop();


        m.push();
        m.translate(2*u, 12*u, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotation(swim ? swimKick : swing));
        box(buf, m.peek().getPositionMatrix(), -2*u, 0, -2*u, 4*u, 12*u, 4*u, r, g, b, a);
        m.pop();
    }

    private void drawBodyLines(MatrixStack m, BufferBuilder buf, float swing,
                               float rightSwingX, float leftSwingX,
                               float headYaw, float headPitch,
                               boolean slim, boolean sneak, boolean swim,
                               float limbPos, float limbSpeed, float idleTime,
                               float r, float g, float b, float a, float expand) {
        float u = 1f / 16f;
        float armW = slim ? 3 : 4;
        float armSwayZ = MathHelper.sin(idleTime) * 0.04f + 0.03f * limbSpeed;

        float swimPhase = limbPos * 0.6662f;
        float swimCycle = MathHelper.sin(swimPhase) * limbSpeed;
        float swimArmX = swim ? swimCycle * 0.6f - (float)(Math.PI / 2f) : 0f;
        float rightArmX = swim ? swimArmX : swing;
        float leftArmX  = swim ? swimArmX : -swing;
        float swimSpread = swim ? MathHelper.clamp(swimCycle, 0f, 1f) * (float)(Math.PI / 4f) : 0f;
        float rightArmZ = swim ?  swimSpread : 0f;
        float leftArmZ  = swim ? -swimSpread : 0f;
        float swimKick = swim ? swimCycle * 0.4f : 0f;
        float ex = expand * u;


        m.push();
        if (sneak) {
            m.translate(0, 12*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.64f));
            m.translate(0, -12*u, 0);
        }
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(headYaw));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(headPitch));
        boxLines(buf, m.peek().getPositionMatrix(), -4*u-ex, -8*u-ex, -4*u-ex, 8*u+ex*2, 8*u+ex*2, 8*u+ex*2, r, g, b, a);
        m.pop();


        m.push();
        if (sneak) {
            m.translate(0, 12*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.64f));
            m.translate(0, -12*u, 0);
        }
        if (swim) {
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(limbPos * 0.3331f) * 3f * limbSpeed));
        }
        boxLines(buf, m.peek().getPositionMatrix(), -4*u-ex, 0-ex, -2*u-ex, 8*u+ex*2, 12*u+ex*2, 4*u+ex*2, r, g, b, a);
        m.pop();

        m.push();
        if (sneak) {
            m.translate(0, 12*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.64f));
            m.translate(0, -12*u, 0);
        }
        m.translate(-4*u, 0, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotation(rightArmX + (swim ? 0 : rightSwingX)));
        m.multiply(RotationAxis.POSITIVE_Z.rotation(swim ? rightArmZ : armSwayZ));
        boxLines(buf, m.peek().getPositionMatrix(), -armW*u-ex, 0-ex, -2*u-ex, armW*u+ex*2, 12*u+ex*2, 4*u+ex*2, r, g, b, a);
        m.pop();


        m.push();
        if (sneak) {
            m.translate(0, 12*u, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.64f));
            m.translate(0, -12*u, 0);
        }
        m.translate(4*u, 0, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotation(leftArmX + (swim ? 0 : leftSwingX)));
        m.multiply(RotationAxis.POSITIVE_Z.rotation(swim ? leftArmZ : -armSwayZ));
        boxLines(buf, m.peek().getPositionMatrix(), 0-ex, 0-ex, -2*u-ex, armW*u+ex*2, 12*u+ex*2, 4*u+ex*2, r, g, b, a);
        m.pop();


        m.push();
        m.translate(-2*u, 12*u, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotation(swim ? -swimKick : -swing));
        boxLines(buf, m.peek().getPositionMatrix(), -2*u-ex, 0-ex, -2*u-ex, 4*u+ex*2, 12*u+ex*2, 4*u+ex*2, r, g, b, a);
        m.pop();


        m.push();
        m.translate(2*u, 12*u, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotation(swim ? swimKick : swing));
        boxLines(buf, m.peek().getPositionMatrix(), -2*u-ex, 0-ex, -2*u-ex, 4*u+ex*2, 12*u+ex*2, 4*u+ex*2, r, g, b, a);
        m.pop();
    }

    private void box(BufferBuilder b, Matrix4f m,
                     float x, float y, float z, float sx, float sy, float sz,
                     float r, float g, float bl, float a) {
        float x2 = x+sx, y2 = y+sy, z2 = z+sz;
        b.vertex(m, x,  y,  z2).color(r,g,bl,a); b.vertex(m, x2, y,  z2).color(r,g,bl,a);
        b.vertex(m, x2, y2, z2).color(r,g,bl,a); b.vertex(m, x,  y2, z2).color(r,g,bl,a);
        b.vertex(m, x2, y,  z ).color(r,g,bl,a); b.vertex(m, x,  y,  z ).color(r,g,bl,a);
        b.vertex(m, x,  y2, z ).color(r,g,bl,a); b.vertex(m, x2, y2, z ).color(r,g,bl,a);
        b.vertex(m, x,  y,  z ).color(r,g,bl,a); b.vertex(m, x,  y,  z2).color(r,g,bl,a);
        b.vertex(m, x,  y2, z2).color(r,g,bl,a); b.vertex(m, x,  y2, z ).color(r,g,bl,a);
        b.vertex(m, x2, y,  z2).color(r,g,bl,a); b.vertex(m, x2, y,  z ).color(r,g,bl,a);
        b.vertex(m, x2, y2, z ).color(r,g,bl,a); b.vertex(m, x2, y2, z2).color(r,g,bl,a);
        b.vertex(m, x,  y2, z2).color(r,g,bl,a); b.vertex(m, x2, y2, z2).color(r,g,bl,a);
        b.vertex(m, x2, y2, z ).color(r,g,bl,a); b.vertex(m, x,  y2, z ).color(r,g,bl,a);
        b.vertex(m, x,  y,  z ).color(r,g,bl,a); b.vertex(m, x2, y,  z ).color(r,g,bl,a);
        b.vertex(m, x2, y,  z2).color(r,g,bl,a); b.vertex(m, x,  y,  z2).color(r,g,bl,a);
    }

    private void boxLines(BufferBuilder b, Matrix4f m,
                          float x, float y, float z, float sx, float sy, float sz,
                          float r, float g, float bl, float a) {
        float x2 = x+sx, y2 = y+sy, z2 = z+sz;
        line(b,m, x,y,z,    x2,y,z,   r,g,bl,a); line(b,m, x2,y,z,   x2,y2,z,  r,g,bl,a);
        line(b,m, x2,y2,z,  x,y2,z,   r,g,bl,a); line(b,m, x,y2,z,   x,y,z,    r,g,bl,a);
        line(b,m, x,y,z2,   x2,y,z2,  r,g,bl,a); line(b,m, x2,y,z2,  x2,y2,z2, r,g,bl,a);
        line(b,m, x2,y2,z2, x,y2,z2,  r,g,bl,a); line(b,m, x,y2,z2,  x,y,z2,   r,g,bl,a);
        line(b,m, x,y,z,    x,y,z2,   r,g,bl,a); line(b,m, x2,y,z,   x2,y,z2,  r,g,bl,a);
        line(b,m, x2,y2,z,  x2,y2,z2, r,g,bl,a); line(b,m, x,y2,z,   x,y2,z2,  r,g,bl,a);
    }

    private void line(BufferBuilder b, Matrix4f m,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float r, float g, float bl, float a) {
        float dx = x2-x1, dy = y2-y1, dz = z2-z1;
        float len = (float) Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (len == 0) len = 1;
        b.vertex(m, x1,y1,z1).color(r,g,bl,a).normal(dx/len,dy/len,dz/len);
        b.vertex(m, x2,y2,z2).color(r,g,bl,a).normal(dx/len,dy/len,dz/len);
    }
}

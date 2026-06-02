package endless.ere.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import net.minecraft.client.gl.ShaderProgramKeys;

import endless.ere.Endless;
import endless.ere.base.events.impl.render.EventRender3D;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.ColorSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.impl.combat.Aura;
import endless.ere.utility.render.display.base.color.ColorRGBA;

@ModuleAnnotation(name = "TargetESP", description = "Подсветка цели", category = Category.RENDER)
public class TargetESP extends Module {

    public static final TargetESP INSTANCE = new TargetESP();

    public final ModeSetting mode = new ModeSetting("Режим", "Красивый", "Свечение");
    public final ColorSetting color = new ColorSetting("Цвет", new ColorRGBA(255, 0, 0, 255));
    public final endless.ere.client.modules.api.setting.impl.NumberSetting kolcoSpeed =
            new endless.ere.client.modules.api.setting.impl.NumberSetting("Скорость кольца", 0.05f, 0.01f, 0.30f, 0.01f,
                    () -> mode.is("Красивый"));
    public final endless.ere.client.modules.api.setting.impl.BooleanSetting rainbow =
            new endless.ere.client.modules.api.setting.impl.BooleanSetting("Радуга", true,
                    () -> mode.is("Красивый"));
    public final endless.ere.client.modules.api.setting.impl.BooleanSetting damageRed =
            new endless.ere.client.modules.api.setting.impl.BooleanSetting("Красный при уроне", true,
                    () -> mode.is("Красивый"));
    public final endless.ere.client.modules.api.setting.impl.BooleanSetting throughWalls =
            new endless.ere.client.modules.api.setting.impl.BooleanSetting("Через стены", false,
                    () -> mode.is("Красивый"));

    private float animationProgress = 0.0f;
    private long lastTime = System.currentTimeMillis();
    private long lastKolcoUpdateTime = 0L;
    private double kolcoStep = 0.0;
    private float kolcoAnimProgress = 0.0f;
    private long lastDamageTime = 0L;
    private float damageFlashIntensity = 0.0f;
    private float prevHealth = -1f;

    @EventTarget
    public void onRender3D(EventRender3D event) {
        LivingEntity target = Aura.INSTANCE.getTarget();
        if (target == null) {
            animationProgress = 0.0f;
            kolcoAnimProgress = Math.max(0.0f, kolcoAnimProgress - 0.05f);
            prevHealth = -1f;
            return;
        }

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastTime) / 1000.0f;
        lastTime = currentTime;

        if (mode.is("Свечение")) {
            renderGlow(target, event.getPartialTicks(), event.getMatrix());
        } else if (mode.is("Красивый")) {
            renderBeautiful(target, event.getPartialTicks(), deltaTime, event.getMatrix());
        }
    }

    @Override
    public void onEnable() {
        lastTime = System.currentTimeMillis();
        super.onEnable();
    }

    private void renderGlow(LivingEntity entity, float tickDelta, MatrixStack matrices) {
        Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());
        double x = net.minecraft.util.math.MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double y = net.minecraft.util.math.MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double z = net.minecraft.util.math.MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();

        matrices.push();
        matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int c = color.getIntColor();
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        int a = 100;

        drawBox(bufferBuilder, matrix, box, r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private void renderBeautiful(LivingEntity entity, float tickDelta, float deltaTime, MatrixStack matrices) {
        // Проверка через стены
        if (!throughWalls.isEnabled() && mc.player != null && !mc.player.canSee(entity)) {
            return;
        }

        // Damage detect
        float curHealth = entity.getHealth();
        if (prevHealth > 0 && curHealth < prevHealth) {
            lastDamageTime = System.currentTimeMillis();
        }
        prevHealth = curHealth;

        // Анимация появления
        kolcoAnimProgress = Math.min(1.0f, kolcoAnimProgress + 0.1f);

        long now = System.currentTimeMillis();
        if (lastKolcoUpdateTime == 0L) lastKolcoUpdateTime = now;
        float dt = Math.min((now - lastKolcoUpdateTime) / 1000.0f, 0.1f);
        lastKolcoUpdateTime = now;
        kolcoStep += kolcoSpeed.getCurrent() * dt * 60.0;

        double tx = net.minecraft.util.math.MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double ty = net.minecraft.util.math.MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double tz = net.minecraft.util.math.MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();
        double rx = tx - camPos.x;
        double ry = ty - camPos.y;
        double rz = tz - camPos.z;

        float entityWidth = entity.getWidth() * 0.9f;
        float entityHeight = entity.getHeight();
        float eased = easeOutCubic(kolcoAnimProgress);

        // Параметры тора (объёмное кольцо)
        float ringMajor = entityWidth;        // радиус кольца
        float ringMinor = entityWidth * 0.10f;// толщина (сечение трубки)
        // Y скачет волной по высоте
        double golovkaY = absSin(kolcoStep) * entityHeight;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tess = Tessellator.getInstance();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Внешнее свечение - тор большего сечения, тусклый
        drawTorus(tess, matrix, rx, ry + golovkaY, rz, ringMajor, ringMinor * 2.6f,
                72, 14, (int)(60 * eased), now, true);
        // Средний halo
        drawTorus(tess, matrix, rx, ry + golovkaY, rz, ringMajor, ringMinor * 1.7f,
                72, 14, (int)(110 * eased), now, true);
        // Основной плотный тор
        drawTorus(tess, matrix, rx, ry + golovkaY, rz, ringMajor, ringMinor,
                72, 16, (int)(230 * eased), now, false);

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Рендер 3D-тора (объёмное кольцо). majorR — радиус кольца, minorR — радиус трубки.
     * @param fadeEdges если true, альфа спадает к "полюсам" сечения для эффекта свечения.
     */
    private void drawTorus(Tessellator tess, Matrix4f matrix, double cx, double cy, double cz,
                           float majorR, float minorR, int majorSegs, int minorSegs,
                           int baseAlpha, long now, boolean fadeEdges) {
        for (int j = 0; j < minorSegs; j++) {
            double v1 = (double) j / minorSegs;
            double v2 = (double) (j + 1) / minorSegs;
            double phi1 = Math.PI * 2 * v1;
            double phi2 = Math.PI * 2 * v2;
            float cosPhi1 = (float) Math.cos(phi1);
            float sinPhi1 = (float) Math.sin(phi1);
            float cosPhi2 = (float) Math.cos(phi2);
            float sinPhi2 = (float) Math.sin(phi2);

            // Модулировать альфу по сечению (1 на боку, ~0 на верх/низ если fadeEdges)
            float aMul1 = fadeEdges ? Math.abs(cosPhi1) : 1.0f;
            float aMul2 = fadeEdges ? Math.abs(cosPhi2) : 1.0f;
            // Подсветка верха для объёма (легче в +y направлении)
            float light1 = 0.75f + 0.25f * Math.max(0, sinPhi1);
            float light2 = 0.75f + 0.25f * Math.max(0, sinPhi2);

            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int i = 0; i <= majorSegs; i++) {
                double u = (double) i / majorSegs;
                double theta = Math.PI * 2 * u;
                float cosT = (float) Math.cos(theta);
                float sinT = (float) Math.sin(theta);

                int colorRGB = getKolcoColor(i * (360 / majorSegs), now);
                int rr = (colorRGB >> 16) & 0xFF;
                int gg = (colorRGB >> 8) & 0xFF;
                int bb = colorRGB & 0xFF;

                int rr1 = Math.min(255, (int)(rr * light1));
                int gg1 = Math.min(255, (int)(gg * light1));
                int bb1 = Math.min(255, (int)(bb * light1));
                int rr2 = Math.min(255, (int)(rr * light2));
                int gg2 = Math.min(255, (int)(gg * light2));
                int bb2 = Math.min(255, (int)(bb * light2));

                int a1 = (int) (baseAlpha * aMul1);
                int a2 = (int) (baseAlpha * aMul2);

                // Точка на торе: ((R + r*cos(phi)) * cos(theta), r*sin(phi), (R + r*cos(phi)) * sin(theta))
                float r1 = majorR + minorR * cosPhi1;
                float r2 = majorR + minorR * cosPhi2;
                float x1 = (float) cx + cosT * r1;
                float y1 = (float) cy + minorR * sinPhi1;
                float z1 = (float) cz + sinT * r1;
                float x2 = (float) cx + cosT * r2;
                float y2 = (float) cy + minorR * sinPhi2;
                float z2 = (float) cz + sinT * r2;

                buf.vertex(matrix, x1, y1, z1).color(rr1, gg1, bb1, a1);
                buf.vertex(matrix, x2, y2, z2).color(rr2, gg2, bb2, a2);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
    }

    private double absSin(double input) {
        return Math.abs(1.0 + Math.sin(input)) / 2.0;
    }

    private int getKolcoColor(int offsetAngle, long now) {
        int colorRGB;
        if (rainbow.isEnabled()) {
            float timeFactor = (now % 3000L) / 3000.0f;
            int hue = (int) (timeFactor * 360.0f) + offsetAngle;
            colorRGB = java.awt.Color.HSBtoRGB((hue % 360) / 360.0f, 0.7f, 1.0f);
        } else {
            colorRGB = color.getIntColor();
        }
        return applyDamageFlash(colorRGB);
    }

    private int applyDamageFlash(int colorRGB) {
        if (!damageRed.isEnabled()) return colorRGB;
        long timeSince = System.currentTimeMillis() - lastDamageTime;
        float target = 0.0f;
        if (timeSince < 500L) {
            target = 1.0f - easeOutCubic(timeSince / 500.0f);
        }
        damageFlashIntensity = net.minecraft.util.math.MathHelper.lerp(0.1f, damageFlashIntensity, target);
        if (damageFlashIntensity < 0.05f) return colorRGB;
        int rr = (colorRGB >> 16) & 0xFF;
        int gg = (colorRGB >> 8) & 0xFF;
        int bb = colorRGB & 0xFF;
        rr = (int) net.minecraft.util.math.MathHelper.lerp(damageFlashIntensity, rr, 255);
        gg = (int) net.minecraft.util.math.MathHelper.lerp(damageFlashIntensity, gg, 50);
        bb = (int) net.minecraft.util.math.MathHelper.lerp(damageFlashIntensity, bb, 50);
        return (rr << 16) | (gg << 8) | bb;
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    private void drawBox(BufferBuilder bufferBuilder, Matrix4f matrix, Box box, int r, int g, int b, int a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        bufferBuilder.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(r, g, b, 0);
        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(r, g, b, 0);

        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(r, g, b, 0);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, 0);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);

        bufferBuilder.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(r, g, b, 0);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(r, g, b, 0);
        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);

        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, 0);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(r, g, b, 0);
    }

    private void drawBeautifulLines(BufferBuilder bufferBuilder, Matrix4f matrix, Box box, double currentY, int r, int g, int b, int a) {
        float minX = (float) box.minX;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxZ = (float) box.maxZ;
        float y = (float) currentY;

        bufferBuilder.vertex(matrix, minX, y, minZ).color(r, g, b, a);
        bufferBuilder.vertex(matrix, maxX, y, minZ).color(r, g, b, a);

        bufferBuilder.vertex(matrix, maxX, y, minZ).color(r, g, b, a);
        bufferBuilder.vertex(matrix, maxX, y, maxZ).color(r, g, b, a);

        bufferBuilder.vertex(matrix, maxX, y, maxZ).color(r, g, b, a);
        bufferBuilder.vertex(matrix, minX, y, maxZ).color(r, g, b, a);

        bufferBuilder.vertex(matrix, minX, y, maxZ).color(r, g, b, a);
        bufferBuilder.vertex(matrix, minX, y, minZ).color(r, g, b, a);
    }
}

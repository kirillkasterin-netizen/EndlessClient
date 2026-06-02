package dev.endless.module.list.misc;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.math.Vector2f;
import org.joml.Matrix4f;
import dev.endless.event.list.EventHUD;
import dev.endless.event.list.EventKeyInput;
import dev.endless.event.list.EventTick;
import dev.endless.event.list.EventWorldRender;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.list.render.hud.Interface;
import dev.endless.module.settings.BindSetting;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.util.base.Instance;
import dev.endless.util.render.math.ProjectionUtil;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Warden Helper — помощник на анархиях с Warden-сундуками (1.21.4+).
 *
 * Что делает:
 *  - подсвечивает сундуки в зоне Warden'а (центр 2000/2000, радиус 250 блоков);
 *  - читает таймер с armorstand'а над сундуком (формат "MM:SS" / "Xс" / "X сек");
 *  - меняет цвет ESP в зависимости от оставшегося времени:
 *      серый — нет таймера, красный — больше 2 минут, жёлтый — менее 2 мин,
 *      зелёный пульс — готов;
 *  - авто GPS: ставит метку на готовый/почти готовый сундук командой "/gps set X Z";
 *  - уведомления: спамит "Сундук готов!" / "Сундук через N сек!" в нотификации;
 *  - бинд "ресет сундуков" чистит кэш (например при смене сервера).
 *
 * Адаптировано из astralis WardenHelper под инфраструктуру Wraith.
 */
@ModuleInformation(moduleName = "Warden Helper",
        moduleDesc = "Помощник для Warden-анарх 1.21.4+ (ESP, авто GPS, нотификации)",
        moduleCategory = ModuleCategory.MISC)
public class WardenHelper extends Module {

    private final BooleanSetting autoGPS = new BooleanSetting(
            "Авто GPS", true);
    private final BooleanSetting notifications = new BooleanSetting(
            "Уведомления", true);
    private final BindSetting resetBind = new BindSetting("Ресет сундуков", -1);

    /** Состояние сундука: позиция, есть ли таймер, время до открытия. */
    private static final class ChestData {
        boolean hasTimer = false;
        private long timerEndMs = 0L;

        void setTimer(long secondsLeft) {
            this.timerEndMs = System.currentTimeMillis() + secondsLeft * 1000L;
            this.hasTimer = true;
        }

        float getTimeLeft() {
            return (timerEndMs - System.currentTimeMillis()) / 1000f;
        }
    }

    private final Map<BlockPos, ChestData> chestDataMap = new ConcurrentHashMap<>();
    private final Set<BlockPos> gpsVisited = new HashSet<>();
    private final Set<BlockPos> notified = new HashSet<>();
    private BlockPos currentGPSTarget = null;

    /** Координаты центра Warden-зоны на анархии. */
    private static final double WARDEN_X = 2000.0;
    private static final double WARDEN_Z = 2000.0;
    private static final double ZONE_RADIUS = 250.0;

    // Цвета ESP (RGB только, alpha задаётся отдельно).
    private static final int CR_INACTIVE_R = 180, CR_INACTIVE_G = 180, CR_INACTIVE_B = 180;
    private static final int CR_DEFAULT_R = 255, CR_DEFAULT_G = 60,  CR_DEFAULT_B  = 60;
    private static final int CR_WARN_R    = 255, CR_WARN_G    = 220, CR_WARN_B     = 50;
    private static final int CR_READY_R   = 60,  CR_READY_G   = 255, CR_READY_B    = 60;

    // Формат таймера в имени armorstand'а: "MM:SS", "MM:SS:SS" или "Xс".
    private static final Pattern TIME_PATTERN    = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern SECONDS_PATTERN = Pattern.compile("(\\d+)\\s*(с|s|сек|sec)");

    @Override
    public void onDisable() {
        clearAll();
        super.onDisable();
    }

    @Subscribe
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) return;
        if (!isInWardenZone()) return;

        // Сканируем сундуки вокруг игрока, если их ещё нет в кэше.
        scanNearbyChests();
        updateTimersFromEntities();
        updateGPSAndNotifications();
    }

    @Subscribe
    public void onKey(EventKeyInput event) {
        if (event.getAction() != 1) return;
        int key = event.getKey();
        if (resetBind.getValue() != -1 && key == resetBind.getValue()) {
            clearAll();
            postNotify("Сундуки сброшены!");
        }
    }

    @Subscribe
    public void onWorldRender(EventWorldRender event) {
        if (mc.world == null || mc.player == null) return;
        if (chestDataMap.isEmpty()) return;

        MatrixStack stack = event.getMatrixStack();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        // ── Filled box (заливка) ───────────────────────────────────────────
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = stack.peek().getPositionMatrix();

        for (Map.Entry<BlockPos, ChestData> entry : chestDataMap.entrySet()) {
            BlockPos pos = entry.getKey();
            ChestData data = entry.getValue();
            int[] rgb = computeColorRGB(data);
            int alphaFill = 50;

            float x1 = (float) (pos.getX() - cam.x);
            float y1 = (float) (pos.getY() - cam.y);
            float z1 = (float) (pos.getZ() - cam.z);
            float x2 = x1 + 1f, y2 = y1 + 1f, z2 = z1 + 1f;

            drawFilledBox(matrix, buffer, x1, y1, z1, x2, y2, z2,
                    rgb[0], rgb[1], rgb[2], alphaFill);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        // ── Outlined box (контур) ──────────────────────────────────────────
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        BufferBuilder lineBuf = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.LINES, VertexFormats.LINES);

        for (Map.Entry<BlockPos, ChestData> entry : chestDataMap.entrySet()) {
            BlockPos pos = entry.getKey();
            ChestData data = entry.getValue();
            int[] rgb = computeColorRGB(data);
            int alphaLine = 200;

            float x1 = (float) (pos.getX() - cam.x);
            float y1 = (float) (pos.getY() - cam.y);
            float z1 = (float) (pos.getZ() - cam.z);
            float x2 = x1 + 1f, y2 = y1 + 1f, z2 = z1 + 1f;

            drawBoxOutline(stack, lineBuf, x1, y1, z1, x2, y2, z2,
                    rgb[0], rgb[1], rgb[2], alphaLine);
        }
        BufferRenderer.drawWithGlobalProgram(lineBuf.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Рисует таймер сундука на HUD через стены (как Tags).
     * Используется ProjectionUtil для перевода мирной точки сундука в экранные
     * координаты, и текст рисуется HUD-слоем поверх всего мира.
     */
    @Subscribe
    public void onHud(EventHUD event) {
        if (mc.world == null || mc.player == null) return;
        if (chestDataMap.isEmpty()) return;

        for (Map.Entry<BlockPos, ChestData> entry : chestDataMap.entrySet()) {
            BlockPos pos = entry.getKey();
            ChestData data = entry.getValue();

            // Точка над сундуком (центр + 1.6 блока вверх для тага).
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 1.6;
            double cz = pos.getZ() + 0.5;

            Vector2f screen = ProjectionUtil.project(cx, cy, cz);
            if (screen.getX() == Float.MAX_VALUE || screen.getY() == Float.MAX_VALUE) continue;

            // Текст таймера (или "?" если таймер не считан).
            String text;
            if (!data.hasTimer) {
                text = "?";
            } else {
                float t = data.getTimeLeft();
                if (t <= 0f) {
                    text = "ГОТОВ";
                } else {
                    int sec = (int) t;
                    int mm = sec / 60;
                    int ss = sec % 60;
                    text = mm > 0
                            ? String.format("%d:%02d", mm, ss)
                            : sec + "с";
                }
            }

            int[] rgb = computeColorRGB(data);
            int color = ColorProvider.rgba(rgb[0], rgb[1], rgb[2], 255);

            // Размер шрифта зависит от расстояния до сундука (чем дальше — тем мельче).
            double dist = mc.player.getEyePos().distanceTo(new Vec3d(cx, cy, cz));
            float fontSize = (float) Math.max(6f, 12f - dist * 0.05f);

            float textWidth = Fonts.SFMEDIUM.get().getWidth(text, fontSize);
            float bgX = screen.getX() - textWidth / 2f - 3f;
            float bgY = screen.getY() - fontSize / 2f - 2f;
            float bgW = textWidth + 6f;
            float bgH = fontSize + 4f;

            // Подложка
            DrawUtil.drawRound(bgX, bgY, bgW, bgH, 2f, ColorProvider.rgba(0, 0, 0, 160));

            // Текст
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), text,
                    screen.getX() - textWidth / 2f,
                    bgY + 2.5f,
                    color, fontSize);
        }
    }

    // ── Логика ────────────────────────────────────────────────────────────

    private void clearAll() {
        chestDataMap.clear();
        gpsVisited.clear();
        notified.clear();
        currentGPSTarget = null;
    }

    private boolean isInWardenZone() {
        return Math.abs(mc.player.getX() - WARDEN_X) <= ZONE_RADIUS
                && Math.abs(mc.player.getZ() - WARDEN_Z) <= ZONE_RADIUS;
    }

    /**
     * Сканирует block entities в радиусе ~64 блока вокруг игрока и заполняет
     * chestDataMap новыми сундуками. Существующие записи не перезаписываются.
     */
    private void scanNearbyChests() {
        BlockPos playerPos = mc.player.getBlockPos();
        int range = 64;
        for (int dx = -range; dx <= range; dx += 4) {
            for (int dz = -range; dz <= range; dz += 4) {
                for (int dy = -16; dy <= 16; dy += 4) {
                    BlockPos chunkPos = playerPos.add(dx, dy, dz);
                    BlockEntity be = mc.world.getBlockEntity(chunkPos);
                    if (be != null && isWardenChest(be)) {
                        chestDataMap.computeIfAbsent(be.getPos(), p -> new ChestData());
                    }
                }
            }
        }
    }

    private boolean isWardenChest(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity;
    }

    /**
     * Ищет ArmorStand'ы рядом с каждым известным сундуком и парсит их имя как
     * таймер. Если имя не парсится / стенда нет — помечаем сундук как "без таймера".
     */
    private void updateTimersFromEntities() {
        for (Map.Entry<BlockPos, ChestData> entry : chestDataMap.entrySet()) {
            BlockPos pos = entry.getKey();
            ChestData data = entry.getValue();
            boolean found = false;
            Box searchBox = new Box(pos).expand(1.0, 3.0, 1.0);

            for (ArmorStandEntity stand : mc.world.getEntitiesByClass(
                    ArmorStandEntity.class, searchBox,
                    s -> s.hasCustomName() && s.getCustomName() != null)) {
                String name = stand.getCustomName().getString().replaceAll("§[0-9a-fk-or]", "");
                long seconds = parseTimeToSeconds(name);
                if (seconds >= 0) {
                    data.setTimer(seconds);
                    found = true;
                    break;
                }
            }
            if (!found) data.hasTimer = false;
        }
    }

    /** Парсит строку "MM:SS" / "MM:SS:SS" / "Xс" / просто число — в секунды. */
    private long parseTimeToSeconds(String str) {
        Matcher m1 = TIME_PATTERN.matcher(str);
        if (m1.find()) {
            if (m1.group(3) == null) {
                return (long) Integer.parseInt(m1.group(1)) * 60
                        + Integer.parseInt(m1.group(2));
            }
            return (long) Integer.parseInt(m1.group(1)) * 3600
                    + (long) Integer.parseInt(m1.group(2)) * 60
                    + Integer.parseInt(m1.group(3));
        }
        Matcher m2 = SECONDS_PATTERN.matcher(str);
        if (m2.find()) return Long.parseLong(m2.group(1));

        String digits = str.replaceAll("[^0-9]", "").trim();
        if (digits.isEmpty()) return -1L;
        try {
            long val = Long.parseLong(digits);
            return (val > 0 && val < 36000) ? val : -1L;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private void updateGPSAndNotifications() {
        // Если мы уже близко к текущей gps-цели, выключаем gps.
        if (currentGPSTarget != null
                && mc.player.squaredDistanceTo(Vec3d.ofCenter(currentGPSTarget)) <= 100.0) {
            sendCommand("gps off");
            currentGPSTarget = null;
        }

        for (Map.Entry<BlockPos, ChestData> entry : chestDataMap.entrySet()) {
            BlockPos pos = entry.getKey();
            ChestData data = entry.getValue();
            if (!data.hasTimer) continue;

            float t = data.getTimeLeft();
            if (t <= 0f) {
                if (autoGPS.getValue() && !gpsVisited.contains(pos)) sendGPS(pos);
                if (notifications.getValue() && !notified.contains(pos)) {
                    postNotify("Сундук готов! [" + pos.getX() + " " + pos.getZ() + "]");
                    notified.add(pos);
                }
            } else if (t <= 20f) {
                if (autoGPS.getValue() && !gpsVisited.contains(pos)) sendGPS(pos);
                if (notifications.getValue() && !notified.contains(pos)) {
                    postNotify("Сундук через " + (int) t + " сек! ["
                            + pos.getX() + " " + pos.getZ() + "]");
                    notified.add(pos);
                }
            }
        }
    }

    private void sendGPS(BlockPos pos) {
        sendCommand("gps set " + pos.getX() + " " + pos.getZ());
        gpsVisited.add(pos);
        currentGPSTarget = pos;
    }

    private void sendCommand(String cmd) {
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendChatCommand(cmd);
        }
    }

    private void postNotify(String text) {
        Interface iface = Instance.get(Interface.class);
        if (iface != null) {
            iface.notifications.postWarning("Warden Helper: " + text);
        }
    }

    // ── ESP цвета ─────────────────────────────────────────────────────────

    /** Возвращает RGB цвет ESP в зависимости от состояния сундука. */
    private int[] computeColorRGB(ChestData data) {
        if (!data.hasTimer) {
            return new int[]{CR_INACTIVE_R, CR_INACTIVE_G, CR_INACTIVE_B};
        }
        float t = data.getTimeLeft();
        if (t <= 0f) {
            // Пульсация зелёного.
            float p = (float) ((Math.sin(System.currentTimeMillis() / 200.0) * 0.3) + 0.7);
            return new int[]{0, (int) (255 * p), (int) (128 * p)};
        }
        if (t > 120f) return new int[]{CR_DEFAULT_R, CR_DEFAULT_G, CR_DEFAULT_B};
        if (t > 20f) {
            return lerpRGB(
                    CR_DEFAULT_R, CR_DEFAULT_G, CR_DEFAULT_B,
                    CR_WARN_R, CR_WARN_G, CR_WARN_B,
                    1f - ((t - 20f) / 100f));
        }
        return lerpRGB(
                CR_WARN_R, CR_WARN_G, CR_WARN_B,
                CR_READY_R, CR_READY_G, CR_READY_B,
                1f - (t / 20f));
    }

    private static int[] lerpRGB(int r1, int g1, int b1, int r2, int g2, int b2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new int[]{
                (int) (r1 + (r2 - r1) * t),
                (int) (g1 + (g2 - g1) * t),
                (int) (b1 + (b2 - b1) * t)
        };
    }

    // ── Рендер примитивов ────────────────────────────────────────────────

    /** Рисует залитый куб от (x1,y1,z1) до (x2,y2,z2) с цветом и альфой. */
    private static void drawFilledBox(Matrix4f m, BufferBuilder buf,
                                      float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      int r, int g, int b, int a) {
        // 6 граней по 4 вершины.
        // bottom (y=y1)
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y1, z2).color(r, g, b, a);
        buf.vertex(m, x1, y1, z2).color(r, g, b, a);
        // top (y=y2)
        buf.vertex(m, x1, y2, z2).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x2, y2, z1).color(r, g, b, a);
        buf.vertex(m, x1, y2, z1).color(r, g, b, a);
        // north (z=z1)
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x1, y2, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z1).color(r, g, b, a);
        buf.vertex(m, x2, y1, z1).color(r, g, b, a);
        // south (z=z2)
        buf.vertex(m, x2, y1, z2).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x1, y2, z2).color(r, g, b, a);
        buf.vertex(m, x1, y1, z2).color(r, g, b, a);
        // west (x=x1)
        buf.vertex(m, x1, y1, z2).color(r, g, b, a);
        buf.vertex(m, x1, y2, z2).color(r, g, b, a);
        buf.vertex(m, x1, y2, z1).color(r, g, b, a);
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        // east (x=x2)
        buf.vertex(m, x2, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x2, y1, z2).color(r, g, b, a);
    }

    /** Рисует контур куба (12 рёбер) линиями. */
    private static void drawBoxOutline(MatrixStack stack, BufferBuilder buf,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       int r, int g, int b, int a) {
        Matrix4f m = stack.peek().getPositionMatrix();
        // 12 рёбер куба.
        // bottom
        line(buf, m, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buf, m, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buf, m, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buf, m, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // top
        line(buf, m, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buf, m, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buf, m, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buf, m, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // verticals
        line(buf, m, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buf, m, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buf, m, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buf, m, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(BufferBuilder buf, Matrix4f m,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             int r, int g, int b, int a) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 0.0001f) len = 1f;
        nx /= len;
        ny /= len;
        nz /= len;
        buf.vertex(m, x1, y1, z1).color(r, g, b, a).normal(nx, ny, nz);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a).normal(nx, ny, nz);
    }
}

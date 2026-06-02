package endless.ere.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4d;
import endless.ere.Endless;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.base.events.impl.render.EventRender2D;
import endless.ere.base.events.impl.render.EventRender3D;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.math.ProjectionUtil;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;
import endless.ere.utility.render.level.Render3DUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подсвечивает сундуки с таймером (Warden и подобные ивенты) и рисует красивый
 * кастомный таймер над ними. Стандартный текст таймера у ArmorStand/TextDisplay
 * скрывается через mixin {@code EntityRendererMixin}.
 */
@ModuleAnnotation(name = "WardenHelper", category = Category.MISC,
        description = "Подсветка и красивый таймер сундуков ивента")
public final class WardenHelper extends Module {

    public static final WardenHelper INSTANCE = new WardenHelper();

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2})\\s*:\\s*(\\d{2})");
    private static final Pattern FORMAT_CODE = Pattern.compile("(?i)§[0-9A-FK-OR]");

    /** Сущности, чей рендер хотим скрыть (ArmorStand с таймером, TextDisplay). */
    private static final Set<Integer> HIDDEN_ENTITY_IDS = ConcurrentHashMap.newKeySet();

    private final NumberSetting rangeSetting = new NumberSetting("Радиус", 30f, 10f, 60f, 1f);
    private final NumberSetting textSize = new NumberSetting("Размер текста", 9f, 6f, 16f, 0.5f);
    private final BooleanSetting hideOriginal = new BooleanSetting("Скрывать стандартный таймер", true);
    private final BooleanSetting glowChest = new BooleanSetting("Свечение сундука", true);

    private final Map<BlockPos, ChestData> chests = new ConcurrentHashMap<>();

    private WardenHelper() {
    }

    public static boolean shouldHide(Entity entity) {
        if (entity == null) return false;
        WardenHelper instance = INSTANCE;
        if (!instance.isEnabled() || !instance.hideOriginal.isEnabled()) return false;
        return HIDDEN_ENTITY_IDS.contains(entity.getId());
    }

    @Override
    public void onDisable() {
        chests.clear();
        HIDDEN_ENTITY_IDS.clear();
        super.onDisable();
    }

    // ── Сканирование таймеров ───────────────────────────────────────────────

    @EventTarget
    public void onTick(EventUpdate event) {
        if (mc.world == null || mc.player == null) return;

        if (mc.player.age % 5 == 0) {
            scanChests();
            scanTimerEntities();
        }

        Vec3d playerCenter = mc.player.getPos();
        int range = (int) rangeSetting.getCurrent();
        chests.entrySet().removeIf(entry -> {
            double dist = playerCenter.distanceTo(entry.getKey().toCenterPos());
            if (dist > range + 12) return true;
            // Если блок больше не сундук — выкидываем
            return !(mc.world.getBlockState(entry.getKey()).getBlock() instanceof ChestBlock);
        });

        // Обновляем список скрытых сущностей
        Set<Integer> alive = new HashSet<>();
        for (ChestData data : chests.values()) {
            if (data.entity != null) alive.add(data.entity.getId());
        }
        HIDDEN_ENTITY_IDS.retainAll(alive);
        HIDDEN_ENTITY_IDS.addAll(alive);
    }

    private void scanChests() {
        int range = (int) rangeSetting.getCurrent();
        BlockPos pPos = mc.player.getBlockPos();
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                for (int y = -5; y <= 5; y++) {
                    BlockPos pos = pPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                        chests.computeIfAbsent(pos.toImmutable(), p -> new ChestData(-1, null));
                    }
                }
            }
        }
    }

    private void scanTimerEntities() {
        for (Map.Entry<BlockPos, ChestData> entry : chests.entrySet()) {
            BlockPos pos = entry.getKey();
            Vec3d center = pos.toCenterPos();
            Box searchBox = new Box(
                    center.x - 1.0, center.y, center.z - 1.0,
                    center.x + 1.0, center.y + 4.0, center.z + 1.0);

            boolean foundTimer = false;
            for (Entity entity : mc.world.getOtherEntities(null, searchBox)) {
                String raw = readTimerText(entity);
                if (raw == null) continue;

                String clean = FORMAT_CODE.matcher(raw).replaceAll("");
                Matcher m = TIME_PATTERN.matcher(clean);
                if (!m.find()) continue;

                int total = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
                ChestData existing = entry.getValue();
                if (existing == null || !existing.hasValidTimer()
                        || Math.abs(existing.timerSeconds - total) > 1
                        || existing.entity != entity) {
                    ChestData fresh = new ChestData(total, entity);
                    fresh.wasArmed = true;
                    chests.put(pos, fresh);
                } else {
                    existing.wasArmed = true;
                }
                foundTimer = true;
                break;
            }

            // Если таймера нет, но раньше у сундука он был — переходим в Ready
            if (!foundTimer) {
                ChestData existing = entry.getValue();
                boolean wasArmed = existing != null && existing.wasArmed;
                if (existing == null
                        || existing.hasValidTimer()
                        || existing.entity != null
                        || existing.wasArmed != wasArmed) {
                    ChestData fresh = new ChestData(-1, null);
                    fresh.wasArmed = wasArmed;
                    chests.put(pos, fresh);
                }
            }
        }
    }

    private String readTimerText(Entity entity) {
        if (entity instanceof DisplayEntity.TextDisplayEntity td) {
            return td.getText().getString();
        }
        if (entity instanceof ArmorStandEntity as) {
            Text name = as.getCustomName();
            return name != null ? name.getString() : null;
        }
        return null;
    }

    // ── Рендер 3D — свечение ────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(EventRender3D event) {
        if (!glowChest.isEnabled()) return;
        long now = System.currentTimeMillis();
        float pulse = 0.55f + 0.45f * (float) Math.sin(now / 320.0);

        for (Map.Entry<BlockPos, ChestData> entry : chests.entrySet()) {
            ChestData data = entry.getValue();
            boolean ticking = data.hasValidTimer() && data.getRemainingSeconds() > 0;
            boolean ready = !data.hasValidTimer() && data.wasArmed;
            if (!ready && !ticking) continue;

            Box box = new Box(entry.getKey()).expand(0.01);

            int glowAlpha = Math.round(MathHelper.clamp(75 + 70 * pulse, 60, 200));
            int outlineAlpha = Math.round(MathHelper.clamp(170 + 70 * pulse, 160, 255));

            int glowColor;
            int outlineColor;
            if (ticking) {
                int leftSec = data.getRemainingSeconds();
                // Бело-жёлтый, ближе к концу — теплее
                float warmMix = leftSec <= 30 ? 1.0f : 0.45f + (1f - Math.min(1f, leftSec / 120f)) * 0.35f;
                glowColor = mixColor(0xFFFFFFFF, 0xFFFFE066, warmMix, glowAlpha);
                outlineColor = mixColor(0xFFFFE066, 0xFFFFFFFF, 0.35f * pulse, outlineAlpha);
            } else {
                // Готов — мягкое бело-зелёное свечение
                glowColor = mixColor(0xFFFFFFFF, 0xFF7CFF9F, 0.65f, glowAlpha);
                outlineColor = mixColor(0xFF7CFF9F, 0xFFFFFFFF, 0.4f * pulse, outlineAlpha);
            }

            // Заливка
            Render3DUtil.drawBox(box, glowColor, 0f, false, true, false);
            // Контур
            Render3DUtil.drawBox(box.expand(0.005), outlineColor, 1.4f, true, false, false);
        }
    }

    // ── Рендер 2D — кастомный таймер ────────────────────────────────────────

    @EventTarget
    public void onRender2D(EventRender2D event) {
        if (mc.world == null || mc.player == null) return;
        CustomDrawContext context = event.getContext();

        Font font = Fonts.MEDIUM.getFont(textSize.getCurrent());
        ColorRGBA themeAccent = Endless.getInstance().getThemeManager().getCurrentTheme().getColor();
        ColorRGBA readyAccent = new ColorRGBA(124, 255, 159);

        for (Map.Entry<BlockPos, ChestData> entry : chests.entrySet()) {
            ChestData data = entry.getValue();
            BlockPos pos = entry.getKey();

            String label;
            ColorRGBA accent;
            if (data.hasValidTimer()) {
                int left = data.getRemainingSeconds();
                if (left <= 0) continue;
                label = String.format("%02d:%02d", left / 60, left % 60);
                accent = left <= 30 ? new ColorRGBA(255, 110, 80) : themeAccent;
            } else if (data.wasArmed) {
                label = "Ready";
                accent = readyAccent;
            } else {
                continue;
            }

            Vec3d anchorWorld = data.entity != null
                    ? data.entity.getPos().add(0, data.entity.getHeight() + 0.2, 0)
                    : pos.toCenterPos().add(0, 1.2, 0);

            Vec3d projected = ProjectionUtil.worldSpaceToScreenSpace(anchorWorld);
            if (projected.z <= 0 || projected.z >= 1) continue;

            float textWidth = font.width(label);
            float padX = 6f, padY = 3f;
            float boxW = textWidth + padX * 2f;
            float boxH = font.height() + padY * 2f;
            float boxX = (float) projected.x - boxW / 2f;
            float boxY = (float) projected.y - boxH;

            DrawUtil.drawBlurHud(context.getMatrices(), boxX, boxY, boxW, boxH,
                    18f, BorderRadius.all(boxH / 3f), ColorRGBA.WHITE);
            context.drawRoundedRect(boxX, boxY, boxW, boxH,
                    BorderRadius.all(boxH / 3f), new ColorRGBA(20, 16, 22, 200));

            // Тонкая акцентная полоска снизу
            float barW = boxW - 8f;
            float barX = boxX + 4f;
            float barY = boxY + boxH - 2f;
            context.drawRoundedRect(barX, barY, barW, 1.2f,
                    BorderRadius.all(0.6f), accent.withAlpha(220));

            context.drawText(font, label,
                    boxX + (boxW - textWidth) / 2f,
                    boxY + padY,
                    ColorRGBA.WHITE);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static int mixColor(int a, int b, float t, int alpha) {
        t = MathHelper.clamp(t, 0f, 1f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        int al = MathHelper.clamp(alpha, 0, 255);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    private static final class ChestData {
        final int timerSeconds;
        final long startTime;
        final boolean hasTimer;
        final Entity entity;
        boolean wasArmed;

        ChestData(int seconds, Entity entity) {
            this.timerSeconds = seconds;
            this.hasTimer = seconds > 0;
            this.startTime = System.currentTimeMillis();
            this.entity = entity;
            this.wasArmed = this.hasTimer;
        }

        int getRemainingSeconds() {
            if (!hasTimer) return 0;
            long elapsed = (System.currentTimeMillis() - startTime) / 1000L;
            return Math.max(0, timerSeconds - (int) elapsed);
        }

        boolean hasValidTimer() {
            return hasTimer;
        }
    }
}

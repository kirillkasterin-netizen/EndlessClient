/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.component.DataComponentTypes
 *  net.minecraft.component.type.PotionContentsComponent
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.network.packet.Packet
 *  net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket
 *  net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
 *  net.minecraft.potion.Potions
 *  net.minecraft.registry.Registries
 */
package endless.ere.client.hud.elements.component;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.events.impl.server.EventPacket;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.utility.math.MathUtil;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

public class CooldownComponent
extends DraggableHudElement {
    private final Map<String, CooldownModule> keyModules = new LinkedHashMap<String, CooldownModule>();
    private final Animation animationWidth = new Animation(200L, 100.0f, Easing.QUAD_IN_OUT);
    private final Animation animationScale = new Animation(200L, 0.0f, Easing.QUAD_IN_OUT);
    private final Animation animationVisible = new Animation(200L, 0.0f, Easing.QUAD_IN_OUT);
    private final Map<String, TimedCooldown> customCooldowns = new LinkedHashMap<String, TimedCooldown>();
    private final Map<String, TimedCooldown> vanillaCooldowns = new LinkedHashMap<String, TimedCooldown>();

    public CooldownComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
        EventManager.register(this);
    }

    @EventTarget
    public void onPacket(EventPacket e) {
        if (mc == null || CooldownComponent.mc.world == null) {
            return;
        }
        Packet<?> packet = e.getPacket();
        if (packet instanceof CooldownUpdateS2CPacket) {
            CooldownUpdateS2CPacket pkt = (CooldownUpdateS2CPacket)packet;
            Item item = (Item)Registries.ITEM.get(pkt.cooldownGroup());
            int ticks = pkt.cooldown();
            String id = "vanilla:" + item.getTranslationKey();
            if (ticks == 0) {
                this.vanillaCooldowns.remove(id);
                this.keyModules.remove(id);
                return;
            }
            long nowTicks = CooldownComponent.mc.world.getTime();
            this.vanillaCooldowns.put(id, TimedCooldown.fromTicks(id, item.getName().getString(), item.getDefaultStack(), nowTicks, ticks));
        } else if (e.getPacket() instanceof PlayerRespawnS2CPacket) {
            this.vanillaCooldowns.clear();
            this.customCooldowns.clear();
            this.keyModules.clear();
        }
    }

    @EventTarget
    public void onCustomCooldown(EventPacket e) {
        PotionContentsComponent data;
        if (mc == null || CooldownComponent.mc.player == null) {
            return;
        }
        ItemStack itemStack = CooldownComponent.mc.player.getActiveItem();
        if (itemStack == null) {
            return;
        }
        if (CooldownComponent.mc.player.getItemUseTime() >= itemStack.getMaxUseTime((LivingEntity)CooldownComponent.mc.player) && (data = (PotionContentsComponent)itemStack.get(DataComponentTypes.POTION_CONTENTS)) != null && (data.getColor() == 33461 || data.getColor() == -515037)) {
            ItemStack medikStak = Items.POTION.getDefaultStack();
            medikStak.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.of(Potions.SWIFTNESS), Optional.of(data.getColor()), List.of(), Optional.empty()));
            if (data.getColor() == -515037) {
                this.addCustomCooldown(medikStak, "\u0418\u0441\u0446\u0435\u043b", 10000L);
            } else {
                this.addCustomCooldown(medikStak, "\u0418\u0441\u0446\u0435\u043b", 10000L);
            }
        }
    }

    public void addCustomCooldown(ItemStack stack, long durationMs) {
        String id = "custom:" + stack.getItem().getTranslationKey();
        this.addCustomCooldownReal(id, stack.getItem().getName().getString(), stack, durationMs);
    }

    public void addCustomCooldown(ItemStack stack, String displayName, long durationMs) {
        this.addCustomCooldownReal("custom:" + displayName, displayName, stack, durationMs);
    }

    public void addCustomCooldownReal(String id, String displayName, ItemStack iconStack, long durationMs) {
        if (mc == null) {
            return;
        }
        if (this.keyModules.containsKey(id)) {
            return;
        }
        long nowNs = System.nanoTime();
        this.customCooldowns.put(id, TimedCooldown.fromReal(id, displayName, iconStack, nowNs, Math.max(1L, durationMs) * 1000000L));
    }

    public void addCustomCooldownTicks(String id, String displayName, ItemStack iconStack, long durationTicks) {
        if (mc == null || CooldownComponent.mc.world == null) {
            return;
        }
        if (this.keyModules.containsKey(id)) {
            return;
        }
        long nowTicks = CooldownComponent.mc.world.getTime();
        this.customCooldowns.put(id, TimedCooldown.fromTicks(id, displayName, iconStack, nowTicks, Math.max(1L, durationTicks)));
    }

    @Override
    public void render(CustomDrawContext ctx) {
        if (mc == null) {
            return;
        }
        Font iconFont = Fonts.ICONS.getFont(6.0f);
        long nowTicks = mc != null && CooldownComponent.mc.world != null ? CooldownComponent.mc.world.getTime() : 0L;
        for (TimedCooldown tc : this.vanillaCooldowns.values()) {
            if (tc.isFinished(nowTicks)) continue;
                this.keyModules.computeIfAbsent(tc.id, k -> new CooldownModule(tc.icon, tc.displayName, tc.getProgress(nowTicks), () -> Float.valueOf(tc.getProgress(CooldownComponent.mc.world != null ? CooldownComponent.mc.world.getTime() : nowTicks)), () -> tc.isFinished(CooldownComponent.mc.world != null ? CooldownComponent.mc.world.getTime() : nowTicks), () -> tc.getRemainingSeconds(CooldownComponent.mc.world != null ? CooldownComponent.mc.world.getTime() : nowTicks)));
        }
        for (TimedCooldown tc : this.customCooldowns.values()) {
            if (tc.base == TimeBase.TICKS) {
                if (tc.isFinished(nowTicks)) continue;
                    this.keyModules.computeIfAbsent(tc.id, k -> new CooldownModule(tc.icon, tc.displayName, tc.getProgress(nowTicks), () -> Float.valueOf(tc.getProgress(CooldownComponent.mc.world != null ? CooldownComponent.mc.world.getTime() : nowTicks)), () -> tc.isFinished(CooldownComponent.mc.world != null ? CooldownComponent.mc.world.getTime() : nowTicks), () -> tc.getRemainingSeconds(CooldownComponent.mc.world != null ? CooldownComponent.mc.world.getTime() : nowTicks)));
                continue;
            }
            if (tc.isFinished(0L)) continue;
            this.keyModules.computeIfAbsent(tc.id, k -> new CooldownModule(tc.icon, tc.displayName, tc.getProgress(0L), () -> Float.valueOf(tc.getProgress(0L)), () -> tc.isFinished(0L), () -> tc.getRemainingSeconds(0L)));
        }
        if (this.keyModules.isEmpty()) {
            this.animationScale.update(0.0f);
        } else {
            boolean singleAndHidden = this.keyModules.size() == 1 && this.keyModules.values().iterator().next().animation.getTargetValue() == 0.0f;
            this.animationScale.update(!singleAndHidden ? 1 : 0);
        }
        float x = this.x;
        float y = this.y;
        float height = (float)(18.0 + this.keyModules.values().stream().mapToDouble(CooldownModule::getHeight).sum());
        float width = (float)this.keyModules.values().stream().mapToDouble(CooldownModule::updateWidth).max().orElse(100.0);
        this.width = width = this.animationWidth.update(width);
        this.height = height;
        this.animationVisible.update(CooldownComponent.mc.currentScreen instanceof ChatScreen || !this.keyModules.isEmpty());
        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        ctx.pushMatrix();
        ctx.getMatrices().translate(x + width / 2.0f, y + height / 2.0f, 0.0f);
        ctx.getMatrices().scale(this.animationVisible.getValue(), this.animationVisible.getValue(), 1.0f);
        ctx.getMatrices().translate(-(x + width / 2.0f), -(y + height / 2.0f), 0.0f);
        BorderRadius radius6 = BorderRadius.all(6.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, height, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
        ctx.drawRoundedRect(x, y, width, height, radius6, theme.getForegroundLight().mulAlpha(0.85f));
        BorderRadius headerRadius = height > 18.5f ? BorderRadius.top(4.0f, 4.0f) : BorderRadius.all(4.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, 18.0f, 35.0f, headerRadius, ColorRGBA.WHITE);
        ctx.drawRoundedRect(x, y, width, 18.0f, headerRadius, theme.getBackgroundColor().mulAlpha(0.65f));
        DrawUtil.drawHudDots(ctx.getMatrices(), x, y, width, 18.0f, headerRadius, 0.18f);
        if (height > 18.5f) {
            ctx.drawRect(x, y + 18.0f, width, 1.0f, theme.getForegroundStroke());
        }
        ctx.drawText(iconFont, "N", x + 8.0f, y + (18.0f - iconFont.height()) / 2.0f, theme.getColor());
        ctx.drawText(iconFont, "M", x + width - 8.0f - iconFont.width("M"), y + (18.0f - iconFont.height()) / 2.0f, theme.getWhiteGray());
        Font font = Fonts.MEDIUM.getFont(6.0f);
        ctx.drawText(font, "Cooldown", x + 8.0f + 8.0f + 2.0f, y + (18.0f - font.height()) / 2.0f, theme.getWhite());
        if (this.animationVisible.getValue() == 1.0f) {
            float kmY = y + 18.0f;
            int i = 0;
            for (CooldownModule km : this.keyModules.values()) {
                km.render(ctx, x, kmY, width, i);
                kmY += km.getHeight();
                ++i;
            }
        }
        this.keyModules.entrySet().removeIf(entry -> ((CooldownModule)entry.getValue()).isDelete());
        if (mc != null && CooldownComponent.mc.world != null) {
            long t = CooldownComponent.mc.world.getTime();
            this.vanillaCooldowns.entrySet().removeIf(e -> ((TimedCooldown)e.getValue()).isFinished(t));
            this.customCooldowns.entrySet().removeIf(e -> ((TimedCooldown)e.getValue()).base == TimeBase.TICKS ? ((TimedCooldown)e.getValue()).isFinished(t) : ((TimedCooldown)e.getValue()).isFinished(0L));
        }
        ctx.drawRoundedBorder(x, y, width, height, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
        DrawUtil.drawRoundedCorner(ctx.getMatrices(), x, y, width, height, 0.1f, Math.min(20.0f, Math.max(12.0f, height / 2.5f)), theme.getColor(), BorderRadius.all(4.0f));
        ctx.popMatrix();
    }

    private class CooldownModule {
        private final Animation animation = new Animation(150L, 0.01f, Easing.QUAD_IN_OUT);
        private final Animation animationColor = new Animation(200L, Easing.QUAD_IN_OUT);
        private final Animation animationProgress;
        private final ItemStack iconStack;
        private final String displayName;
        private final Supplier<Float> progressSupplier;
        private final BooleanSupplier finishedSupplier;
        private final Supplier<Integer> remainingSecondsSupplier;

        public CooldownModule(ItemStack iconStack, String displayName, float initialProgress, Supplier<Float> progressSupplier, BooleanSupplier finishedSupplier, Supplier<Integer> remainingSecondsSupplier) {
            this.iconStack = iconStack;
            this.displayName = displayName;
            this.progressSupplier = progressSupplier;
            this.finishedSupplier = finishedSupplier;
            this.remainingSecondsSupplier = remainingSecondsSupplier;
            this.animationProgress = new Animation(200L, initialProgress, Easing.LINEAR);
        }

        public float updateWidth() {
            float width = 120.0f;
            String timeStr = this.formatTime(this.remainingSecondsSupplier.get());
            float moduleTextWidth = Fonts.MEDIUM.getWidth(this.displayName, 6.0f);
            float timeTextWidth = Fonts.MEDIUM.getWidth(timeStr, 6.0f);
            float rightPad = 8.0f + timeTextWidth;
            float widthText = width - (rightPad + 10.0f + 5.0f + 10.0f);
            if (widthText < 8.0f + moduleTextWidth + 8.0f) {
                float deltaWidth = moduleTextWidth + 8.0f + 8.0f - widthText;
                width += deltaWidth;
            }
            return width;
        }

        public float getHeight() {
            return 18.0f * this.animation.getValue();
        }

        public void render(CustomDrawContext ctx, float x, float y, float width, int i) {
            float progress = this.updateProgressAndGet();
            Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
            Font font = Fonts.MEDIUM.getFont(6.0f);
            this.animation.update(progress > 0.0f ? 1 : 0);
            ctx.pushMatrix();
            ctx.getMatrices().translate(x + width / 2.0f, y + 9.0f, 0.0f);
            float deltaAnim = this.animation.getValue();
            ctx.getMatrices().scale(deltaAnim, deltaAnim, 1.0f);
            ctx.getMatrices().translate(-(x + width / 2.0f), -(y + 9.0f), 0.0f);
            this.animationColor.update(i % 2 == 0 ? 1 : 0);
            ColorRGBA backgroundColor = new ColorRGBA(0, 0, 0, 255);
            ctx.drawRoundedRect(x, y, width, 18.0f, i == CooldownComponent.this.keyModules.size() - 1 ? BorderRadius.bottom(4.0f, 4.0f) : BorderRadius.ZERO, backgroundColor);
            ctx.pushMatrix();
            ctx.getMatrices().translate(x + 6.0f, y + 5.0f, 0.0f);
            ctx.getMatrices().scale(0.5f, 0.5f, 1.0f);
            ctx.drawItem(this.iconStack, 0, 0);
            ctx.popMatrix();
            ctx.drawText(font, this.displayName, x + 8.0f + 8.0f + 8.0f, y + (18.0f - font.height()) / 2.0f, theme.getWhite());
            String timeStr = this.formatTime(this.remainingSecondsSupplier.get());
            float timeWidth = Fonts.MEDIUM.getWidth(timeStr, 6.0f);
            float cooldownProgress = this.progressSupplier.get();
            ColorRGBA timerColor = ColorRGBA.WHITE.mix(new ColorRGBA(255, 50, 50, 255), cooldownProgress);
            ctx.drawText(font, timeStr, x + width - timeWidth - 8.0f, y + (18.0f - font.height()) / 2.0f, timerColor);
            ctx.popMatrix();
        }

        public boolean isDelete() {
            return this.animation.getValue() == 0.0f && this.finishedSupplier.getAsBoolean();
        }

        private float updateProgressAndGet() {
            float p = this.progressSupplier.get().floatValue();
            this.animationProgress.update(p);
            return MathUtil.round(this.animationProgress.getValue());
        }

        private String formatTime(int totalSeconds) {
            if (totalSeconds < 0) {
                totalSeconds = 0;
            }
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    private static enum TimeBase {
        TICKS,
        REAL;

    }

    private static class TimedCooldown {
        final String id;
        final String displayName;
        final ItemStack icon;
        final TimeBase base;
        final long start;
        final long duration;

        private TimedCooldown(String id, String displayName, ItemStack icon, TimeBase base, long start, long duration) {
            this.id = id;
            this.displayName = displayName;
            this.icon = icon;
            this.base = base;
            this.start = start;
            this.duration = duration;
        }

        static TimedCooldown fromTicks(String id, String name, ItemStack icon, long startTicks, long durationTicks) {
            return new TimedCooldown(id, name, icon, TimeBase.TICKS, startTicks, durationTicks);
        }

        static TimedCooldown fromReal(String id, String name, ItemStack icon, long startNs, long durationNs) {
            return new TimedCooldown(id, name, icon, TimeBase.REAL, startNs, durationNs);
        }

        float getProgress(long nowTick) {
            long remain = switch (this.base) {
                case TimeBase.TICKS -> Math.max(0L, this.start + this.duration - nowTick);
                case TimeBase.REAL -> Math.max(0L, this.start + this.duration - System.nanoTime());
                default -> throw new MatchException(null, null);
            };
            return this.duration <= 0L ? 0.0f : (float)remain / (float)this.duration;
        }

        boolean isFinished(long nowTick) {
            return switch (this.base) {
                case TimeBase.TICKS -> {
                    if (nowTick >= this.start + this.duration) {
                        yield true;
                    }
                    yield false;
                }
                case TimeBase.REAL -> {
                    if (System.nanoTime() >= this.start + this.duration) {
                        yield true;
                    }
                    yield false;
                }
                default -> throw new MatchException(null, null);
            };
        }

        int getRemainingSeconds(long nowTick) {
            long remain = switch (this.base) {
                case TimeBase.TICKS -> Math.max(0L, this.start + this.duration - nowTick);
                case TimeBase.REAL -> Math.max(0L, this.start + this.duration - System.nanoTime());
                default -> throw new MatchException(null, null);
            };
            if (this.base == TimeBase.TICKS) {
                return (int)(remain / 20L);
            }
            return (int)(remain / 1000000000L);
        }
    }
}

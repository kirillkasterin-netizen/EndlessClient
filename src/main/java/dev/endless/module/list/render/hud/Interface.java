package dev.endless.module.list.render.hud;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import dev.endless.Endless;
import dev.endless.event.list.EventHUD;
import dev.endless.event.list.EventPacket;
import dev.endless.event.list.EventPopTotem;
import dev.endless.event.list.EventTick;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.list.combat.KillAura;
import dev.endless.module.list.misc.NameProtect;
import dev.endless.module.list.render.Tags;
import dev.endless.module.settings.*;
import dev.endless.module.settings.impl.Theme;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import dev.endless.util.base.Instance;
import dev.endless.util.draggable.DragManager;
import dev.endless.util.draggable.Draggable;
import dev.endless.util.keyboard.KeyStorage;
import dev.endless.util.math.Counter;
import dev.endless.util.render.builders.Builder;
import dev.endless.util.render.builders.states.QuadColorState;
import dev.endless.util.render.builders.states.QuadRadiusState;
import dev.endless.util.render.builders.states.SizeState;
import dev.endless.util.render.effects.GlassEffect;
import dev.endless.util.render.math.Animation;
import dev.endless.util.render.math.Easing;
import dev.endless.util.render.math.Scissor;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;
import dev.endless.util.replace.ReplaceUtil;
import dev.endless.util.server.Server;
import dev.endless.util.staff.StaffManager;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ModuleInformation(moduleName = "Interface", moduleCategory = ModuleCategory.RENDER)
public class Interface extends Module {

    private static final Identifier TARGET_HUD_GLOW_TEXTURE = Identifier.of("mre", "images/glow.png");
    private static final Identifier WATERMARK_LOGO_TEXTURE = Identifier.of("mre", "images/logoendless.png");


    private final ModeListSetting elements = new ModeListSetting("Элементы",
            new BooleanSetting("Ватермарка", true),
            new BooleanSetting("Координаты", false),
            new BooleanSetting("Активный таргет", true),
            new BooleanSetting("Таргет худ от темы", false),
            new BooleanSetting("Привязанные модули", true),
            new BooleanSetting("Активные модераторы", true),
            new BooleanSetting("Бафы", true),
            new BooleanSetting("Скорость", true),
            new BooleanSetting("Счетчик тотемов", true),
            new BooleanSetting("Нотификации", true),
            new BooleanSetting("СпекТрекер", true),
            new BooleanSetting("Блюр фона", true),
            new BooleanSetting("Задний фон от темы", false),
            new BooleanSetting("Эффект жидкого стекла", true)
    );
    private final ModeSetting hudMode = new ModeSetting("Режим худа", "New", "New");
    private final ModeSetting targetHudMode = new ModeSetting("Режим таргет худа", "New", "New");
    private final ModeSetting guiStyle = new ModeSetting("Стиль ClickGUI", "Панели", "Панели");

    private final SliderSetting backgroundIntensity =
            new SliderSetting("Интенсивность фона", 0.5f, 0.05f, 1.0f, 0.01f);
    private final SliderSetting headerIntensity =
            new SliderSetting("Интенсивность заголовков", 0.5f, 0.05f, 1.0f, 0.01f);
    private final SliderSetting itemIntensity =
            new SliderSetting("Интенсивность элементов", 0.2f, 0.05f, 1.0f, 0.01f);
    private final SliderSetting lowHpAlertThreshold =
            new SliderSetting("Порог ХП оповещения", 8f, 1f, 20f, 0.5f);

    private static final SoundEvent SPEK_SOUND = SoundEvent.of(Identifier.of("mre", "spek"));

    private final Draggable watermarkDrag = DragManager.installDrag(this, "HotKeys", 4, 4);
    private final Draggable keyBindsDrag = DragManager.installDrag(this, "HotKeys", 100, 50);
    private final Draggable staffListDrag = DragManager.installDrag(this, "StaffList", 200, 50);
    private final Draggable potionsDrag = DragManager.installDrag(this, "Potions", 300, 50);
    private final Draggable targetHUDDrag = DragManager.installDrag(this, "TargetHUD", 130, 130);
    private final Draggable totemCounterDrag = DragManager.installDrag(this, "TotemCounter", 200, 200);

    public final NotificationsElement notifications = new NotificationsElement();

    private final Map<String, Long> spekSuspects = new ConcurrentHashMap<>();
    
    public ModeSetting getGuiStyle() {
        return guiStyle;
    }
    
    public float getBackgroundIntensity() {
        return backgroundIntensity.getFloatValue();
    }

    public void drawHeaderBackground(float x, float y, float w, float h, float radius, int alpha) {
        float intensity = headerIntensity.getFloatValue();
        int baseColor = ColorProvider.rgba(40, 38, 50, (int) (alpha * intensity));
        
        if (elements.isEnabled("Блюр фона")) {
            DrawUtil.drawRoundBlur(x, y, w, h, radius, ColorProvider.rgba(200, 200, 200, alpha), 12);
        }
        DrawUtil.drawRound(x, y, w, h, radius, baseColor);
        
        if (elements.isEnabled("Задний фон от темы")) {
            DrawUtil.drawRound(x, y, w, h, radius, getThemeTint(alpha));
        }
    }

    private void drawItemBackground(float x, float y, float w, float h, float radius, int alpha) {
        float intensity = itemIntensity.getFloatValue();
        int baseColor = ColorProvider.rgba(40, 38, 50, (int) (alpha * intensity));
        
        if (elements.isEnabled("Блюр фона")) {
            DrawUtil.drawRoundBlur(x, y, w, h, radius, ColorProvider.rgba(200, 200, 200, alpha), 12);
        }
        DrawUtil.drawRound(x, y, w, h, radius, baseColor);
        
        if (elements.isEnabled("Задний фон от темы")) {
            DrawUtil.drawRound(x, y, w, h, radius, getThemeTint(alpha));
        }
    }

    public void drawBackground(float x, float y, float w, float h, float radius, int alpha) {
        int baseColor = ColorProvider.rgba(40, 38, 50, (int) (alpha * backgroundIntensity.getFloatValue()));
        
        if (elements.isEnabled("Блюр фона")) {
            DrawUtil.drawRoundBlur(x, y, w, h, radius, ColorProvider.rgba(200, 200, 200, alpha), 12);
        }
        DrawUtil.drawRound(x, y, w, h, radius, baseColor);
        
        if (elements.isEnabled("Задний фон от темы")) {
            DrawUtil.drawRound(x, y, w, h, radius, getThemeTint(alpha));
        }
    }

    public void drawBackground(float x, float y, float w, float h, float tl, float tr, float bl, float br, int alpha) {
        org.joml.Vector4f radii = new org.joml.Vector4f(tl, tr, bl, br);
        int baseColor = ColorProvider.rgba(40, 38, 50, (int) (alpha * backgroundIntensity.getFloatValue()));
        
        if (elements.isEnabled("Блюр фона")) {
            DrawUtil.drawRoundBlur(x, y, w, h, radii, ColorProvider.rgba(200, 200, 200, alpha), 12);
        }
        DrawUtil.drawRound(x, y, w, h, radii, baseColor);
        
        if (elements.isEnabled("Задний фон от темы")) {
            DrawUtil.drawRound(x, y, w, h, radii, getThemeTint(alpha));
        }
    }

    @Subscribe
    public void onEventHUD(EventHUD e) {
        if (mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;

        // Начинаем захват HUD если включен эффект жидкого стекла
        if (elements.isEnabled("Эффект жидкого стекла")) {
            GlassEffect.setEnabled(true);
            GlassEffect.beginCapture();
        }

        if (elements.isEnabled("Нотификации")) {
            notifications.render(e.getDrawContext());
        }


        if (elements.isEnabled("Счетчик тотемов")) {
            renderTotemCounter(e.getDrawContext());
        }
        if (elements.isEnabled("Ватермарка")) {
            renderWatermark(e.getDrawContext());
        }
        if (elements.isEnabled("Координаты")) {
            renderCoordsInfo(e.getDrawContext());
        }
        if (elements.isEnabled("Активный таргет")) {
            renderTargetHUDNew(e.getDrawContext());
        }
        if (elements.isEnabled("Привязанные модули")) {
            renderKeyBinds(e.getDrawContext());
        }
        if (elements.isEnabled("Активные модераторы")) {
            renderStaffList(e.getDrawContext());
        }
        if (elements.isEnabled("Бафы")) {
            renderPotions(e.getDrawContext());
        }
        if (elements.isEnabled("Скорость")) {
            renderSpeed(e.getDrawContext());
        }
        
        // Заканчиваем захват и применяем эффект жидкого стекла ко всему HUD
        if (elements.isEnabled("Эффект жидкого стекла")) {
            GlassEffect.endCaptureAndApply();
        }
    }

    @Subscribe
    private void onUpdate(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        if (elements.isEnabled("СпекТрекер")) {
            long now = System.currentTimeMillis();

            spekSuspects.entrySet().removeIf(entry -> now - entry.getValue() > 30000);

            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                if (mc.player.distanceTo(p) < 50) {
                    spekSuspects.put(p.getName().getString(), now);
                }
            }

            KillAura ka = Instance.get(KillAura.class);
            if (ka != null && ka.isEnabled() && ka.getTarget() != null) {
                spekSuspects.put(ka.getTarget().getName().getString(), now);
            }
        }

        if (elements.isEnabled("Активные модераторы")) {
            update();
        }
        if (elements.isEnabled("Бафы")) {
            updatePotions();
        }
    }

    @Subscribe
    private void onPopTotem(EventPopTotem e) {
        if (!elements.isEnabled("Нотификации")) return;
        PlayerEntity player = e.getPlayer();
        String name = player.getName().getString();
        boolean enchanted = !player.getOffHandStack().getEnchantments().isEmpty();

        Text tagText = Tags.processName(player);
        notifications.postTotem(tagText, enchanted);
    }

    @Subscribe
    private void onPacket(EventPacket e) {
        if (!elements.isEnabled("СпекТрекер") || mc.player == null) return;

        if (e.getPacket() instanceof GameMessageS2CPacket packet) {
            String rawContent = packet.content().getString();
            String msgLower = rawContent.toLowerCase();

            boolean isTrigger = msgLower.contains("спек") ||
                    msgLower.contains("spec") ||
                    msgLower.contains("spek") ||
                    msgLower.contains("report") ||
                    msgLower.contains("фаст");

            if (isTrigger) {
                for (String suspect : spekSuspects.keySet()) {
                    if (rawContent.contains(suspect)) {
                        notifications.postWarning("Report Detect: " + suspect);
                        playSpekSound();
                        break;
                    }
                }
            }
        }
    }

    private void playSpekSound() {
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SPEK_SOUND, 1.0f));
        }
    }

    private final Animation animation = new Animation(Easing.EXPO_OUT, 300);
    private final Animation armorAnim = new Animation(Easing.EXPO_OUT, 300);
    private final Animation hpAnimation = new Animation(Easing.EXPO_OUT, 600);
    private final Animation outdatedHpAnimation = new Animation(Easing.EXPO_OUT, 1200);
    private final Animation absorptionAnimation = new Animation(Easing.EXPO_OUT, 300);
    private final Animation absorptionTrailAnimation = new Animation(Easing.EXPO_OUT, 1200);

    private float lastHealthVal = 0;
    private long lastTime = System.currentTimeMillis();

    private Entity lastTarget;
    private float lastHpPercent = -1f;
    private final Animation widthAnim = new Animation(Easing.EXPO_OUT, 200);
    private final Animation xLine = new Animation(Easing.EXPO_OUT, 170);
    private final Animation alpha = new Animation(Easing.EXPO_OUT, 200);

    public int getThemeTint(int alpha) {
        int themeColor = ColorProvider.getColorClient();
        return ColorProvider.setAlpha(themeColor, (int) (100 * (alpha / 255f) * backgroundIntensity.getFloatValue()));
    }

    private final Animation lowHpAlertAnimation = new Animation(Easing.EXPO_OUT, 300);


    private void renderKeyBinds(DrawContext context) {
        if (mc.player == null) return;
        renderKeyBindsNew(context);
    }

    private record BindEntry(String label, String bind, double animValue, ModuleCategory category) {}

    private void renderKeyBindsClassic(DrawContext context) {
        if (mc.player == null) return;

        float posX = keyBindsDrag.getX();
        float posY = keyBindsDrag.getY();

        float defaultWidth = 55;
        float height = 14.5f;


        List<BindEntry> entries = new ArrayList<>();
        for (Module module : Endless.getInstance().getModuleStorage().getModules()) {
            if (module.getKey() != -1 && module.getAnimation().getValue() > 0.001) {
                entries.add(new BindEntry(module.getName(), KeyStorage.getKey(module.getKey()), module.getAnimation().getValue(), module.getCategory()));
            }
            for (Setting setting : module.getSettings()) {
                if (setting instanceof BooleanSetting bs && bs.getKey() != -1 && bs.getValue()) {
                    String label = module.getName() + " > " + bs.getName();
                    entries.add(new BindEntry(label, KeyStorage.getKey(bs.getKey()), 1.0, module.getCategory()));
                }
            }
        }

        boolean isFound = !entries.isEmpty();
        if (isFound) alpha.run(1);
        if (!isFound && !(mc.currentScreen instanceof ChatScreen)) alpha.run(0);
        if (mc.currentScreen instanceof ChatScreen) alpha.run(1);

        float globalAlpha = (float) alpha.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        drawBackground(posX, posY, (float) widthAnim.getValue(), height, 3, headerAlpha);

        DrawUtil.drawRound(posX + 15.25f, posY + 2, 0.5f, 10.5f, 0, ColorProvider.rgba(125,125,125, headerAlpha));
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "C", posX + 4f, posY + 4f, ColorProvider.rgba(255,255,255, headerAlpha), 8);
        drawHeaderText("Hotkeys", posX + 19.5f, posY + 3.25f, 7.5f, headerAlpha);

        posY += 14.5f;
        float bindWidth = 0;

        for (BindEntry entry : entries) {
            float localBindWidth = Fonts.SFREGULAR.get().getWidth(entry.bind(), 6.75f);
            if (localBindWidth > bindWidth) bindWidth = localBindWidth;
        }

        xLine.run(bindWidth);

        for (BindEntry entry : entries) {
            float animVal = (float) Math.min(1.0, Math.max(0.0, entry.animValue()));

            float heightFactor = 1.0f;
            float itemHeight = 12 * heightFactor;
            height += itemHeight;

            int itemAlpha = (int) (255 * globalAlpha);
            itemAlpha = Math.min(255, Math.max(0, itemAlpha));

            if (itemAlpha < 5) {
                posY += itemHeight;
                continue;
            }

            String bind = entry.bind();
            String label = entry.label();
            float elementsWidth = Fonts.SFREGULAR.get().getWidth(label, 6.75f) + Fonts.SFREGULAR.get().getWidth(bind, 6.75f) + 30;

            float textYOffset = (itemHeight / 2f) - 4f;

            drawBackground(posX, posY, (float) widthAnim.getValue(), itemHeight, 3, itemAlpha);

            float separatorX = (float) (posX + widthAnim.getValue() - 6.5f - xLine.getValue());
            DrawUtil.drawRound(separatorX, posY + 2, 0.5f, itemHeight - 4, 0, ColorProvider.rgba(125,125,125, itemAlpha));

            DrawUtil.drawText(Fonts.SFREGULAR.get(), label, posX + 4.25f, posY + textYOffset, ColorProvider.rgba(255,255,255, itemAlpha), 6.5f);

            float bindX = (float) (posX + widthAnim.getValue() - 2.5f - xLine.getValue() - Fonts.SFREGULAR.get().getWidth(bind, 6.75f) / 2 + xLine.getValue() / 2 - 0.25f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), bind, bindX, posY + textYOffset, ColorProvider.rgba(255,255,255, itemAlpha), 6.5f);

            if (elementsWidth > defaultWidth) defaultWidth = elementsWidth;

            posY += itemHeight;
        }

        widthAnim.run(defaultWidth);
        keyBindsDrag.setWidth((float) widthAnim.getValue());
        keyBindsDrag.setHeight(height);
    }

    private void renderKeyBindsNew(DrawContext context) {
        if (mc.player == null) return;

        float posX = keyBindsDrag.getX();
        float posY = keyBindsDrag.getY();

        float headerHeight = 15f;
        float itemHeight = 12f;
        float minWidth = 65f;

        List<BindEntry> entries = new ArrayList<>();
        for (Module module : Endless.getInstance().getModuleStorage().getModules()) {
            if (module.getKey() != -1 && module.getAnimation().getValue() > 0.001) {
                entries.add(new BindEntry(module.getName(), KeyStorage.getKey(module.getKey()), module.getAnimation().getValue(), module.getCategory()));
            }
            for (Setting setting : module.getSettings()) {
                if (setting instanceof BooleanSetting bs && bs.getKey() != -1 && bs.getValue()) {
                    entries.add(new BindEntry(bs.getName(), KeyStorage.getKey(bs.getKey()), 1.0, module.getCategory()));
                }
            }
        }

        boolean isFound = !entries.isEmpty();
        if (isFound) alpha.run(1);
        if (!isFound && !(mc.currentScreen instanceof ChatScreen)) alpha.run(0);
        if (mc.currentScreen instanceof ChatScreen) alpha.run(1);

        float globalAlpha = (float) alpha.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        // Считаем суммарную ширину блока: иконка + label + bind в одной строке.
        float maxBindTextW = 0f;
        for (BindEntry entry : entries) {
            float bw = Fonts.SFMEDIUM.get().getWidth(entry.bind(), 6.75f);
            if (bw > maxBindTextW) maxBindTextW = bw;
        }
        float maxLabelTextW = 0f;
        for (BindEntry entry : entries) {
            float lw = Fonts.SFMEDIUM.get().getWidth(entry.label(), 6.75f);
            if (lw > maxLabelTextW) maxLabelTextW = lw;
        }
        // 5 (left pad) + 8 (icon) + 4 (gap) + label + 8 (gap) + bind + 6 (right pad)
        float boxWidth = Math.max(minWidth, 5f + 8f + 4f + maxLabelTextW + 8f + maxBindTextW + 6f);

        // Высота = header + (item * count). Без gap'ов между строками,
        // чтобы получился единый блок, как на референсе.
        float totalHeight = headerHeight + Math.max(0, entries.size()) * itemHeight + 2f;

        // Один единый фон под всё (темнее).
        drawHeaderBackground(posX, posY, boxWidth, totalHeight, 3f, headerAlpha);

        // Header: иконка слева + название правее (как в Potions).
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "C",
                posX + 5f, posY + 3.5f,
                ColorProvider.setAlpha(ColorProvider.getColorIcons(), headerAlpha), 8);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "Keybinds",
                posX + 5f + 11f, posY + 3f,
                ColorProvider.rgba(255, 255, 255, headerAlpha), 7.5f);

        // Разделительная полоска под заголовком
        DrawUtil.drawRound(posX + 5f, posY + headerHeight - 1.5f, boxWidth - 10f, 0.5f, 0.25f,
                ColorProvider.rgba(255, 255, 255, (int)(25 * (headerAlpha / 255f))));

        float curY = posY + headerHeight;
        for (BindEntry entry : entries) {
            float animVal = (float) Math.min(1.0, Math.max(0.0, entry.animValue()));
            int itemAlpha = (int) Math.min(255, Math.max(0, 255 * animVal * globalAlpha));
            if (itemAlpha < 5) {
                curY += itemHeight;
                continue;
            }

            // Иконка категории.
            String catIcon = switch (entry.category()) {
                case COMBAT -> "a";
                case MOVEMENT -> "b";
                case RENDER -> "c";
                case PLAYER -> "d";
                case MISC -> "e";
            };
            DrawUtil.drawText(Fonts.ICONS_MINCED.get(), catIcon,
                    posX + 5f, curY + (itemHeight / 2f) - 4f,
                    ColorProvider.setAlpha(ColorProvider.getColorIcons(), itemAlpha), 7f);

            // Label.
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), entry.label(),
                    posX + 5f + 8f + 4f, curY + (itemHeight / 2f) - 4f,
                    ColorProvider.rgba(255, 255, 255, itemAlpha), 6.75f);

            // Bind справа.
            float bindW = Fonts.SFMEDIUM.get().getWidth(entry.bind(), 6.75f);
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), entry.bind(),
                    posX + boxWidth - bindW - 6f, curY + (itemHeight / 2f) - 4f,
                    ColorProvider.rgba(255, 255, 255, itemAlpha), 6.75f);

            curY += itemHeight;
        }

        keyBindsDrag.setWidth(boxWidth);
        keyBindsDrag.setHeight(totalHeight);
    }

    private final List<Staff> staffPlayers = new ArrayList<>();
    private final Pattern namePattern = Pattern.compile("^\\w{3,16}$");
    private final Pattern prefixMatches = Pattern.compile(".*(ꔷ|ꔳ|ꔩ|ꔥ|ꔡ|ꔗ|ꔓ|\\bmod\\b|\\badm\\b|\\bhelp\\b|\\bwne\\b|модер|хелп|помощ|админ|владел|отриц|\\btaf\\b|\\bcurat\\b|куратор|\\bdev\\b|разраб|\\bsupp\\b|саппорт|\\byt\\b|\\[yt\\]|ютуб|стажер|сотрудник).*");
    private void renderTotemCounter(DrawContext context) {
        if (mc.player == null) return;

        float posX = totemCounterDrag.getX();
        float posY = totemCounterDrag.getY();

        int totemCount = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == net.minecraft.item.Items.TOTEM_OF_UNDYING) {
                totemCount += mc.player.getInventory().getStack(i).getCount();
            }
        }

        String text = totemCount + "x";
        float width = Fonts.SFREGULAR.get().getWidth(text, 9) + 18;
        float height = 14;

        context.getMatrices().push();
        context.getMatrices().translate(posX + 2.5f, posY + 2.5f, 0);
        context.getMatrices().scale(0.79f, 0.79f, 0.79f);
        context.drawItem(new ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING), 0, 0);
        context.getMatrices().pop();

        DrawUtil.drawText(Fonts.SFREGULAR.get(), text, posX + 15, posY + 4.5f, -1, 9);

        totemCounterDrag.setWidth(width);
        totemCounterDrag.setHeight(height);
    }

    private final Animation widthAnim2 = new Animation(Easing.EXPO_OUT, 200);
    private final Animation alpha2 = new Animation(Easing.EXPO_OUT, 200);

    private void renderStaffList(DrawContext context) {
        if (mc.player == null) return;
        renderStaffListNew(context);
    }

    private void renderStaffListNew(DrawContext context) {
        float posX = staffListDrag.getX();
        float posY = staffListDrag.getY();

        float headerHeight = 15f;
        float itemHeight = 12f;
        float minWidth = 75f;

        boolean isFound = !staffPlayers.isEmpty();
        if (isFound) alpha2.run(1);
        if (!isFound && !(mc.currentScreen instanceof ChatScreen)) alpha2.run(0);
        if (mc.currentScreen instanceof ChatScreen) alpha2.run(1);

        float globalAlpha = (float) alpha2.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        // Размер всего блока: иконка-голова + имя + точка статуса в одной строке.
        float maxNameW = 0f;
        int visible = 0;
        for (Staff staff : staffPlayers) {
            staff.animation.run(staff.isOnServer ? 1 : 0);
            if (staff.animation.getValue() <= 0.001f) continue;
            visible++;
            float nw = Fonts.SFMEDIUM.get().getWidth(staff.prefix, 6.75f);
            if (nw > maxNameW) maxNameW = nw;
        }
        // 2 (left pad) + 8 (head) + 4 (gap) + name + 6 (gap) + 7 (dot) + 6 (right pad)
        float boxWidth = Math.max(keyBindsDrag.getWidth(), 2f + 8f + 4f + maxNameW + 6f + 7f + 6f);
        float totalHeight = headerHeight + Math.max(0, visible) * itemHeight + 2f;

        // Один общий фон (темнее стандартного).
        drawHeaderBackground(posX, posY, boxWidth, totalHeight, 3f, headerAlpha);

        // Header: иконка слева + название правее (как в Potions).
        DrawUtil.drawText(Fonts.ICONS_MINCED.get(), "n",
                posX + 5f, posY + 3.5f,
                ColorProvider.setAlpha(ColorProvider.getColorIcons(), headerAlpha), 9);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "StaffList",
                posX + 5f + 11f, posY + 3f,
                ColorProvider.rgba(255, 255, 255, headerAlpha), 7.5f);

        // Разделительная полоска под заголовком
        DrawUtil.drawRound(posX + 5f, posY + headerHeight - 1.5f, boxWidth - 10f, 0.5f, 0.25f,
                ColorProvider.rgba(255, 255, 255, (int)(25 * (headerAlpha / 255f))));

        float curY = posY + headerHeight;

        for (Staff staff : staffPlayers) {
            float animVal = (float) staff.animation.getValue();
            if (animVal <= 0.001f) continue;

            int itemAlpha = (int) Math.min(255, Math.max(0, 255 * animVal * globalAlpha));
            if (itemAlpha < 5) {
                curY += itemHeight;
                continue;
            }

            String name = staff.name;
            net.minecraft.util.Identifier skinTexture;
            PlayerListEntry playerEntry = mc.getNetworkHandler().getPlayerListEntry(name);
            if (playerEntry != null) {
                skinTexture = playerEntry.getSkinTextures().texture();
            } else {
                skinTexture = DefaultSkinHelper.getTexture();
            }
            int textureId = mc.getTextureManager().getTexture(skinTexture).getGlId();
            float headSize = 8f;
            Builder.texture()
                    .size(new SizeState(headSize, headSize))
                    .radius(new QuadRadiusState(2))
                    .color(new QuadColorState(ColorProvider.setAlpha(-1, itemAlpha)))
                    .texture(8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, textureId)
                    .smoothness(1f)
                    .build()
                    .render(context.getMatrices().peek().getPositionMatrix(),
                            posX + 4f, curY + (itemHeight - headSize) / 2f);

            DrawUtil.drawText(Fonts.SFMEDIUM.get(), staff.prefix,
                    posX + 4f + headSize + 4f, curY + (itemHeight / 2f) - 4f,
                    6.75f, itemAlpha);

            int dotColor = staff.status == Status.NONE
                    ? ColorProvider.rgba(32, 255, 32, itemAlpha)
                    : ColorProvider.rgba(255, 32, 32, itemAlpha);
            DrawUtil.drawRound(posX + boxWidth - 7f - 6f, curY + (itemHeight - 7f) / 2f,
                    7f, 7f, 2.6f, dotColor);

            curY += itemHeight;
        }

        staffListDrag.setWidth(boxWidth);
        staffListDrag.setHeight(totalHeight);
    }

    private void renderStaffListClassic(DrawContext context) {
        float posX = staffListDrag.getX();
        float posY = staffListDrag.getY();

        float defaultWidth = 64;
        float height = 14.5f;

        boolean isFound = false;
        if (!staffPlayers.isEmpty()) {
            alpha2.run(1);
            isFound = true;
        }

        if (!isFound && !(mc.currentScreen instanceof ChatScreen)) alpha2.run(0);
        if (mc.currentScreen instanceof ChatScreen) alpha2.run(1);

        float globalAlpha = (float) alpha2.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        drawBackground(posX, posY, (float) widthAnim2.getValue(), 14.5f, 3, headerAlpha);

        DrawUtil.drawRound(posX + 15.25f, posY + 2, 0.5f, 10.5f, 0, ColorProvider.rgba(88,88,88, headerAlpha));
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "O", posX + 4.25f, posY + 4.5f, ColorProvider.setAlpha(-1, headerAlpha), 8.5f);
        drawHeaderText("Staff Online", posX + 19.5f, posY + 3.25f, 7.5f, headerAlpha);

        posY += 14.5f;
        float bindWidth = 0;

        float headOffset = 12f;

        for (Staff staff : staffPlayers) {
            staff.animation.run(staff.isOnServer ? 1 : 0);
            float localBindWidth = headOffset + Fonts.SFREGULAR.get().getWidth(staff.prefix, 6.75f) + Fonts.SFREGULAR.get().getWidth(staff.status.string, 6.75f);
            if (localBindWidth > bindWidth) {
                bindWidth = localBindWidth;
            }
        }

        for (Staff staff : staffPlayers) {
            float animVal = (float) staff.animation.getValue();
            if (animVal <= 0.001f) continue;

            float heightFactor = Math.min(1.0f, animVal);
            float itemHeight = 11 * heightFactor;
            height += itemHeight;

            float alphaFactor = Math.min(1.0f, Math.max(0.0f, animVal));
            int itemAlpha = (int) (255 * alphaFactor * globalAlpha);
            itemAlpha = Math.min(255, Math.max(0, itemAlpha));

            if (itemAlpha < 5) {
                posY += itemHeight;
                continue;
            }

            String name = staff.name;
            Text prefix = staff.prefix;

            float elementsWidth = headOffset + Fonts.SFREGULAR.get().getWidth(prefix, 6.75f) + 15;
            float textYOffset = (itemHeight / 2f) - (3f);

            drawBackground(posX, posY, (float) widthAnim2.getValue(), itemHeight, 3, itemAlpha);

            DrawUtil.drawRound((float) (posX + widthAnim2.getValue() - 11.25f), posY + 2, 0.5f, itemHeight - 4, 0, ColorProvider.rgba(125,125,125, itemAlpha));

            float headSize = 8f;
            float headX = posX + 3f;
            float headY = posY + textYOffset - 1f;

            net.minecraft.util.Identifier skinTexture;
            PlayerListEntry playerEntry = mc.getNetworkHandler().getPlayerListEntry(name);
            if (playerEntry != null) {
                skinTexture = playerEntry.getSkinTextures().texture();
            } else {
                skinTexture = DefaultSkinHelper.getTexture();
            }

            int textureId = mc.getTextureManager().getTexture(skinTexture).getGlId();

            dev.endless.util.render.renderers.impl.BuiltTexture headBuilt = Builder.texture()
                    .size(new SizeState(headSize, headSize))
                    .radius(new QuadRadiusState(2))
                    .color(new QuadColorState(ColorProvider.setAlpha(-1, itemAlpha)))
                    .texture(8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, textureId)
                    .smoothness(1f)
                    .build();

            headBuilt.render(context.getMatrices().peek().getPositionMatrix(), headX, headY);

            DrawUtil.drawText(Fonts.SFMEDIUM.get(), prefix, posX + 2f + headOffset, posY + textYOffset - 0.5f, 6.5f, itemAlpha);

            DrawUtil.drawRound((float) (posX + widthAnim2.getValue() - 8), posY + textYOffset + 1f, 5, 5, 2, staff.status == Status.NONE ? ColorProvider.rgba(32,255,32, itemAlpha) : ColorProvider.rgba(255,32,32, itemAlpha));

            if (elementsWidth > defaultWidth) {
                defaultWidth = elementsWidth;
            }

            posY += itemHeight;
        }

        widthAnim2.run(defaultWidth);
        staffListDrag.setWidth((float) widthAnim2.getValue());
        staffListDrag.setHeight(height);
    }
    private final Animation widthAnim3 = new Animation(Easing.EXPO_OUT, 200);
    private final Animation xLine2 = new Animation(Easing.EXPO_OUT, 170);
    private final Animation alpha3 = new Animation(Easing.EXPO_OUT, 200);
    private void renderPotions(DrawContext context) {
        if (mc.player == null) return;
        renderPotionsNew(context);
    }
    private void renderPotionsNew(DrawContext context) {
        if (mc.player == null) return;

        float posX = potionsDrag.getX();
        float posY = potionsDrag.getY();

        float headerHeight = 15f;
        float itemHeight = 12f;
        float minWidth = 130f;

        potionItems.sort(java.util.Comparator.comparing(pi -> pi.name));

        boolean isFound = false;
        for (PotionItem item : potionItems) {
            item.animation.run(item.active ? 1 : 0);
            if (item.animation.getValue() > 0.001f) isFound = true;
        }

        if (!isFound && !(mc.currentScreen instanceof ChatScreen)) alpha3.run(0);
        else alpha3.run(1);

        float globalAlpha = (float) alpha3.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        // Считаем итоговую ширину контента: иконка + name + time.
        float maxNameW = 0f;
        float maxTimeW = 0f;
        int visible = 0;
        for (PotionItem item : potionItems) {
            if (item.animation.getValue() <= 0.001f) continue;
            visible++;
            String ampStr = item.amplifier >= 1 ? " " + (item.amplifier + 1) : "";
            String nameStr = item.name + ampStr;

            int seconds = item.durationTicks / 20;
            String timeStr = String.format("%d:%02d", seconds / 60, seconds % 60);

            float nw = Fonts.SFMEDIUM.get().getWidth(nameStr, 6.75f);
            if (nw > maxNameW) maxNameW = nw;
            float tw = Fonts.SFMEDIUM.get().getWidth(timeStr, 6.75f);
            if (tw > maxTimeW) maxTimeW = tw;
        }
        // 4 (left pad) + 8 (icon) + 4 (gap) + name + 8 (gap) + time + 6 (right pad)
        float boxWidth = Math.max(keyBindsDrag.getWidth(), 4f + 8f + 4f + maxNameW + 8f + maxTimeW + 6f);
        float totalHeight = headerHeight + Math.max(0, visible) * itemHeight + 2f;

        // Один общий фон для всего блока (темнее стандартного).
        drawHeaderBackground(posX, posY, boxWidth, totalHeight, 3f, headerAlpha);

        // Header: иконка слева, надпись правее.
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "E",
                posX + 5f, posY + 3.5f,
                ColorProvider.setAlpha(ColorProvider.getColorIcons(), headerAlpha), 9);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "Potions",
                posX + 5f + 11f, posY + 3f,
                ColorProvider.rgba(255, 255, 255, headerAlpha), 7.5f);

        // Разделительная полоска под заголовком
        DrawUtil.drawRound(posX + 5f, posY + headerHeight - 1.5f, boxWidth - 10f, 0.5f, 0.25f,
                ColorProvider.rgba(255, 255, 255, (int)(25 * (headerAlpha / 255f))));

        float curY = posY + headerHeight;
        for (PotionItem item : potionItems) {
            float animVal = (float) item.animation.getValue();
            if (animVal <= 0.001f) continue;

            int itemAlpha = (int) Math.min(255, Math.max(0, 255 * animVal * globalAlpha));
            if (itemAlpha < 5) {
                curY += itemHeight;
                continue;
            }

            int seconds = item.durationTicks / 20;
            String timeStr = String.format("%d:%02d", seconds / 60, seconds % 60);
            String ampStr = item.amplifier >= 1 ? " " + (item.amplifier + 1) : "";
            String nameStr = item.name + ampStr;

            // Иконка эффекта.
            net.minecraft.client.texture.Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(item.effect);
            if (sprite != null) {
                float iconSize = 8f;
                float iconX = posX + 4f;
                float iconY = curY + (itemHeight - iconSize) / 2f;
                int color = (itemAlpha << 24) | 0xFFFFFF;
                RenderSystem.setShaderColor(1f, 1f, 1f, itemAlpha / 255f);
                context.drawSpriteStretched(net.minecraft.client.render.RenderLayer::getGuiTextured,
                        sprite, (int) iconX, (int) iconY, (int) iconSize, (int) iconSize, color);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }

            // Название эффекта.
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), nameStr,
                    posX + 4f + 8f + 4f, curY + (itemHeight / 2f) - 4f,
                    ColorProvider.rgba(255, 255, 255, itemAlpha), 6.75f);

            // Кулдаун справа.
            float timeW = Fonts.SFMEDIUM.get().getWidth(timeStr, 6.75f);
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), timeStr,
                    posX + boxWidth - timeW - 6f, curY + (itemHeight / 2f) - 4f,
                    ColorProvider.rgba(255, 255, 255, itemAlpha), 6.75f);

            curY += itemHeight;
        }

        potionsDrag.setWidth(boxWidth);
        potionsDrag.setHeight(totalHeight);
    }

    private void renderPotionsClassic(DrawContext context) {
        if (mc.player == null) return;

        float posX = potionsDrag.getX();
        float posY = potionsDrag.getY();

        float headerIconW = Fonts.ICONS_NURIK.get().getWidth("E", 8);
        float headerTextW = Fonts.SFMEDIUM.get().getWidth("Active Potions", 7.5f);
        float defaultWidth = headerIconW + headerTextW + 30;

        float height = 14.5f;

        potionItems.sort(java.util.Comparator.comparing(pi -> pi.name));

        boolean isFound = false;

        for (PotionItem item : potionItems) {
            item.animation.run(item.active ? 1 : 0);
            if (item.animation.getValue() > 0.001f) {
                isFound = true;
            }
            int seconds = item.durationTicks / 20;
            int minutes = seconds / 60;
            int sec = seconds % 60;
            String duration = String.format("%d:%02d", minutes, sec);

            float nameW = Fonts.SFREGULAR.get().getWidth(item.name, 6.5f);
            float ampW = (item.amplifier >= 1 ? Fonts.SFREGULAR.get().getWidth(" " + (item.amplifier + 1), 6.5f) : 0);
            float timeW = Fonts.SFREGULAR.get().getWidth(duration, 6.5f);

            float moduleWidth = nameW + ampW + timeW + 45;

            if (moduleWidth > defaultWidth) {
                defaultWidth = moduleWidth;
            }
        }

        if (!isFound && !(mc.currentScreen instanceof ChatScreen)) alpha3.run(0);
        else alpha3.run(1);

        float globalAlpha = (float) alpha3.getValue();
        if (globalAlpha <= 0.05f) return;

        int headerAlpha = (int) Math.min(255, Math.max(0, 255 * globalAlpha));

        widthAnim3.run(defaultWidth);

        float currentWidth = Math.max(20, (float) widthAnim3.getValue());

        drawBackground(posX, posY, currentWidth - 3, height, 3, headerAlpha);

        DrawUtil.drawRound(posX + 13.75f, posY + 2, 0.5f, 10.5f, 0, ColorProvider.rgba(88,88,88, headerAlpha));
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "E", posX + 4, posY + 3.75f, ColorProvider.rgba(255,255,255, headerAlpha), 8);
        drawHeaderText("Active Potions", posX + 18f, posY + 3.25f, 7.5f, headerAlpha);

        posY += 14.5f;

        xLine2.run(12);

        for (PotionItem item : potionItems) {
            float animVal = (float) item.animation.getValue();
            if (animVal <= 0.001f) continue;

            float heightFactor = Math.min(1.0f, animVal);
            float itemHeight = 12 * heightFactor;
            height += itemHeight;

            float alphaFactor = Math.min(1.0f, Math.max(0.0f, animVal));
            int itemAlpha = (int) (255 * alphaFactor * globalAlpha);
            itemAlpha = Math.min(255, Math.max(0, itemAlpha));

            if (itemAlpha < 5) {
                posY += itemHeight;
                continue;
            }

            String moduleName = item.name;
            int seconds = item.durationTicks / 20;
            int minutes = seconds / 60;
            int sec = seconds % 60;
            String bind = String.format("%d:%02d", minutes, sec);

            float textYOffset = (itemHeight / 2f) - (3f);

            drawBackground(posX, posY, currentWidth - 3, itemHeight, 3, itemAlpha);

            float separatorX = (float) (posX + currentWidth - 6.5f - xLine2.getValue());

            DrawUtil.drawRound(separatorX, posY + 2, 0.5f, itemHeight - 4, 0, ColorProvider.rgba(88,88,88, itemAlpha));

            DrawUtil.drawText(Fonts.SFREGULAR.get(), moduleName, posX + 4, posY + textYOffset - 0.5f, ColorProvider.rgba(255,255,255, itemAlpha), 6.5f);

            if (item.amplifier >= 1) {
                DrawUtil.drawText(Fonts.SFREGULAR.get(), String.valueOf(item.amplifier + 1), posX + 6 + Fonts.SFREGULAR.get().getWidth(moduleName, 6.75f), posY + textYOffset - 0.5f, ColorProvider.setAlpha(ColorProvider.rgba(211,211,211,255), itemAlpha), 6.5f);
            }

            float timeWidth = Fonts.SFREGULAR.get().getWidth(bind, 6.75f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), bind, separatorX - timeWidth - 3f, posY + textYOffset - 0.5f, ColorProvider.rgba(255,255,255, itemAlpha), 6.5f);

            net.minecraft.client.texture.Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(item.effect);
            if (sprite != null) {
                RenderSystem.setShaderColor(1f, 1f, 1f, (itemAlpha / 255f));
                float iconSize = 9;
                float iconX = separatorX + 3.5f;
                float iconY = posY + (itemHeight - iconSize) / 2f;

                int color = (itemAlpha << 24) | 0xFFFFFF;

                context.drawSpriteStretched(
                        net.minecraft.client.render.RenderLayer::getGuiTextured,
                        sprite,
                        (int) iconX,
                        (int) iconY,
                        (int) iconSize,
                        (int) iconSize,
                        color
                );
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }

            posY += itemHeight;
        }

        widthAnim3.run(defaultWidth);
        potionsDrag.setWidth((float) widthAnim3.getValue());
        potionsDrag.setHeight(height);
    }
    public void update() {
        for (Staff staff : staffPlayers) {
            staff.isOnServer = false;
        }

        for (PlayerListEntry playerListEntry : mc.getNetworkHandler().getPlayerList()) {
            String name = playerListEntry.getProfile().getName().replaceAll("[\\[\\]]", "");
            PlayerListEntry info = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(name);
            boolean vanish = info == null;
            boolean isGM3 = info != null && info.getGameMode() == GameMode.SPECTATOR;

            boolean matchesPrefix = prefixMatches.matcher(playerListEntry.getDisplayName() != null ? playerListEntry.getDisplayName().getString().toLowerCase(Locale.ROOT) : "").matches();
            boolean isValidName = namePattern.matcher(name).matches();
            boolean notSelf = !name.equals(MinecraftClient.getInstance().player.getName().getString());

            if ((isValidName && notSelf && matchesPrefix) || (isValidName && notSelf && vanish) || StaffManager.isStaff(name)) {
                if (StaffManager.isStaff(name)) {
                    String[] names = new String[]{"auction", "exp_smith", "shop_balls", "shop_grief", "free", "shop_kits", "siege", "rwplus", "bossfight", "guide", "shop_smith", "shop_spawners", "colliseum", "battlepass", "buyer", "huckster", "buff_brewer", "killer", "shop_mage"};
                    boolean contains = false;
                    if (MinecraftClient.getInstance().getCurrentServerEntry() != null && MinecraftClient.getInstance().getCurrentServerEntry().address != null && (MinecraftClient.getInstance().getCurrentServerEntry().address.contains("mc.rwdonat.pw") || MinecraftClient.getInstance().getCurrentServerEntry().address.contains("mc.cakeworld.pw"))) {
                        for (int i = 0; i < Arrays.stream(names).count(); i++) {
                            if (name.contains(names[i])) {
                                contains = true;
                                break;
                            }
                        }
                    }
                    if (contains) continue;
                }
                Optional<Staff> existingStaff = staffPlayers.stream().filter(s -> s.name.equals(name)).findFirst();

                Status status = vanish ? Status.VANISHED : (isGM3 ? Status.VANISHED : Status.NONE);

                if (existingStaff.isPresent()) {
                    Staff s = existingStaff.get();
                    s.isOnServer = true;
                    s.status = status;
                } else {
                    String[] names = new String[]{"auction", "exp_smith", "shop_balls", "shop_grief", "free", "shop_kits", "siege", "rwplus", "bossfight", "guide", "shop_smith", "shop_spawners", "colliseum", "battlepass", "buyer", "huckster", "buff_brewer", "killer", "shop_mage"};
                    boolean contains = false;
                    if (MinecraftClient.getInstance().getCurrentServerEntry() != null && MinecraftClient.getInstance().getCurrentServerEntry().address != null && (MinecraftClient.getInstance().getCurrentServerEntry().address.contains("mc.rwdonat.pw") || MinecraftClient.getInstance().getCurrentServerEntry().address.contains("mc.cakeworld.pw"))) {
                        for (int i = 0; i < Arrays.stream(names).count(); i++) {
                            if (name.contains(names[i])) {
                                contains = true;
                            }
                        }
                    }
                    if (!contains) {
                        Text originalPrefix = playerListEntry.getDisplayName();
                        Text prefix = originalPrefix;
                        if (prefix != null) {
                            prefix = ReplaceUtil.replaceSymbols(prefix);
                            String fullString = prefix.getString();
                            int nickIndex = fullString.indexOf(name);
                            if (nickIndex != -1) {
                                int endIndex = nickIndex + name.length();
                                if (endIndex < fullString.length()) {
                                    net.minecraft.text.MutableText newText = Text.empty();
                                    int currentLength = 0;
                                    net.minecraft.text.MutableText baseCopy = prefix.copy();
                                    baseCopy.getSiblings().clear();
                                    String mainContent = baseCopy.getString();

                                    if (!mainContent.isEmpty() && currentLength < endIndex) {
                                        int takeLength = Math.min(mainContent.length(), endIndex - currentLength);
                                        newText.append(Text.literal(mainContent.substring(0, takeLength)).setStyle(prefix.getStyle()));
                                        currentLength += takeLength;
                                    }

                                    for (Text sibling : prefix.getSiblings()) {
                                        if (currentLength >= endIndex) break;
                                        net.minecraft.text.MutableText siblingCopy = sibling.copy();
                                        siblingCopy.getSiblings().clear();
                                        String siblingContent = siblingCopy.getString();

                                        int takeLength = Math.min(siblingContent.length(), endIndex - currentLength);
                                        if (takeLength > 0) {
                                            newText.append(Text.literal(siblingContent.substring(0, takeLength)).setStyle(sibling.getStyle()));
                                            currentLength += takeLength;
                                        }
                                    }

                                    prefix = newText;
                                }
                            }
                        }
                        Staff staff = new Staff(prefix == null ? Text.of(playerListEntry.getProfile().getName()) : prefix, name, vanish || isGM3, status);
                        staff.isOnServer = true;
                        staffPlayers.add(staff);
                    }
                }
            }
        }

        staffPlayers.removeIf(staff -> !staff.isOnServer && staff.animation.getValue() == 0);
    }

    public enum Status {
        NONE("", -1),
        VANISHED("SPEC", ColorProvider.rgba(229, 0, 63, 255));

        public final String string;
        public final int color;

        Status(String string, int color) {
            this.string = string;
            this.color = color;
        }
    }

    public static class Staff {
        Text prefix;
        public String name;
        boolean isSpec;
        Status status;
        boolean isOnServer;
        Animation animation;
        long mills;

        public Staff(Text prefix, String name, boolean isSpec, Status status) {
            this.prefix = prefix;
            this.name = name;
            this.isSpec = isSpec;
            this.status = status;
            animation = new Animation(Easing.EXPO_OUT, 233);
            mills = System.currentTimeMillis();
        }
    }

    public int getPing(PlayerEntity entity) {
        PlayerListEntry list = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return list != null ? list.getLatency() : 0;
    }

    private void renderWatermark(DrawContext context) {
        if (mc.player == null) return;
        renderWatermarkNew(context);
    }

    // Typewriter animation state for watermark title
    private int wmTypeIndex = 0;
    private boolean wmTypeErasing = false;
    private long wmTypeLastMs = 0;
    private static final long WM_TYPE_INTERVAL_MS = 100; // ms per character
    private static final long WM_TYPE_PAUSE_MS = 1500;   // pause at full/empty
    private long wmTypePauseUntil = 0;

    private void renderWatermarkNew(DrawContext context) {
        Counter.updateFPS();

        String fullAlphaStr = "Endless 1.21.4";
        String userText = "user";
        String fpsValue = Counter.getCurrentFPS() + " Fps";

        // ── Typewriter logic ──────────────────────────────────────────────────
        long now = System.currentTimeMillis();
        if (now >= wmTypePauseUntil && now - wmTypeLastMs >= WM_TYPE_INTERVAL_MS) {
            wmTypeLastMs = now;
            if (!wmTypeErasing) {
                wmTypeIndex++;
                if (wmTypeIndex >= fullAlphaStr.length()) {
                    wmTypePauseUntil = now + WM_TYPE_PAUSE_MS;
                    wmTypeErasing = true;
                }
            } else {
                wmTypeIndex--;
                if (wmTypeIndex <= 0) {
                    wmTypeIndex = 0;
                    wmTypePauseUntil = now + WM_TYPE_PAUSE_MS;
                    wmTypeErasing = false;
                }
            }
        }
        String alphaStr = fullAlphaStr.substring(0, MathHelper.clamp(wmTypeIndex, 0, fullAlphaStr.length()));

        float x = watermarkDrag.getX();
        float y = watermarkDrag.getY();
        float height = 16.5f;

        int themeColor = ColorProvider.getColorClient();
        int iconColor = ColorProvider.getColorIcons();
        int whiteColor = -1;

        long time = System.currentTimeMillis();

        float alphaW = Fonts.SFMEDIUM.get().getWidth(alphaStr, 7f);
        
        // Вычисляем ширину всех элементов
        float logoWidth = 17f;
        float userW = Fonts.SFMEDIUM.get().getWidth(userText, 7f);
        float fpsW = Fonts.SFMEDIUM.get().getWidth(fpsValue, 7f);
        
        // Динамическая ширина: зависит от текущей длины печатающегося текста.
        float totalWidth = logoWidth + 2 + alphaW + 5 + 10 + userW + 10 + 10 + fpsW + 10;

        // Рисуем единый фон
        drawHeaderBackground(x, y, totalWidth, height, 3f, 255);

        // Рисуем логотип с анимацией
        float pulse = (float) (Math.sin(time / 200.0) * 0.3 + 0.7);
        int animatedThemeColor = ColorProvider.setAlpha(themeColor, (int)(255 * pulse));
        int logoTexId = mc.getTextureManager().getTexture(WATERMARK_LOGO_TEXTURE).getGlId();
        Builder.texture()
                .size(new SizeState(15f, 15f))
                .radius(QuadRadiusState.NO_ROUND)
                .color(new QuadColorState(animatedThemeColor))
                .texture(0f, 0f, 1f, 1f, logoTexId)
                .smoothness(1f)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x + 1.5f, y + 0.5f);

        // Рисуем текст "Endless 1.21.4" с градиентом после логотипа
        float currentAlphaX = x + logoWidth + 2f;
        int themeDark = ColorProvider.rgba(
                (int)(((themeColor >> 16) & 0xFF) * 0.75f),
                (int)(((themeColor >> 8) & 0xFF) * 0.75f),
                (int)((themeColor & 0xFF) * 0.75f),
                255
        );
        for (int i = 0; i < alphaStr.length(); i++) {
            String ch = String.valueOf(alphaStr.charAt(i));
            int charColor = colorLerp(themeColor, themeDark, 3.0f, i * 0.35f);
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), ch, currentAlphaX, y + 4f, charColor, 7f);
            currentAlphaX += Fonts.SFMEDIUM.get().getWidth(ch, 7f);
        }

        // Рисуем информацию справа
        float currX = currentAlphaX + 5f;
        
        // Иконка и текст пользователя
        DrawUtil.drawText(Fonts.ICONS_MINCED.get(), "n", currX, y + 4.75f, iconColor, 7f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), userText, currX + 10, y + 4f, whiteColor, 7f);
        currX += 10 + userW + 10;
        
        // Иконка и FPS
        DrawUtil.drawText(Fonts.ICONS_MINCED.get(), "c", currX, y + 4.75f, iconColor, 7f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), fpsValue, currX + 10, y + 4f, whiteColor, 7f);

        watermarkDrag.setWidth(totalWidth);
        watermarkDrag.setHeight(height);
    }

    private void renderWatermarkNursultan(DrawContext context) {
        Counter.updateFPS();

        String userText = "user";
        String fpsValue = Counter.getCurrentFPS() + " Fps";
        String timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String coordsText = (int) mc.player.getX() + " " + (int) mc.player.getY() + " " + (int) mc.player.getZ();
        String pingText = Server.getPing(mc.player) + " Ping";
        String tpsText = String.format("%.1f Ticks", Endless.getInstance().getTpsGetter().getTPS());
        double dX = mc.player.getX() - mc.player.prevX;
        double dZ = mc.player.getZ() - mc.player.prevZ;
        String speedText = String.format("%.1f Bps", Math.hypot(dX, dZ) * 20);

        float x = watermarkDrag.getX();
        float y = watermarkDrag.getY();
        float startX = x;
        float height = 15f;
        float gap = 2f;

        int sepColor = ColorProvider.rgba(255, 255, 255, 100);
        int themeColor = ColorProvider.getColorClient();
        int iconColor = ColorProvider.getColorIcons();
        int themeColorTwo = ColorProvider.getColorClient();
        int whiteColor = -1;

        long time = System.currentTimeMillis();

        String alphaStr = "Endless DLC";
        float alphaW = Fonts.SFMEDIUM.get().getWidth(alphaStr, 7f);
        float firstBoxWidth = 17f + 10f + alphaW;

        drawBackground(x, y, firstBoxWidth, height, 2f, 255);

        float pulse = (float) (Math.sin(time / 200.0) * 0.3 + 0.7);
        int animatedThemeColor = ColorProvider.setAlpha(themeColor, (int)(255 * pulse));
        int logoTexId = mc.getTextureManager().getTexture(WATERMARK_LOGO_TEXTURE).getGlId();
        Builder.texture()
                .size(new SizeState(15f, 15f))
                .radius(QuadRadiusState.NO_ROUND)
                .color(new QuadColorState(animatedThemeColor))
                .texture(0f, 0f, 1f, 1f, logoTexId)
                .smoothness(1f)
                .build()
                .render(context.getMatrices().peek().getPositionMatrix(), x + 1.5f, y + 0f);
        DrawUtil.drawRound(x + 18f, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);

        float currentAlphaX = x + 22f;
        int themeDark = ColorProvider.rgba(
                (int)(((themeColor >> 16) & 0xFF) * 0.55f),
                (int)(((themeColor >> 8) & 0xFF) * 0.55f),
                (int)((themeColor & 0xFF) * 0.55f),
                255
        );
        for (int i = 0; i < alphaStr.length(); i++) {
            String ch = String.valueOf(alphaStr.charAt(i));
            int charColor = colorLerp(themeColor, themeDark, 3.0f, i * 0.35f);
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), ch, currentAlphaX, y + 3.5f, charColor, 7f);
            currentAlphaX += Fonts.SFMEDIUM.get().getWidth(ch, 7f);
        }

        float firstRowX = x + firstBoxWidth + gap;
        float userW = Fonts.SFMEDIUM.get().getWidth(userText, 7f);
        float fpsW = Fonts.SFMEDIUM.get().getWidth(fpsValue, 7f);
        float timeW = Fonts.SFMEDIUM.get().getWidth(timeText, 7f);
        float wCombined = 4 + 10 + userW + 5 + 1 + 5 + 10 + fpsW + 5 + 1 + 5 + 10 + timeW + 6;

        drawBackground(firstRowX, y, wCombined, height, 2f, 255);

        float currX = firstRowX + 4;
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0057", currX, y + 4.25f, iconColor, 7f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), userText, currX + 10, y + 3.5f, whiteColor, 7f);
        currX += 11 + userW + 5;
        DrawUtil.drawRound(currX, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        currX += 6;
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0058", currX, y + 4.25f, iconColor, 7f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), fpsValue, currX + 11, y + 3.5f, whiteColor, 7f);
        currX += 11 + fpsW + 5;
        DrawUtil.drawRound(currX, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        currX += 6;
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0056", currX, y + 4.25f, iconColor, 7f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), timeText, currX + 11, y + 3.5f, whiteColor, 7f);

        float row1Width = (firstRowX + wCombined) - startX;
        x = startX;
        y += height + gap;

        float pulse2 = (float) (Math.sin((time + 150) / 250.0) * 0.3 + 0.7);
        int animatedThemeColor2 = ColorProvider.setAlpha(themeColor, (int)(255 * pulse2));

        float wCoords = 17 + Fonts.SFMEDIUM.get().getWidth(coordsText, 7f) + 4;
        drawBackground(x, y, wCoords, height, 2f, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0046", x + 4, y + 4.25f, iconColor, 7f);
        DrawUtil.drawRound(x + 13, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), coordsText, x + 17, y + 3.5f, whiteColor, 7f);
        x += wCoords + gap;

        float wPing = 17 + Fonts.SFMEDIUM.get().getWidth(pingText, 7f) + 4;
        drawBackground(x, y, wPing, height, 2f, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0051", x + 4, y + 4.25f, iconColor, 7f);
        DrawUtil.drawRound(x + 13.5f, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), pingText, x + 17, y + 3.5f, whiteColor, 7f);
        x += wPing + gap;

        float wTps = 17 + Fonts.SFMEDIUM.get().getWidth(tpsText, 7f) + 4;
        drawBackground(x, y, wTps, height, 2f, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0024", x + 4, y + 4.25f, iconColor, 7f);
        DrawUtil.drawRound(x + 13, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), tpsText, x + 17, y + 3.5f, whiteColor, 7f);
        x += wTps + gap;

        float wSpeed = 20 + Fonts.SFMEDIUM.get().getWidth(speedText, 7f) + 4;
        drawBackground(x, y, wSpeed, height, 2f, 255);
        DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "\u0040", x + 4, y + 4.25f, iconColor, 7f);
        DrawUtil.drawRound(x + 15, y + 3.5f, 0.5f, height - 7f, 0.2f, sepColor);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), speedText, x + 20, y + 3.5f, whiteColor, 7f);

        float row2Width = (x + wSpeed) - startX;

        watermarkDrag.setWidth(Math.max(row1Width, row2Width));
        watermarkDrag.setHeight((height * 2) + gap);
    }
    private void drawHeaderText(String text, float x, float y, float size, int alpha) {
        int themeColor = ColorProvider.getColorClient();
        int themeDark = ColorProvider.rgba(
                (int)(((themeColor >> 16) & 0xFF) * 0.75f),
                (int)(((themeColor >> 8) & 0xFF) * 0.75f),
                (int)((themeColor & 0xFF) * 0.75f),
                255
        );
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int charColor = colorLerp(themeColor, themeDark, 3.0f, i * 0.35f);
            charColor = ColorProvider.setAlpha(charColor, alpha);
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), ch, cx, y, charColor, size);
            cx += Fonts.SFMEDIUM.get().getWidth(ch, size);
        }
    }

    private int colorLerp(int start, int end, float speed, float offset) {
        long t = System.currentTimeMillis();
        double ph = t * (speed / 1000.0) + offset;
        float p = (float) (Math.sin(ph) * 0.5 + 0.5);

        int sr = (start >> 16) & 0xFF;
        int sg = (start >> 8) & 0xFF;
        int sb = start & 0xFF;
        int er = (end >> 16) & 0xFF;
        int eg = (end >> 8) & 0xFF;
        int eb = end & 0xFF;

        int r = (int) (sr * (1f - p) + er * p);
        int g = (int) (sg * (1f - p) + eg * p);
        int b = (int) (sb * (1f - p) + eb * p);

        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderCoordsInfo(DrawContext context) {
        if (mc.player == null) return;

        String coordsText = "x: " + (int) mc.player.getX()
                + "  y: " + (int) mc.player.getY()
                + "  z: " + (int) mc.player.getZ();

        // Размещаем чуть ниже основного блока ватермарки.
        float gap = 2f;
        float height = Math.max(1f, watermarkDrag.getHeight());
        if (height <= 1f) height = 16.5f;

        // Ширина = иконка + текст + правый отступ; всегда подгоняется под значения.
        float iconLeftPad = 5f;
        float iconWidth = 8f;        // визуальная ширина иконки (с зазором)
        float textGap = 4f;          // отступ от иконки до текста
        float rightPad = 6f;         // небольшой отступ после z
        float coordsW = Fonts.SFMEDIUM.get().getWidth(coordsText, 7f);
        float totalWidth = iconLeftPad + iconWidth + textGap + coordsW + rightPad;

        float x = watermarkDrag.getX();
        float y = watermarkDrag.getY() + watermarkDrag.getHeight() + gap;

        drawHeaderBackground(x, y, totalWidth, height, 3f, 255);

        int iconColor = ColorProvider.getColorIcons();
        int whiteColor = -1;

        // Иконка категории Misc.
        DrawUtil.drawText(Fonts.ICONS_MINCED.get(), "e", x + iconLeftPad, y + 4.75f, iconColor, 7f);
        // Координаты правее иконки.
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), coordsText,
                x + iconLeftPad + iconWidth + textGap, y + 4f, whiteColor, 7f);
    }

    private void renderSpeed(DrawContext context) {
        if (mc.player == null) return;


        double deltaX = mc.player.getX() - mc.player.prevX;
        double deltaY = mc.player.getY() - mc.player.prevY;
        double deltaZ = mc.player.getZ() - mc.player.prevZ;
        double speedBps = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 20;


        String text = String.format(java.util.Locale.US, "%.2f", speedBps);
        float fontSize = 11f;
        float textWidth = Fonts.SFBOLD.get().getWidth(text, fontSize);


        float x = mc.getWindow().getScaledWidth() / 2f - (textWidth / 2f);
        float y = mc.getWindow().getScaledHeight() / 2f + 12f;

        float rectW = textWidth + 10f;
        float rectH = 12f;





        DrawUtil.drawText(Fonts.SFBOLD.get(), text, x, y, -1, fontSize);
    }

    private void renderTargetHUDClassic(DrawContext context) {
        KillAura killAura = Instance.get(KillAura.class);
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        LivingEntity target = null;
        if (killAura.isEnabled() && killAura.getTarget() != null && killAura.getTarget().isAlive()) {
            target = killAura.getTarget();
        }
        else if (mc.targetedEntity instanceof LivingEntity living && living.isAlive()) {
            target = living;
        }
        else if (chatOpen) {
            target = mc.player;
        }
        if (target != null) {
            lastTarget = target;
            animation.run(1);
            armorAnim.run(1);
        } else {
            animation.run(0);
            armorAnim.run(0);
        }

        if (animation.getValue() <= 0.05f || lastTarget == null || !(lastTarget instanceof LivingEntity)) return;

        LivingEntity livingEntity = (LivingEntity) lastTarget;
        AbstractClientPlayerEntity playerEntity = lastTarget instanceof AbstractClientPlayerEntity ? (AbstractClientPlayerEntity) lastTarget : null;

        float anim = (float) animation.getValue();
        int alphaInt = (int) (255 * anim);

        float width = 95;
        float height = 36;
        float x = targetHUDDrag.getX();
        float y = targetHUDDrag.getY();

        drawHeaderBackground(x, y, width, height, 4, alphaInt);

        float headSize = 30f; // Увеличена голова с 22f до 30f
        float headX = x + 3;
        float headY = y + 2f;
        float currentHpRaw = dev.endless.util.server.Server.getHealth(livingEntity, false);

        if (lastHpRaw == -1f || lastTarget != livingEntity) {
            lastHpRaw = currentHpRaw;
            headParticles.clear();
        }

        if (currentHpRaw < lastHpRaw) {
            lastHpRaw = currentHpRaw;
        } else if (currentHpRaw > lastHpRaw) {
            lastHpRaw = currentHpRaw;
        }

        float hurtPercent = livingEntity.hurtTime / 10f;
        int headColor = ColorProvider.rgba(255, (int)(255 * (1 - hurtPercent)), (int)(255 * (1 - hurtPercent)), alphaInt);

        if (playerEntity != null) {
            try {
                net.minecraft.util.Identifier skin = playerEntity.getSkinTextures().texture();
                int texId = mc.getTextureManager().getTexture(skin).getGlId();

                dev.endless.util.render.renderers.impl.BuiltTexture headTexture = Builder.texture()
                        .size(new SizeState(headSize, headSize))
                        .radius(new QuadRadiusState(5))
                        .color(new QuadColorState(headColor))
                        .texture(8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, texId)
                        .smoothness(1f)
                        .build();

                headTexture.render(context.getMatrices().peek().getPositionMatrix(), headX, headY);
            } catch (Exception ignored) {}
        } else {
            DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "N", headX + 1, headY + 8, headColor, 26f);
        }

        float textX = x + 34; // Сдвинуто вправо с 26 до 34 (голова теперь больше)

        dev.endless.module.list.misc.NameProtect nameProtect = Instance.get(dev.endless.module.list.misc.NameProtect.class);

        String name = nameProtect.isEnabled() ? nameProtect.getCustomName(livingEntity.getName().getString()) : livingEntity.getName().getString();

        Scissor.push();
        Scissor.setFromComponentCoordinates(textX, y, width - 42, height);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), name, textX + 0.5f, y + 4, ColorProvider.rgba(255, 255, 255, alphaInt), 8f);
        Scissor.unset();
        Scissor.pop();

        float currentHp = dev.endless. util.server.Server.getHealth(livingEntity, false);
        if (Float.isNaN(currentHp) || currentHp < 0) currentHp = 0;

        String hpText = String.format(java.util.Locale.US, "HP: %.1f", currentHp);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), hpText, textX + 1, y + 14.5f, ColorProvider.rgba(255, 255, 255, alphaInt), 6.75f);

        float absorption = livingEntity.getAbsorptionAmount();
        if (absorption > 0) {
            String absText = String.format(java.util.Locale.US,"(+ %.1f)", absorption);
            float offset = Fonts.SFMEDIUM.get().getWidth(hpText, 6.5f) + 3;
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), absText, textX + offset + 3, y + 14.5f, ColorProvider.rgba(255, 215, 0, alphaInt), 6.5f);
        }

        float barX = x + 34; // HP бар начинается после головы (было x + 3)
        float barY = y + 24; // Позиция HP бара
        float barWidth = width - 37; // Уменьшена ширина бара (было width - 6)
        float barHeight = 7f;

        float maxHealth = livingEntity.getMaxHealth();
        float hpPercent = MathHelper.clamp(currentHp / maxHealth, 0, 1);

        hpAnimation.run(hpPercent);
        outdatedHpAnimation.run(hpPercent);

        float hpWNow = barWidth * (float) hpAnimation.getValue();
        float hpWOld = barWidth * (float) outdatedHpAnimation.getValue();

        int hpLeftFull, hpRightFull;

        int c1 = ColorProvider.getColorClient();
        int c1r = (c1 >> 16) & 0xFF;
        int c1g = (c1 >> 8) & 0xFF;
        int c1b = c1 & 0xFF;
        int c1dark = ColorProvider.rgba((int)(c1r * 0.25f), (int)(c1g * 0.25f), (int)(c1b * 0.25f), 255);

        hpRightFull = ColorProvider.setAlpha(c1, alphaInt);
        hpLeftFull = ColorProvider.setAlpha(c1dark, alphaInt);

        int backColor = ColorProvider.rgba(20, 20, 20, (int)(120 * anim));

        DrawUtil.drawRound(barX, barY, barWidth, barHeight, 1.5f, backColor);

        if (hpWOld > hpWNow + 0.5f) {
            DrawUtil.drawRound(barX, barY, hpWOld, barHeight, 1.5f, ColorProvider.setAlpha(c1, (int)(135 * anim)));
        }

        if (hpWNow > 0.5f) {
            DrawUtil.drawRound(barX, barY, hpWNow, barHeight, 1.5f, hpLeftFull, hpLeftFull, hpRightFull, hpRightFull);
        }

        float absPercent = MathHelper.clamp(livingEntity.getAbsorptionAmount() / maxHealth, 0, 1);
        absorptionAnimation.run(barWidth * absPercent);
        absorptionTrailAnimation.run(barWidth * absPercent);
        float abWNow = (float) absorptionAnimation.getValue();
        float abWOld = (float) absorptionTrailAnimation.getValue();

        if (abWNow > 0.5f) {
            int absLeftColor = ColorProvider.rgba(140, 120, 0, (int)(200 * anim));
            int absRightColor = ColorProvider.rgba(255, 215, 0, (int)(255 * anim));
            if (abWOld > abWNow + 0.5f) {
                DrawUtil.drawRound(barX - 0.25f, barY, abWOld, barHeight, 1.5f,
                        ColorProvider.rgba(200, 170, 0, (int)(190 * anim)), ColorProvider.rgba(200, 170, 0, (int)(190 * anim)),
                        ColorProvider.rgba(255, 215, 0, (int)(190 * anim)), ColorProvider.rgba(255, 215, 0, (int)(190 * anim)));
            }
            DrawUtil.drawRound(barX - 0.25f, barY, abWNow, barHeight, 1.5f,
                    absLeftColor, absLeftColor, absRightColor, absRightColor);
        }

        float armorAlpha = (float) armorAnim.getValue();
        if (armorAlpha > 0.05f) {
            List<ItemStack> items = new ArrayList<>();
            items.add(livingEntity.getMainHandStack());
            for (ItemStack stack : livingEntity.getArmorItems()) items.add(stack);
            items.add(livingEntity.getOffHandStack());
            Collections.reverse(items);

            float itemScale = 0.65f;
            float slotSize = 16 * itemScale;
            float itemX = x + width - (slotSize * 6) - 5;
            float itemY = y - slotSize - 2;

            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 100);
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            for (ItemStack stack : items) {
                if (stack.isEmpty()) {
                    itemX += slotSize;
                    continue;
                }
                context.getMatrices().push();
                context.getMatrices().translate(itemX, itemY, 0);
                context.getMatrices().scale(armorAlpha * itemScale, armorAlpha * itemScale, 1f);
                context.drawItem(stack, 0, 0);
                context.drawStackOverlay(textRenderer, stack, 0, 0);
                context.getMatrices().pop();
                itemX += slotSize;
            }
            context.getMatrices().pop();
        }

        targetHUDDrag.setWidth(width);
        targetHUDDrag.setHeight(height);
    }

    private float trailHealthPercent = 1f;
    private float lastHealthPercent = 1f;
    private float lastHpRaw = -1f;




    private final List<HeadParticle> headParticles = new ArrayList<>();

    private static class HeadParticle {
        float x, y, vx, vy, size;
        long spawnTime;
        int color;

        HeadParticle(float startX, float startY, int color) {
            this.x = startX;
            this.y = startY;
            double angle = Math.random() * Math.PI * 2;
            double speed = Math.random() * 0.4 + 0.1;
            this.vx = (float) (Math.cos(angle) * speed);
            this.vy = (float) (Math.sin(angle) * speed);
            this.size = (float) (Math.random() * 8 + 2);
            this.spawnTime = System.currentTimeMillis();
            this.color = color;
        }

        void update() {
            x += vx;
            y += vy;
        }

        float getAlpha() {
            long elapsed = System.currentTimeMillis() - spawnTime;
            if (elapsed >= 2000) return 0;
            return 1f - ((float) elapsed / 2000f);
        }
    }
    public void drawEntity(float x, float y, float scale, float yawAngle, float pitchAngle, net.minecraft.entity.LivingEntity entity) {
        MatrixStack matrices = new MatrixStack();
        matrices.push();
        matrices.translate(x, y, 50.0);
        matrices.scale(-scale, scale, scale);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(yawAngle));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(pitchAngle));

        float bodyYaw = entity.bodyYaw;
        float prevBodyYaw = entity.prevBodyYaw;
        float headYaw = entity.headYaw;
        float prevHeadYaw = entity.prevHeadYaw;
        float yaw = entity.getYaw();
        float prevYaw = entity.prevYaw;
        float pitch = entity.getPitch();
        float prevPitch = entity.prevPitch;

        entity.bodyYaw = 0;
        entity.prevBodyYaw = 0;
        entity.headYaw = 0;
        entity.prevHeadYaw = 0;
        entity.setYaw(0);
        entity.prevYaw = 0;
        entity.setPitch(0);
        entity.prevPitch = 0;

        net.minecraft.client.render.DiffuseLighting.disableGuiDepthLighting();
        net.minecraft.client.render.VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);
        mc.getEntityRenderDispatcher().render(entity, 0.0, 0.0, 0.0, tickDelta, matrices, immediate, 0x00F000F0);

        immediate.draw();
        net.minecraft.client.render.DiffuseLighting.enableGuiDepthLighting();

        entity.bodyYaw = bodyYaw;
        entity.prevBodyYaw = prevBodyYaw;
        entity.headYaw = headYaw;
        entity.prevHeadYaw = prevHeadYaw;
        entity.setYaw(yaw);
        entity.prevYaw = prevYaw;
        entity.setPitch(pitch);
        entity.prevPitch = prevPitch;

        matrices.pop();
    }



    private java.awt.Color lerpColor(java.awt.Color a, java.awt.Color b, float t) {
        return new java.awt.Color(
                (int) (a.getRed() + t * (b.getRed() - a.getRed())),
                (int) (a.getGreen() + t * (b.getGreen() - a.getGreen())),
                (int) (a.getBlue() + t * (b.getBlue() - a.getBlue()))
        );
    }

    private static class PotionItem {
        String name;
        int amplifier;
        int durationTicks;
        boolean active;
        net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect;
        Animation animation = new Animation(Easing.EXPO_OUT, 233);

        PotionItem(String name, int amplifier, int durationTicks, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect) {
            this.name = name;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
            this.effect = effect;
            this.active = true;
        }
    }

    private final java.util.List<PotionItem> potionItems = new CopyOnWriteArrayList<>();

    private void updatePotions() {
        java.util.Map<String, StatusEffectInstance> currentEffects = mc.player.getStatusEffects().stream()
                .collect(Collectors.toMap(
                        e -> net.minecraft.text.Text.translatable(e.getTranslationKey()).getString() + ":" + e.getAmplifier(),
                        e -> e,
                        (e1, e2) -> e1
                ));

        potionItems.forEach(item -> {
            String key = item.name + ":" + item.amplifier;
            StatusEffectInstance effect = currentEffects.get(key);

            if (effect != null) {
                item.durationTicks = effect.getDuration();
                if (!item.active) {
                    item.animation.setValue(1.0f);
                }
                item.active = true;
                currentEffects.remove(key);
            } else {
                item.active = false;
            }
        });

        currentEffects.forEach((key, effect) -> {
            potionItems.add(new PotionItem(
                    net.minecraft.text.Text.translatable(effect.getTranslationKey()).getString(),
                    effect.getAmplifier(),
                    effect.getDuration(),
                    effect.getEffectType()
            ));
        });

        potionItems.removeIf(item -> !item.active && item.animation.getValue() == 0);
    }

    private void renderTargetHUDNew(DrawContext context) {
        KillAura killAura = Instance.get(KillAura.class);
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        LivingEntity target = null;
        if (killAura.isEnabled() && killAura.getTarget() != null && killAura.getTarget().isAlive()) {
            target = killAura.getTarget();
        }
        else if (mc.targetedEntity instanceof LivingEntity living && living.isAlive()) {
            target = living;
        }
        else if (chatOpen) {
            target = mc.player;
        }
        if (target != null) {
            lastTarget = target;
            animation.run(1);
            armorAnim.run(1);
        } else {
            animation.run(0);
            armorAnim.run(0);
        }

        if (animation.getValue() <= 0.05f || lastTarget == null || !(lastTarget instanceof LivingEntity)) return;

        LivingEntity livingEntity = (LivingEntity) lastTarget;
        AbstractClientPlayerEntity playerEntity = lastTarget instanceof AbstractClientPlayerEntity ? (AbstractClientPlayerEntity) lastTarget : null;

        float anim = (float) animation.getValue();
        int alphaInt = (int) (255 * anim);

        // Получаем имя игрока
        dev.endless.module.list.misc.NameProtect nameProtect = Instance.get(dev.endless.module.list.misc.NameProtect.class);
        String name = nameProtect.isEnabled() ? nameProtect.getCustomName(livingEntity.getName().getString()) : livingEntity.getName().getString();

        // Фиксированная ширина блока — больше не растягивается под длину имени.
        // Длинные имена плавно фейдятся (см. ниже).
        float width = 110f;

        float height = 36;
        float x = targetHUDDrag.getX();
        float y = targetHUDDrag.getY();

        drawHeaderBackground(x, y, width, height, 4, alphaInt);

        float headSize = 24f;
        float headX = x + 6;
        float headY = y + 6f;
        float currentHpRaw = dev.endless.util.server.Server.getHealth(livingEntity, false);

        if (lastHpRaw == -1f || lastTarget != livingEntity) {
            lastHpRaw = currentHpRaw;
            headParticles.clear();
        }

        if (currentHpRaw < lastHpRaw) {
            lastHpRaw = currentHpRaw;
        } else if (currentHpRaw > lastHpRaw) {
            lastHpRaw = currentHpRaw;
        }

        float hurtPercent = livingEntity.hurtTime / 10f;
        int headColor = ColorProvider.rgba(255, (int)(255 * (1 - hurtPercent)), (int)(255 * (1 - hurtPercent)), alphaInt);

        // Подложка для обводки HP — рисуется ДО головы, голова перекроет центр.
        // Это и есть индикатор: при потере HP подложка обрезается справа налево.
        float hpForBorder = dev.endless.util.server.Server.getHealth(livingEntity, false);
        if (Float.isNaN(hpForBorder) || hpForBorder < 0) hpForBorder = 0;
        float maxHpForBorder = livingEntity.getMaxHealth();
        float hpPercentBorder = MathHelper.clamp(hpForBorder / Math.max(1f, maxHpForBorder), 0f, 1f);
        hpAnimation.run(hpPercentBorder);
        float animHpBorder = (float) hpAnimation.getValue();

        float borderThick = 2f;
        float bgX = headX - borderThick;
        float bgY = headY - borderThick;
        float bgW = headSize + borderThick * 2f;
        float bgH = headSize + borderThick * 2f;

        if (animHpBorder > 0.01f) {
            float clipWidth = bgW * animHpBorder;
            dev.endless.util.render.math.Scissor.push();
            dev.endless.util.render.math.Scissor.setFromComponentCoordinates(bgX, bgY, clipWidth, bgH);

            int themeColor1 = ColorProvider.setAlpha(ColorProvider.getThemeColor(), alphaInt);
            int themeColor2 = ColorProvider.setAlpha(ColorProvider.getThemeColorTwo(), alphaInt);

            DrawUtil.drawRound(bgX, bgY, bgW, bgH, 6f, themeColor1, themeColor2, themeColor2, themeColor1);

            dev.endless.util.render.math.Scissor.unset();
            dev.endless.util.render.math.Scissor.pop();
        }

        if (playerEntity != null) {
            try {
                net.minecraft.util.Identifier skin = playerEntity.getSkinTextures().texture();
                int texId = mc.getTextureManager().getTexture(skin).getGlId();

                dev.endless.util.render.renderers.impl.BuiltTexture headTexture = Builder.texture()
                        .size(new SizeState(headSize, headSize))
                        .radius(new QuadRadiusState(5))
                        .color(new QuadColorState(headColor))
                        .texture(8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, texId)
                        .smoothness(1f)
                        .build();

                headTexture.render(context.getMatrices().peek().getPositionMatrix(), headX, headY);
            } catch (Exception ignored) {}
        } else {
            DrawUtil.drawText(Fonts.ICONS_NURIK.get(), "N", headX + 1, headY + 8, headColor, 26f);
        }

        float textX = x + 36;

        // Длинные имена плавно фейдятся: до 8 символов — 100%,
        // после 8 — линейно до ~25% к 14 символам.
        int nameLen = name.length();
        float lengthFade = 1.0f;
        if (nameLen > 8) {
            float t = MathHelper.clamp((nameLen - 8) / 6.0f, 0f, 1f);
            lengthFade = MathHelper.lerp(t, 1.0f, 0.25f);
        }
        int nameAlpha = (int) (alphaInt * lengthFade);

        // Клипуем имя, чтобы не вылезало за правый край блока (под круг HP).
        float nameClipMaxWidth = (x + width - 30f) - textX; // 30 — место под круг HP
        dev.endless.util.render.math.Scissor.push();
        dev.endless.util.render.math.Scissor.setFromComponentCoordinates(textX, y, nameClipMaxWidth, height);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), name, textX + 0.5f, y + 4,
                ColorProvider.rgba(255, 255, 255, nameAlpha), 8f);
        dev.endless.util.render.math.Scissor.unset();
        dev.endless.util.render.math.Scissor.pop();

        // HP-цифра в правом верхнем углу (белая)
        float currentHp = dev.endless.util.server.Server.getHealth(livingEntity, false);
        if (Float.isNaN(currentHp) || currentHp < 0) currentHp = 0;
        int hpInt = Math.round(currentHp);
        String hpText = String.valueOf(hpInt);
        float hpTextSize = 8f;
        float hpTextWidth = Fonts.SFMEDIUM.get().getWidth(hpText, hpTextSize);
        float hpTextX = x + width - hpTextWidth - 6f;
        float hpTextY = y + 4f;
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), hpText, hpTextX, hpTextY,
                ColorProvider.rgba(255, 255, 255, alphaInt), hpTextSize);

        // Предметы (с количеством и прочностью, как было)
        float armorAlpha = (float) armorAnim.getValue();
        if (armorAlpha > 0.05f) {
            List<ItemStack> items = new ArrayList<>();
            items.add(livingEntity.getMainHandStack());
            for (ItemStack stack : livingEntity.getArmorItems()) items.add(stack);
            items.add(livingEntity.getOffHandStack());
            Collections.reverse(items);

            float itemScale = 0.5f;
            float slotSize = 16 * itemScale;
            float itemX = textX;
            float itemY = y + 13;

            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 100);
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

            int itemIndex = 0;
            for (ItemStack stack : items) {
                if (stack.isEmpty()) {
                    itemX += slotSize;
                    itemIndex++;
                    continue;
                }
                context.getMatrices().push();

                float zOffset = 0;
                if (itemIndex == 0) zOffset = 10;
                else if (itemIndex == items.size() - 1) zOffset = 0;
                else zOffset = 5;

                context.getMatrices().translate(itemX, itemY, zOffset);
                context.getMatrices().scale(armorAlpha * itemScale, armorAlpha * itemScale, 1f);
                context.drawItem(stack, 0, 0);

                if (stack.getCount() > 1) {
                    String countText = String.valueOf(stack.getCount());
                    context.getMatrices().push();
                    context.getMatrices().scale(1f / itemScale, 1f / itemScale, 1f);
                    context.drawText(textRenderer, countText,
                        (int)(10 * itemScale - textRenderer.getWidth(countText)),
                        (int)(10 * itemScale),
                        0xFFFFFF, true);
                    context.getMatrices().pop();
                }

                if (stack.isDamageable() && stack.getDamage() > 0) {
                    int maxDamage = stack.getMaxDamage();
                    int damage = stack.getDamage();
                    float durabilityPercent = 1.0f - ((float) damage / maxDamage);

                    int durabilityColor;
                    if (durabilityPercent > 0.5f) durabilityColor = 0x00FF00;
                    else if (durabilityPercent > 0.25f) durabilityColor = 0xFFFF00;
                    else durabilityColor = 0xFF0000;

                    context.getMatrices().push();
                    context.getMatrices().scale(1f / itemScale, 1f / itemScale, 1f);
                    float barWidth = 13 * itemScale;
                    float barHeight = 2 * itemScale;
                    float barX = 1 * itemScale;
                    float barY = 13 * itemScale;
                    context.fill((int)barX, (int)barY, (int)(barX + barWidth), (int)(barY + barHeight), 0xFF000000);
                    context.fill((int)barX, (int)barY, (int)(barX + barWidth * durabilityPercent), (int)(barY + barHeight), durabilityColor | 0xFF000000);
                    context.getMatrices().pop();
                }

                context.getMatrices().pop();
                itemX += slotSize;
                itemIndex++;
            }
            context.getMatrices().pop();
        }

        // Префикс доната (берётся из tab-list display name) — под предметами
        if (playerEntity != null) {
            String donatePrefix = extractDonatePrefix(playerEntity);
            if (donatePrefix != null && !donatePrefix.isEmpty()) {
                float prefixSize = 6.5f;
                float prefixY = y + height - prefixSize - 3f;
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), donatePrefix, x + 36, prefixY,
                        ColorProvider.rgba(255, 255, 255, alphaInt), prefixSize);
            }
        }

        targetHUDDrag.setWidth(width);
        targetHUDDrag.setHeight(height);
    }

    /**
     * Достаёт префикс доната из tab-list игрока. Берёт display name из PlayerListEntry,
     * вырезает имя игрока — остаётся префикс (если он есть). Если префикса нет, возвращает null.
     */
    private String extractDonatePrefix(AbstractClientPlayerEntity playerEntity) {
        if (mc.getNetworkHandler() == null) return null;
        try {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(playerEntity.getUuid());
            if (entry == null) return null;
            Text display = entry.getDisplayName();
            if (display == null) return null;

            String full = display.getString();
            if (full == null || full.isEmpty()) return null;

            String name = playerEntity.getName().getString();
            int nameIdx = full.indexOf(name);
            if (nameIdx <= 0) return null; // префикса нет (или имя в начале)

            String prefix = full.substring(0, nameIdx).trim();
            // Убираем § коды и квадратные скобки/двоеточия по краям
            prefix = prefix.replaceAll("§.", "").trim();
            while (prefix.endsWith(":") || prefix.endsWith("|") || prefix.endsWith("›") || prefix.endsWith(">")) {
                prefix = prefix.substring(0, prefix.length() - 1).trim();
            }
            return prefix.isEmpty() ? null : prefix;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Рисует круговой прогресс-бар (кольцо, которое заполняется по кругу)
     * @param matrix Матрица трансформации
     * @param centerX Центр X
     * @param centerY Центр Y
     * @param outerRadius Внешний радиус кольца
     * @param innerRadius Внутренний радиус кольца
     * @param progress Прогресс от 0.0 до 1.0
     * @param progressColor Цвет заполненной части
     * @param bgColor Цвет фона (незаполненной части)
     */
    private void drawCircularProgress(Matrix4f matrix, float centerX, float centerY, 
                                      float outerRadius, float innerRadius, 
                                      float progress, int progressColor, int bgColor) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        
        int segments = 360; // Максимальное количество сегментов (1° на сегмент)
        float angleStep = (float) (2 * Math.PI / segments);
        float startAngle = (float) (-Math.PI / 2); // Начинаем сверху
        
        // Рисуем фон (полное кольцо)
        BufferBuilder builder = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        
        for (int i = 0; i <= segments; i++) {
            float angle = startAngle + angleStep * i;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            
            float outerX = centerX + cos * outerRadius;
            float outerY = centerY + sin * outerRadius;
            float innerX = centerX + cos * innerRadius;
            float innerY = centerY + sin * innerRadius;
            
            builder.vertex(matrix, outerX, outerY, 0).color(bgColor);
            builder.vertex(matrix, innerX, innerY, 0).color(bgColor);
        }
        
        BufferRenderer.drawWithGlobalProgram(builder.end());
        
        // Рисуем прогресс (заполненную часть)
        if (progress > 0.001f) {
            builder = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            
            int progressSegments = (int) (segments * progress);
            
            for (int i = 0; i <= progressSegments; i++) {
                float angle = startAngle + angleStep * i;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                
                float outerX = centerX + cos * outerRadius;
                float outerY = centerY + sin * outerRadius;
                float innerX = centerX + cos * innerRadius;
                float innerY = centerY + sin * innerRadius;
                
                builder.vertex(matrix, outerX, outerY, 0).color(progressColor);
                builder.vertex(matrix, innerX, innerY, 0).color(progressColor);
            }
            
            BufferRenderer.drawWithGlobalProgram(builder.end());
        }
        
        RenderSystem.disableBlend();
    }

    /**
     * Smoothstep функция для плавной интерполяции
     * @param edge0 Нижняя граница
     * @param edge1 Верхняя граница
     * @param x Значение для интерполяции
     * @return Плавно интерполированное значение
     */
    private float smoothstep(float edge0, float edge1, float x) {
        // Ограничиваем x в диапазоне [0, 1]
        float t = MathHelper.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        // Применяем smoothstep формулу: 3t² - 2t³
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Рисует круговой прогресс-бар с градиентом и сглаживанием краев
     * @param matrix Матрица трансформации
     * @param centerX Центр X
     * @param centerY Центр Y
     * @param outerRadius Внешний радиус кольца
     * @param innerRadius Внутренний радиус кольца
     * @param progress Прогресс от 0.0 до 1.0
     * @param progressColor Базовый цвет заполненной части (цвет темы)
     * @param bgColor Цвет фона (незаполненной части)
     * @param alpha Прозрачность
     */
    private void drawCircularProgressGradient(Matrix4f matrix, float centerX, float centerY, 
                                      float outerRadius, float innerRadius, 
                                      float progress, int progressColor, int bgColor, int alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        
        // Включаем сглаживание
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        
        int segments = 360; // Максимальное количество сегментов (1° на сегмент)
        float angleStep = (float) (2 * Math.PI / segments);
        float startAngle = (float) (-Math.PI / 2); // Начинаем сверху
        float aaWidth = 0.5f; // Ширина антиалиасинга в пикселях
        
        // Рисуем фон (полное кольцо) с сглаживанием краев
        // Внешний край с антиалиасингом
        BufferBuilder builder = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        
        int bgR = (bgColor >> 16) & 0xFF;
        int bgG = (bgColor >> 8) & 0xFF;
        int bgB = bgColor & 0xFF;
        int bgA = (bgColor >> 24) & 0xFF;
        
        for (int i = 0; i <= segments; i++) {
            float angle = startAngle + angleStep * i;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            
            // Внешний край с прозрачностью для сглаживания
            float outerAAX = centerX + cos * (outerRadius + aaWidth);
            float outerAAY = centerY + sin * (outerRadius + aaWidth);
            float outerX = centerX + cos * outerRadius;
            float outerY = centerY + sin * outerRadius;
            
            int transparentBg = ColorProvider.rgba(bgR, bgG, bgB, 0);
            
            builder.vertex(matrix, outerAAX, outerAAY, 0).color(transparentBg);
            builder.vertex(matrix, outerX, outerY, 0).color(bgColor);
        }
        
        BufferRenderer.drawWithGlobalProgram(builder.end());
        
        // Основное кольцо фона
        builder = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        
        for (int i = 0; i <= segments; i++) {
            float angle = startAngle + angleStep * i;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            
            float outerX = centerX + cos * outerRadius;
            float outerY = centerY + sin * outerRadius;
            float innerX = centerX + cos * innerRadius;
            float innerY = centerY + sin * innerRadius;
            
            builder.vertex(matrix, outerX, outerY, 0).color(bgColor);
            builder.vertex(matrix, innerX, innerY, 0).color(bgColor);
        }
        
        BufferRenderer.drawWithGlobalProgram(builder.end());
        
        // Внутренний край с антиалиасингом
        builder = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        
        for (int i = 0; i <= segments; i++) {
            float angle = startAngle + angleStep * i;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            
            float innerX = centerX + cos * innerRadius;
            float innerY = centerY + sin * innerRadius;
            float innerAAX = centerX + cos * (innerRadius - aaWidth);
            float innerAAY = centerY + sin * (innerRadius - aaWidth);
            
            int transparentBg = ColorProvider.rgba(bgR, bgG, bgB, 0);
            
            builder.vertex(matrix, innerX, innerY, 0).color(bgColor);
            builder.vertex(matrix, innerAAX, innerAAY, 0).color(transparentBg);
        }
        
        BufferRenderer.drawWithGlobalProgram(builder.end());
        
        // Рисуем прогресс (заполненную часть) с градиентом и сглаживанием
        if (progress > 0.001f) {
            int progressSegments = (int) (segments * progress);
            
            // Извлекаем RGB компоненты из цвета темы
            int r = (progressColor >> 16) & 0xFF;
            int g = (progressColor >> 8) & 0xFF;
            int b = progressColor & 0xFF;
            
            // Внешний край прогресса с антиалиасингом
            builder = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            
            for (int i = 0; i <= progressSegments; i++) {
                float angle = startAngle + angleStep * i;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                
                float gradientProgress = (float) i / progressSegments;
                float brightness = 1.0f - (gradientProgress * 0.3f);
                
                int currentR = (int)(r * brightness);
                int currentG = (int)(g * brightness);
                int currentB = (int)(b * brightness);
                
                float outerAAX = centerX + cos * (outerRadius + aaWidth);
                float outerAAY = centerY + sin * (outerRadius + aaWidth);
                float outerX = centerX + cos * outerRadius;
                float outerY = centerY + sin * outerRadius;
                
                int transparentColor = ColorProvider.rgba(currentR, currentG, currentB, 0);
                int currentColor = ColorProvider.rgba(currentR, currentG, currentB, alpha);
                
                builder.vertex(matrix, outerAAX, outerAAY, 0).color(transparentColor);
                builder.vertex(matrix, outerX, outerY, 0).color(currentColor);
            }
            
            BufferRenderer.drawWithGlobalProgram(builder.end());
            
            // Основное кольцо прогресса
            builder = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            
            for (int i = 0; i <= progressSegments; i++) {
                float angle = startAngle + angleStep * i;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                
                float outerX = centerX + cos * outerRadius;
                float outerY = centerY + sin * outerRadius;
                float innerX = centerX + cos * innerRadius;
                float innerY = centerY + sin * innerRadius;
                
                float gradientProgress = (float) i / progressSegments;
                float brightness = 1.0f - (gradientProgress * 0.3f);
                
                int currentR = (int)(r * brightness);
                int currentG = (int)(g * brightness);
                int currentB = (int)(b * brightness);
                
                int currentColor = ColorProvider.rgba(currentR, currentG, currentB, alpha);
                
                builder.vertex(matrix, outerX, outerY, 0).color(currentColor);
                builder.vertex(matrix, innerX, innerY, 0).color(currentColor);
            }
            
            BufferRenderer.drawWithGlobalProgram(builder.end());
            
            // Внутренний край прогресса с антиалиасингом
            builder = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            
            for (int i = 0; i <= progressSegments; i++) {
                float angle = startAngle + angleStep * i;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                
                float gradientProgress = (float) i / progressSegments;
                float brightness = 1.0f - (gradientProgress * 0.3f);
                
                int currentR = (int)(r * brightness);
                int currentG = (int)(g * brightness);
                int currentB = (int)(b * brightness);
                
                float innerX = centerX + cos * innerRadius;
                float innerY = centerY + sin * innerRadius;
                float innerAAX = centerX + cos * (innerRadius - aaWidth);
                float innerAAY = centerY + sin * (innerRadius - aaWidth);
                
                int currentColor = ColorProvider.rgba(currentR, currentG, currentB, alpha);
                int transparentColor = ColorProvider.rgba(currentR, currentG, currentB, 0);
                
                builder.vertex(matrix, innerX, innerY, 0).color(currentColor);
                builder.vertex(matrix, innerAAX, innerAAY, 0).color(transparentColor);
            }
            
            BufferRenderer.drawWithGlobalProgram(builder.end());
        }
        
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        RenderSystem.disableBlend();
    }

}

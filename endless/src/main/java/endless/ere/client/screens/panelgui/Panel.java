package endless.ere.client.screens.panelgui;

import com.darkmagician6.eventapi.EventManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import endless.ere.Endless;
import endless.ere.base.font.Fonts;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.screens.panelgui.components.ModuleComponent;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.client.screens.panelgui.RenderUtil;

import java.awt.*;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * @author chuppachups1337
 * @since 04/10/2025
 */

public class Panel extends Screen {

    private float x, y;
    private final float width = 820f;
    private final float height = 295f;
    private final Map<Category, List<ModuleComponent>> columns = new EnumMap<>(Category.class);
    private final SearchComponent search = new SearchComponent();
    private final Animation alphaAnimation = new Animation(200, 0.0f, Easing.LINEAR);
    private final double ANIM_DURATION_SECONDS = 0.2f;
    private final float SCROLL_SPEED = 15f;
    private final float SCROLLBAR_WIDTH = 2.0f;

    private final Map<Category, Float> targetScrollOffsets = new EnumMap<>(Category.class);
    private final Map<Category, Float> currentScrollOffsets = new EnumMap<>(Category.class);

    private boolean dragging = false;
    private float dragX, dragY;

    private final float SCROLL_LERP_FACTOR = 0.1f;

    // Theme swatches state
    private boolean colorPickerOpen = false;
    private float pickerHue = 0.7f;
    private float pickerSat = 0.7f;
    private float pickerVal = 1.0f;
    private boolean draggingHue = false;
    private boolean draggingSV = false;
    private static final float SWATCH_SIZE = 18f;
    private static final float SWATCH_GAP = 8f;
    private float[] swatchBounds = new float[4];
    // Position picker
    private float pickerX = 0, pickerY = 0;
    private boolean pickerInitialized = false;
    private boolean draggingPicker = false;
    private float pickerDragOffX, pickerDragOffY;

    public Panel(Text title) {
        super(title);
        EventManager.register(this);
        rebuildColumns();
        alphaAnimation.setValue((float)0.0D);
    }

    private void rebuildColumns() {
        columns.clear();
        targetScrollOffsets.clear();
        currentScrollOffsets.clear();
        List<Module> modules = Endless.getInstance().getModuleManager().getModules();
        Category[] categories = {Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER, Category.MISC};
        for (Category c : categories) {
            List<ModuleComponent> list = modules.stream()
                    .filter(m -> m.getCategory() == c)
                    .map(ModuleComponent::new)
                    .collect(Collectors.toList());
            columns.put(c, list);
            targetScrollOffsets.put(c, 0f);
            currentScrollOffsets.put(c, 0f);
        }
    }

    @Override
    protected void init() {
        super.init();
        this.x = (client.getWindow().getScaledWidth() / 2f) - (width / 2f);
        this.y = (client.getWindow().getScaledHeight() / 2f) - (height / 2f);
        search.setX(x + (width / 2f) - 150f);
        search.setY(y + height + 8f);
        alphaAnimation.setDuration((long)(ANIM_DURATION_SECONDS * 1000)); alphaAnimation.setEasing(Easing.CUBIC_OUT); alphaAnimation.animateTo(1.0f);
        for (Category c : targetScrollOffsets.keySet()) {
            targetScrollOffsets.put(c, 0f);
            currentScrollOffsets.put(c, 0f);
        }
    }

    private void updateSmoothScrolling(float partialTicks) {
        for (Category c : columns.keySet()) {
            float target = targetScrollOffsets.getOrDefault(c, 0f);
            float current = currentScrollOffsets.getOrDefault(c, 0f);

            if (Math.abs(target - current) < 0.01f) {
                current = target;
            } else {
                current += (target - current) * SCROLL_LERP_FACTOR * (1.0f - partialTicks);
            }
            currentScrollOffsets.put(c, current);
        }
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float partialTicks) {
        updateSmoothScrolling(partialTicks);

        float currentAlpha = alphaAnimation.getValue();
        if (currentAlpha <= 0.01f && (alphaAnimation.getTargetValue() == 0.0D || !(!alphaAnimation.isDone()))) return;

        if (dragging) {
            this.x = mouseX - dragX;
            this.y = mouseY - dragY;
        }

        int panelAlpha = (int) (currentAlpha * 230);
        int headerAlpha = (int) (currentAlpha * 255);
        int scrollbarColor = new Color(255, 255, 255, (int)(currentAlpha * 120)).getRGB();

        Category[] cats = {Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER, Category.MISC};
        float gutter = 5f;
        float colWidth = (width - gutter * (cats.length - 1) - 220) / cats.length;
        float startX = x + 20f;
        float headerY = y - 10f;

        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            float colX = startX + i * (colWidth + gutter);
            float listY = headerY + 22f;
            List<ModuleComponent> all = columns.getOrDefault(c, Collections.emptyList());
            List<ModuleComponent> comps = filterBySearch(all);
            float contentTotalHeight = 0;
            for (ModuleComponent comp : comps) {
                contentTotalHeight += comp.getAnimatedHeight() + 2;
            }

            float listHeight = height - 70f;

            float contentStart = listY + 20f;
            float scrollAreaX1 = colX + 60f;
            float scrollAreaY1 = listY - 6f;
            float scrollAreaX2 = colX + 60f + colWidth;
            float scrollAreaY2 = listY - 6f + listHeight + 32f;
            float visibleModuleHeight = scrollAreaY2 - contentStart;

            // Колонка с тонкой обводкой
            RenderUtil.Blur.drawBlur(g, scrollAreaX1, scrollAreaY1, colWidth, listHeight + 32f, 8f, 10, -1);
            RenderUtil.drawRoundedRect(g, scrollAreaX1, scrollAreaY1, colWidth, listHeight + 32f, 8f, new Color(18, 14, 22, panelAlpha).getRGB());

            // Сетка-волны в цвете темы (двигается, видна локально)
            endless.ere.utility.render.display.shader.DrawUtil.drawHudGrid(
                    g.getMatrices(), scrollAreaX1, scrollAreaY1,
                    colWidth, listHeight + 32f,
                    endless.ere.utility.render.display.base.BorderRadius.all(8f),
                    14f, 0.6f, 0.045f);

            // Хедер: имя слева, иконка справа
            int headerTextColor = new Color(235, 235, 235, headerAlpha).getRGB();
            int headerIconColor = Endless.getInstance().getThemeManager().getCurrentTheme().getColor()
                    .withAlpha(headerAlpha).getRGB();
            float headerLabelY = listY - 6f + 7f;
            float headerPadX = 12f;

            Fonts.MEDIUM.getFont(10).drawString(g, c.getName(),
                    scrollAreaX1 + headerPadX, headerLabelY, headerTextColor);

            String iconStr = c.getIcon();
            float iconW = Fonts.ICONS.getFont(9).getStringWidth(iconStr);
            Fonts.ICONS.getFont(9).drawString(g, iconStr,
                    scrollAreaX1 + colWidth - headerPadX - iconW,
                    headerLabelY, headerIconColor);

            // Тонкая разделительная полоска между заголовком и контентом
            int separatorAlpha = (int) (currentAlpha * 28);
            RenderUtil.drawRoundedRect(g, scrollAreaX1 + 10f, listY + 16f,
                    colWidth - 20f, 0.6f, 0.3f,
                    new Color(255, 255, 255, separatorAlpha).getRGB());

            float scrollOffset = currentScrollOffsets.getOrDefault(c, 0f);
            float curY = contentStart + scrollOffset;
            
            g.enableScissor((int) scrollAreaX1, (int) contentStart - 2, (int) scrollAreaX2, (int) scrollAreaY2);
            for (ModuleComponent comp : comps) {
                comp.setX(colX + 60f);
                comp.setY(curY);
                comp.setWidth(colWidth);
                comp.draw(g, mouseX, mouseY, partialTicks, currentAlpha);
                curY += comp.getAnimatedHeight() + 2;
            }
            g.disableScissor();

            float maxScroll = Math.max(0, contentTotalHeight - visibleModuleHeight);
            if (maxScroll > 0) {
                float trackHeight = visibleModuleHeight;
                float thumbHeight = Math.max(15.0f, trackHeight * (visibleModuleHeight / contentTotalHeight));
                float scrollPercentage = scrollOffset == 0 ? 0 : -scrollOffset / maxScroll;
                float thumbY = contentStart + (trackHeight - thumbHeight) * scrollPercentage;
                float scrollX = scrollAreaX2 - SCROLLBAR_WIDTH - 2f;

                RenderUtil.drawRoundedRect(g, scrollX, thumbY, SCROLLBAR_WIDTH, thumbHeight, 1.5f, scrollbarColor);
            }
        }

        search.setX(x + (width / 2f) - (search.getWidth() / 2f));
        search.setY(y + height + 8f);
        search.draw(g, mouseX, mouseY, partialTicks, currentAlpha);

        // 4 квадратика по центру под search
        float swatchTotalW = 4 * SWATCH_SIZE + 3 * SWATCH_GAP;
        float swatchStartX = x + (width / 2f) - swatchTotalW / 2f;
        float swatchStartY = search.getY() + search.getHeight() + 8f;
        drawThemeSwatches(g, swatchStartX, swatchStartY, currentAlpha);

        // Color picker popup (draggable)
        if (colorPickerOpen) {
            drawColorPicker(g, currentAlpha, mouseX, mouseY);
        }

        if (isAnyBinding()) {
            int overlayAlpha = (int) (currentAlpha * 160);
            RenderUtil.drawRoundedRect(g, 0, 0,
                    client.getWindow().getScaledWidth(),
                    client.getWindow().getScaledHeight(),
                    0f, new Color(0, 0, 0, overlayAlpha).getRGB());

            int bindTextAlpha = (int) (currentAlpha * 230);
            int bindTextColor = new Color(220, 220, 220, bindTextAlpha).getRGB();
            float bindLabelY = y + height + 8f + search.getHeight() + 6f;
            Fonts.MEDIUM.getFont(7).drawCenteredString(g, "Binding...",
                    x + (width / 2f), bindLabelY, bindTextColor);
        }
    }

    private boolean isAnyBinding() {
        for (List<ModuleComponent> list : columns.values()) {
            for (ModuleComponent comp : list) {
                if (comp.isBinding()) return true;
            }
        }
        return false;
    }

    /** 4 квадратика по центру под search: Dark, Light, Custom, "?" - color picker. */
    private void drawThemeSwatches(DrawContext g, float startX, float startY, float currentAlpha) {
        endless.ere.base.theme.Theme[] themes = {
                endless.ere.base.theme.Theme.DARK,
                endless.ere.base.theme.Theme.LIGHT,
                endless.ere.base.theme.Theme.CUSTOM_THEME
        };
        endless.ere.base.theme.Theme current = Endless.getInstance().getThemeManager().getCurrentTheme();
        int alpha = (int) (currentAlpha * 255);

        for (int i = 0; i < themes.length; i++) {
            float sx = startX + i * (SWATCH_SIZE + SWATCH_GAP);
            endless.ere.base.theme.Theme t = themes[i];
            int rgb = t.getColor().getRGB() & 0xFFFFFF;
            boolean active = current.getName().equals(t.getName());

            RenderUtil.drawRoundedRect(g, sx, startY, SWATCH_SIZE, SWATCH_SIZE, 4f,
                    new Color(rgb | (alpha << 24), true).getRGB());
            if (active) {
                RenderUtil.drawRoundedRect(g, sx - 1, startY - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 5f,
                        new Color(255, 255, 255, (int)(currentAlpha * 200)).getRGB(), 1.5f);
            }
        }

        // Custom color picker swatch (4-й, прозрачный с пипеткой как в AutoBuy)
        float sx = startX + 3 * (SWATCH_SIZE + SWATCH_GAP);
        swatchBounds[0] = sx; swatchBounds[1] = startY; swatchBounds[2] = SWATCH_SIZE; swatchBounds[3] = SWATCH_SIZE;
        RenderUtil.drawRoundedRect(g, sx, startY, SWATCH_SIZE, SWATCH_SIZE, 4f,
                new Color(60, 60, 70, (int)(currentAlpha * 150)).getRGB());
        endless.ere.utility.render.display.shader.DrawUtil.drawHudGrid(
                g.getMatrices(), sx, startY, SWATCH_SIZE, SWATCH_SIZE,
                endless.ere.utility.render.display.base.BorderRadius.all(4f),
                4f, 0.4f, currentAlpha * 0.15f);
        // Пипетка (V из ICONS как в AutoBuy)
        String pipChar = "V";
        float pw = Fonts.ICONS.getFont(9).getStringWidth(pipChar);
        Fonts.ICONS.getFont(9).drawString(g, pipChar,
                sx + (SWATCH_SIZE - pw) / 2f, startY + 4f,
                new Color(255, 255, 255, (int)(currentAlpha * 220)).getRGB());
    }

    private void drawColorPicker(DrawContext g, float currentAlpha, int mouseX, int mouseY) {
        float pickerW = 90f, pickerH = 92f;

        // Initialize position once
        if (!pickerInitialized) {
            pickerX = swatchBounds[0] + SWATCH_SIZE - pickerW / 2;
            pickerY = swatchBounds[1] + SWATCH_SIZE + 6f;
            if (pickerX + pickerW > client.getWindow().getScaledWidth()) pickerX = client.getWindow().getScaledWidth() - pickerW - 4;
            if (pickerX < 4) pickerX = 4;
            if (pickerY + pickerH > client.getWindow().getScaledHeight()) pickerY = client.getWindow().getScaledHeight() - pickerH - 4;
            pickerInitialized = true;
        }

        if (draggingPicker) {
            pickerX = mouseX - pickerDragOffX;
            pickerY = mouseY - pickerDragOffY;
        }

        float px = pickerX, py = pickerY;
        int alpha = (int) (currentAlpha * 235);

        // ── Background ──
        RenderUtil.drawRoundedRect(g, px, py, pickerW, pickerH, 5f,
                new Color(18, 14, 22, alpha).getRGB());

        // ── Field as backdrop for SV+Hue ──
        float fieldPad = 5f;
        float fieldX = px + fieldPad;
        float fieldY = py + fieldPad;
        float fieldW = pickerW - fieldPad * 2;
        float fieldH = 68f;
        RenderUtil.drawRoundedRect(g, fieldX, fieldY, fieldW, fieldH, 3f,
                new Color(28, 22, 38, (int)(currentAlpha * 200)).getRGB());

        // SV-square
        float svInner = 3f;
        float svX = fieldX + svInner;
        float svY = fieldY + svInner;
        float svSize = fieldH - svInner * 2;
        int steps = 22;
        float stepSize = svSize / steps;
        for (int sx = 0; sx < steps; sx++) {
            for (int sy = 0; sy < steps; sy++) {
                float s = sx / (float)(steps - 1);
                float v = 1f - sy / (float)(steps - 1);
                int rgb = Color.HSBtoRGB(pickerHue, s, v) & 0xFFFFFF;
                RenderUtil.drawRoundedRect(g, svX + sx * stepSize, svY + sy * stepSize,
                        stepSize + 0.5f, stepSize + 0.5f, 0,
                        new Color(rgb | (alpha << 24), true).getRGB());
            }
        }
        // SV cursor
        float cursorX = svX + pickerSat * svSize;
        float cursorY = svY + (1f - pickerVal) * svSize;
        RenderUtil.drawRoundedRect(g, cursorX - 2, cursorY - 2, 4, 4, 2f,
                new Color(0, 0, 0, alpha).getRGB());
        RenderUtil.drawRoundedRect(g, cursorX - 1, cursorY - 1, 2, 2, 1f,
                new Color(255, 255, 255, alpha).getRGB());

        // Hue slider (right of SV)
        float hueX = svX + svSize + 5;
        float hueY = svY;
        float hueW = fieldW - svSize - svInner * 2 - 5;
        float hueH = svSize;
        int hueSteps = 60;
        for (int i = 0; i < hueSteps; i++) {
            float hue = i / (float)hueSteps;
            int rgb = Color.HSBtoRGB(hue, 1, 1) & 0xFFFFFF;
            RenderUtil.drawRoundedRect(g, hueX, hueY + i * (hueH / hueSteps),
                    hueW, hueH / hueSteps + 0.5f, 0,
                    new Color(rgb | (alpha << 24), true).getRGB());
        }
        float hCursorY = hueY + pickerHue * hueH;
        RenderUtil.drawRoundedRect(g, hueX - 1, hCursorY - 1, hueW + 2, 2, 1f,
                new Color(255, 255, 255, alpha).getRGB());

        // Current color preview below
        int curRgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0xFFFFFF;
        float curX = fieldX;
        float curY = fieldY + fieldH + 4;
        RenderUtil.drawRoundedRect(g, curX, curY, fieldW, 10, 2f,
                new Color(curRgb | (alpha << 24), true).getRGB());
    }

    private boolean handleSwatchClick(double mxD, double myD) {
        endless.ere.base.theme.Theme[] themes = {
                endless.ere.base.theme.Theme.DARK,
                endless.ere.base.theme.Theme.LIGHT,
                endless.ere.base.theme.Theme.CUSTOM_THEME
        };
        float swatchTotalW = 4 * SWATCH_SIZE + 3 * SWATCH_GAP;
        float startX = x + (width / 2f) - swatchTotalW / 2f;
        float startY = search.getY() + search.getHeight() + 8f;
        for (int i = 0; i < themes.length; i++) {
            float sx = startX + i * (SWATCH_SIZE + SWATCH_GAP);
            if (mxD >= sx && mxD <= sx + SWATCH_SIZE && myD >= startY && myD <= startY + SWATCH_SIZE) {
                Endless.getInstance().getThemeManager().switchTheme(themes[i]);
                colorPickerOpen = false;
                return true;
            }
        }
        // Custom picker swatch
        float csx = startX + 3 * (SWATCH_SIZE + SWATCH_GAP);
        if (mxD >= csx && mxD <= csx + SWATCH_SIZE && myD >= startY && myD <= startY + SWATCH_SIZE) {
            colorPickerOpen = !colorPickerOpen;
            pickerInitialized = false; // re-init position next render
            return true;
        }

        // Обработка кликов в open color picker
        if (colorPickerOpen) {
            float pickerW = 90f, pickerH = 92f;
            float px = pickerX, py = pickerY;
            float fieldPad = 5f;
            float fieldX = px + fieldPad, fieldY = py + fieldPad;
            float fieldW = pickerW - fieldPad * 2;
            float fieldH = 68f;
            float svInner = 3f;
            float svX = fieldX + svInner, svY = fieldY + svInner;
            float svSize = fieldH - svInner * 2;
            float hueX = svX + svSize + 5;
            float hueY = svY;
            float hueW = fieldW - svSize - svInner * 2 - 5;
            float hueH = svSize;

            if (mxD >= svX && mxD <= svX + svSize && myD >= svY && myD <= svY + svSize) {
                pickerSat = (float)((mxD - svX) / svSize);
                pickerVal = 1f - (float)((myD - svY) / svSize);
                draggingSV = true;
                applyCustomColor();
                return true;
            }
            if (mxD >= hueX && mxD <= hueX + hueW && myD >= hueY && myD <= hueY + hueH) {
                pickerHue = (float)((myD - hueY) / hueH);
                draggingHue = true;
                applyCustomColor();
                return true;
            }
            // Drag picker by клик в любой пустой области (не на SV/Hue)
            if (mxD >= px && mxD <= px + pickerW && myD >= py && myD <= py + pickerH) {
                draggingPicker = true;
                pickerDragOffX = (float)(mxD - px);
                pickerDragOffY = (float)(myD - py);
                return true;
            }
        }
        return false;
    }

    private void applyCustomColor() {
        // НЕ меняем тему меню автоматически - только сохраняем выбранный цвет в picker.
        // Тема меняется только если игрок явно нажмёт на CUSTOM_THEME swatch.
        // Если уже активна Custom тема - применяем цвет к ней.
        if (Endless.getInstance().getThemeManager().getCurrentTheme().getName().equals("Custom")) {
            int rgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal);
            endless.ere.utility.render.display.base.color.ColorRGBA c =
                    new endless.ere.utility.render.display.base.color.ColorRGBA(
                            (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
            endless.ere.base.theme.Theme.CUSTOM_THEME.setColor(c);
        }
    }

    private boolean isAnyBindingDuplicate() {
        return false;
    }

    private List<ModuleComponent> filterBySearch(List<ModuleComponent> source) {
        String query = search.getText();
        if (query == null || query.isEmpty()) return source;
        String needle = query.toLowerCase(java.util.Locale.ROOT).trim();
        if (needle.isEmpty()) return source;
        List<ModuleComponent> out = new java.util.ArrayList<>();
        for (ModuleComponent comp : source) {
            String name = comp.getModule().getName();
            if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                out.add(comp);
            }
        }
        return out;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double verticalDelta) {
        if (!isInteractable()) return false;

        float listY_start = y - 10f + 22f - 6f;
        float listY_end = listY_start + height - 70f + 32f;

        if (mouseY >= listY_start && mouseY <= listY_end) {
            Category[] cats = {Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER, Category.MISC};
            float gutter = 5f;
            float colWidth = (width - gutter * (cats.length - 1) - 220) / cats.length;
            float startX = x + 20f;

            for (int i = 0; i < cats.length; i++) {
                Category c = cats[i];
                float colX_start = startX + i * (colWidth + gutter) + 60f;
                float colX_end = colX_start + colWidth;

                if (mouseX >= colX_start && mouseX <= colX_end) {
                    float targetOffset = targetScrollOffsets.getOrDefault(c, 0f);
                    targetOffset += verticalDelta * SCROLL_SPEED;

                    float contentTotalHeight = 0;
                    for (ModuleComponent comp : filterBySearch(columns.getOrDefault(c, Collections.emptyList()))) {
                        contentTotalHeight += comp.getAnimatedHeight() + 2;
                    }
                    float contentStart = y - 10f + 22f + 20f;
                    float scrollAreaY2 = y - 10f + 22f - 6f + height - 70f + 32f;
                    float visibleModuleHeight = scrollAreaY2 - contentStart;
                    float maxScroll = Math.max(0, contentTotalHeight - visibleModuleHeight);

                    targetOffset = Math.max(-maxScroll, Math.min(0, targetOffset));

                    targetScrollOffsets.put(c, targetOffset);
                    return true;
                }
            }
        }
        return false;
    }


    public boolean shouldPause() { return false; }

    @Override
    public void tick() {
        super.tick();
        alphaAnimation.update();
        if (alphaAnimation.getTargetValue() == 0.0D && !(!alphaAnimation.isDone())) {
            client.setScreen(null);
        }
    }

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float partialTick) {
        float currentAlpha = alphaAnimation.getValue();
        int bgAlpha = (int) (currentAlpha * 200);
        RenderUtil.drawRoundedRect(g, 0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), 0f, new Color(0, 0, 0, bgAlpha).getRGB());
    }

    private boolean isInteractable() {
        return alphaAnimation.getValue() > 0.9f && !(!alphaAnimation.isDone());
    }

    @Override
    public boolean mouseClicked(double mxD, double myD, int button) {
        if (!isInteractable()) return false;
        if (handleSwatchClick(mxD, myD)) return true;
        if (search.mouseClicked((float) mxD, (float) myD, button)) return true;
        float listY_start = y - 10f + 22f - 6f;
        float listY_end = listY_start + height - 70f + 32f;
        
        if (myD >= listY_start && myD <= listY_end) {
            for (List<ModuleComponent> list : columns.values()) {
                for (ModuleComponent comp : filterBySearch(list)) {
                    if (comp.mouseClicked((float) mxD, (float) myD, button)) return true;
                }
            }
        }
        if (button == 0 && mxD >= x && mxD <= x + width && myD >= y - 10f && myD <= y + 22f) {
            dragging = true;
            dragX = (float) mxD - x;
            dragY = (float) myD - y;
            return true;
        }
        return super.mouseClicked(mxD, myD, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!isInteractable()) return false;

        // Drag color picker / hue / SV
        if (draggingPicker) {
            return true;
        }
        if (draggingSV && colorPickerOpen) {
            float fieldX = pickerX + 5, fieldY = pickerY + 5;
            float svX = fieldX + 3, svY = fieldY + 3, svSize = 62;
            pickerSat = Math.max(0, Math.min(1, (float)((mouseX - svX) / svSize)));
            pickerVal = Math.max(0, Math.min(1, 1f - (float)((mouseY - svY) / svSize)));
            applyCustomColor();
            return true;
        }
        if (draggingHue && colorPickerOpen) {
            float fieldY = pickerY + 5;
            float hueY = fieldY + 3, hueH = 62;
            pickerHue = Math.max(0, Math.min(1, (float)((mouseY - hueY) / hueH)));
            applyCustomColor();
            return true;
        }

        float listY_start = y - 10f + 22f - 6f;
        float listY_end = listY_start + height - 70f + 32f;
        
        if (mouseY >= listY_start && mouseY <= listY_end) {
            for (List<ModuleComponent> list : columns.values()) {
                for (ModuleComponent comp : filterBySearch(list)) {
                    comp.mouseDragged(mouseX, mouseY, button);
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mxD, double myD, int button) {
        if (!isInteractable()) return false;
        // Stop dragging picker stuff
        draggingPicker = false;
        draggingHue = false;
        draggingSV = false;
        float mx = (float) mxD;
        float my = (float) myD;
        float listY_start = y - 10f + 22f - 6f;
        float listY_end = listY_start + height - 70f + 32f;
        
        if (myD >= listY_start && myD <= listY_end) {
            for (List<ModuleComponent> list : columns.values()) {
                for (ModuleComponent comp : filterBySearch(list)) {
                    comp.mouseReleased(mx, my, button);
                }
            }
        }
        if (button == 0) {
            dragging = false;
        }
        search.mouseReleased(mx, my, button);
        return super.mouseReleased(mxD, myD, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.startFadeOut();
            return true;
        }
        if (!isInteractable()) return false;
        if (search.keyPressed(keyCode, scanCode, modifiers)) return true;
        for (List<ModuleComponent> list : columns.values()) {
            for (ModuleComponent comp : filterBySearch(list)) {
                if (comp.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isInteractable()) return false;
        if (search.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void close() {
        startFadeOut();
    }

    public void startFadeOut() {
        alphaAnimation.setDuration((long)(ANIM_DURATION_SECONDS * 1000)); alphaAnimation.setEasing(Easing.CUBIC_IN); alphaAnimation.animateTo(0.0f);
        EventManager.unregister(this);
    }

    public void shutDown() {
        startFadeOut();
    }
}
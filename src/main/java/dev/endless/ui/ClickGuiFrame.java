package dev.endless.ui;

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import dev.endless.module.ModuleCategory;
import dev.endless.util.IMinecraft;
import dev.endless.util.cursor.CursorManager;
import dev.endless.util.render.helper.HoverUtil;
import dev.endless.util.render.math.Easing;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ClickGuiFrame extends Screen implements IMinecraft {

    private final List<Panel> panels = new ArrayList<>();

    // Хранилище пресетов тем для нижнего переключателя. Панель-редактор не открывается.
    private final ThemeManagerWindow themeManager;

    // Поиск
    private String searchText = "";
    private boolean searchFocused = false;

    public ClickGuiFrame() {
        super(Text.of("Avalora Frame"));
        for (ModuleCategory category : ModuleCategory.values()) {
            panels.add(new Panel(category, this));
        }
        themeManager = new ThemeManagerWindow(this);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CursorManager.reset();
        CursorManager.resetIBeam();
        CursorManager.resetClick();

        int windowWidth = mc.getWindow().getScaledWidth();
        int windowHeight = mc.getWindow().getScaledHeight();

        float panelWidth = 105f;
        float spacing = 4f;
        float panelHeight = 240f;
        float panelTotalWidth = panels.size() * (panelWidth + spacing) - spacing;

        float startX = (windowWidth - panelTotalWidth) / 2f;
        float panelY = (windowHeight - panelHeight) / 2f;

        // ── Поле поиска над панелями ──
        float searchW = 130f;
        float searchH = 16f;
        float searchX = (windowWidth - searchW) / 2f;
        float searchY = panelY - searchH - 8f;

        boolean searchHovered = HoverUtil.isHovered(mouseX, mouseY, searchX, searchY, searchW, searchH);
        if (searchHovered) CursorManager.requestIBeam();

        int searchBgAlpha = searchFocused ? 220 : 180;
        DrawUtil.drawRoundBlur(searchX, searchY, searchW, searchH, 4f, ColorProvider.rgba(75, 75, 75, 150), 12f);
        DrawUtil.drawRound(searchX, searchY, searchW, searchH, 4f, ColorProvider.rgba(20, 20, 25, searchBgAlpha));

        // Иконка лупы
        float iconW = Fonts.ICONS_MINCED.get().getWidth("l", 9f);
        DrawUtil.drawText(Fonts.ICONS_MINCED.get(), "l", searchX + 5f, searchY + (searchH / 2f) - 4f,
                ColorProvider.setAlpha(ColorProvider.getColorIcons(), 200), 9f);

        // Текст поиска
        String searchDisplay = searchText.isEmpty() && !searchFocused ? "Search..." : searchText;
        String cursor = searchFocused && System.currentTimeMillis() % 1000 > 500 ? "|" : "";
        int searchTextColor = searchText.isEmpty() && !searchFocused
                ? ColorProvider.rgba(140, 140, 150, 255)
                : ColorProvider.rgba(220, 220, 230, 255);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), searchDisplay + cursor, searchX + 16f, searchY + (searchH / 2f) - 3.5f, searchTextColor, 7f);

        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            panel.getAnimationAlpha().setDuration(650);
            panel.getAnimationAlpha().run(1);
            panel.getAnimationAlpha().setEasing(Easing.QUINTIC_OUT);

            panel.setX(startX + i * (panelWidth + spacing));
            panel.setY(panelY);
            panel.setWidth(panelWidth);
            panel.setHeight(panelHeight);

            panel.render(context.getMatrices(), mouseX, mouseY, delta);
        }

        // ── Нижний переключатель тем (как на скрине) ──
        renderThemeBar(context, mouseX, mouseY,
                startX, panelY + panelHeight + 10f, panelTotalWidth);

        for (Panel panel : panels) {
            boolean isMouseInPanel = HoverUtil.isHovered(mouseX, mouseY, panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight());

            for (ModuleComponent component : panel.getModuleComponents()) {
                if (component.isHovered() && isMouseInPanel) {
                    String desc = component.getModule().getDesc();
                    if (desc != null && !desc.isEmpty()) {
                        float textWidth = Fonts.SFREGULAR.get().getWidth(desc, 8f) + 2;
                        DrawUtil.drawRound(windowWidth / 2f - textWidth / 2f - 2, windowHeight / 2f - 180, textWidth + 8, 14, 0, ColorProvider.rgba(0, 0, 0, 111));
                        DrawUtil.drawText(Fonts.SFREGULAR.get(), desc, windowWidth / 2f - textWidth / 2f, windowHeight / 2f - 176, ColorProvider.rgba(255, 255, 255, 255), 8f);
                    }
                }
            }
        }

        long window = mc.getWindow().getHandle();
        if (CursorManager.shouldBeHand()) GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR));
        else if (CursorManager.shouldIBeam()) GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR));
        else if (CursorManager.shouldClick()) GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR));
        else GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR));
    }

    public boolean searchCheck(String text) {
        if (searchText.isEmpty()) return false;
        return !text.toLowerCase().contains(searchText.toLowerCase());
    }

    /** Координаты нижнего тулбара выбора темы. Сохраняем после рендера для обработки клика. */
    private float themeBarX, themeBarY, themeBarW, themeBarH;

    private void renderThemeBar(DrawContext context, int mouseX, int mouseY,
                                float panelsStartX, float barY, float panelsTotalWidth) {
        int presetCount = themeManager.getPresetCount();
        if (presetCount == 0) return;

        float dotSize = 14f;
        float dotGap = 6f;
        float padX = 10f;
        float maxDots = Math.min(presetCount, 8); // не больше 8 кружков визуально
        float content = maxDots * dotSize + (maxDots - 1) * dotGap;
        float barWidth = content + padX * 2f;
        float barHeight = 22f;
        float barX = panelsStartX + (panelsTotalWidth - barWidth) / 2f;

        themeBarX = barX;
        themeBarY = barY;
        themeBarW = barWidth;
        themeBarH = barHeight;

        // Фон-пилюля
        DrawUtil.drawRoundBlur(barX, barY, barWidth, barHeight, barHeight / 2f,
                ColorProvider.rgba(75, 75, 75, 200), 16f);
        DrawUtil.drawRound(barX, barY, barWidth, barHeight, barHeight / 2f,
                ColorProvider.rgba(20, 20, 25, 200));

        float cursorX = barX + padX;
        float dotY = barY + (barHeight - dotSize) / 2f;
        for (int i = 0; i < maxDots; i++) {
            int color = themeManager.getPresetAccent(i);
            boolean active = themeManager.isPresetActive(i);
            boolean hovered = HoverUtil.isHovered(mouseX, mouseY, cursorX, dotY, dotSize, dotSize);
            if (hovered) CursorManager.requestHand();

            // Окантовка для активного
            if (active) {
                float ring = dotSize + 4f;
                DrawUtil.drawRound(cursorX - 2f, dotY - 2f, ring, ring, ring / 2f,
                        ColorProvider.rgba(255, 255, 255, 220));
            }
            int alpha = hovered ? 255 : 230;
            DrawUtil.drawRound(cursorX, dotY, dotSize, dotSize, dotSize / 2f,
                    ColorProvider.setAlpha(color, alpha));

            cursorX += dotSize + dotGap;
        }
    }

    private boolean handleThemeBarClick(double mouseX, double mouseY) {
        if (themeBarW <= 0f || themeBarH <= 0f) return false;
        if (!HoverUtil.isHovered(mouseX, mouseY, themeBarX, themeBarY, themeBarW, themeBarH)) return false;

        float dotSize = 14f;
        float dotGap = 6f;
        float padX = 10f;
        int presetCount = themeManager.getPresetCount();
        int maxDots = Math.min(presetCount, 8);

        float cursorX = themeBarX + padX;
        float dotY = themeBarY + (themeBarH - dotSize) / 2f;
        for (int i = 0; i < maxDots; i++) {
            if (HoverUtil.isHovered(mouseX, mouseY, cursorX, dotY, dotSize, dotSize)) {
                themeManager.applyPresetByIndex(i);
                return true;
            }
            cursorX += dotSize + dotGap;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleThemeBarClick(mouseX, mouseY)) {
            return true;
        }

        // Клик по полю поиска
        int windowWidth = mc.getWindow().getScaledWidth();
        int windowHeight = mc.getWindow().getScaledHeight();
        float panelWidth = 105f;
        float spacing = 4f;
        float panelHeight = 240f;
        float panelTotalWidth = panels.size() * (panelWidth + spacing) - spacing;
        float panelY = (windowHeight - panelHeight) / 2f;
        float searchW = 130f;
        float searchH = 16f;
        float searchX = (windowWidth - searchW) / 2f;
        float searchY = panelY - searchH - 8f;

        if (HoverUtil.isHovered(mouseX, mouseY, searchX, searchY, searchW, searchH) && button == 0) {
            searchFocused = true;
            return true;
        } else if (button == 0) {
            searchFocused = false;
        }

        for (Panel panel : panels) {
            if (HoverUtil.isHovered(mouseX, mouseY, panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight())) {
                panel.mouseClicked(mouseX, mouseY, button);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (Panel panel : panels) {
            panel.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (Panel panel : panels) {
            panel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Обработка поиска
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            long window = mc.getWindow().getHandle();
            GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR));
        }
        for (Panel panel : panels) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                panel.getAnimationAlpha().setValue(0);
                panel.getAnimationAlpha().reset();
            }
            panel.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && searchText.length() < 20) {
            searchText += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

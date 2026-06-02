package endless.ere.client.screens.morecosmetics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.screens.panelgui.RenderUtil;
import endless.ere.utility.render.display.base.color.ColorRGBA;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Стилизованная панель управления MoreCosmetics. Открывается, когда модуль
 * MoreCosmetic включен, и предоставляет быстрый доступ к функциям мода.
 */
public class MoreCosmeticPanel extends Screen {

    private static final float WIDTH = 240f;
    private static final float HEADER = 24f;
    private static final float ROW_HEIGHT = 22f;
    private static final float ROW_GAP = 4f;
    private static final float PADDING = 10f;

    private final Animation alphaAnimation = new Animation(180L, 0f, Easing.CUBIC_OUT);
    private final long animDurationMs = 180L;

    private final Runnable onClose;
    private final List<Row> rows = new ArrayList<>();

    private float x, y, height;
    private boolean dragging = false;
    private float dragX, dragY;
    private boolean closing = false;

    public MoreCosmeticPanel(Runnable onClose) {
        super(Text.literal("MoreCosmetics"));
        this.onClose = onClose;
        buildRows();
        alphaAnimation.setValue(0f);
    }

    private void buildRows() {
        rows.clear();
        rows.add(Row.toggle("Плащ",
                MoreCosmeticsBridge::isCloakEnabled,
                MoreCosmeticsBridge::setCloakEnabled));
        rows.add(Row.toggle("Никнейм",
                MoreCosmeticsBridge::isNametagEnabled,
                MoreCosmeticsBridge::setNametagEnabled));
        rows.add(Row.button("Открыть меню косметики", () -> {
            startFadeOut();
            MoreCosmeticsBridge.openUI(true);
        }));
    }

    @Override
    protected void init() {
        super.init();
        this.height = HEADER + PADDING + rows.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP + PADDING;
        this.x = (client.getWindow().getScaledWidth() / 2f) - (WIDTH / 2f);
        this.y = (client.getWindow().getScaledHeight() / 2f) - (height / 2f);
        alphaAnimation.setDuration(animDurationMs);
        alphaAnimation.setEasing(Easing.CUBIC_OUT);
        alphaAnimation.animateTo(1f);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        alphaAnimation.update();
        if (closing && alphaAnimation.isDone() && alphaAnimation.getTargetValue() == 0f) {
            client.setScreen(null);
            if (onClose != null) onClose.run();
        }
    }

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float partialTick) {
        float a = alphaAnimation.getValue();
        int bgAlpha = (int) (a * 170);
        RenderUtil.drawRoundedRect(g, 0, 0,
                client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight(),
                0f, new Color(0, 0, 0, bgAlpha).getRGB());
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float partialTicks) {
        float a = alphaAnimation.getValue();
        if (a <= 0.01f && alphaAnimation.getTargetValue() == 0f) return;

        if (dragging) {
            this.x = mouseX - dragX;
            this.y = mouseY - dragY;
        }

        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        ColorRGBA accent = theme.getColor();

        int panelAlpha = (int) (a * 235);
        int headerAlpha = (int) (a * 255);

        // Backdrop blur + body
        RenderUtil.Blur.drawBlur(g, x, y, WIDTH, height, 8f, 14, -1);
        RenderUtil.drawRoundedRect(g, x, y, WIDTH, height, 8f,
                new Color(20, 16, 22, panelAlpha).getRGB());

        // Accent strip on header
        int accentColor = new Color(
                accent.getRed(), accent.getGreen(), accent.getBlue(),
                (int) (a * 255)).getRGB();
        RenderUtil.drawRoundedRect(g, x + PADDING, y + 9f, 3f, 12f, 1.5f, accentColor);

        // Title
        Fonts.MEDIUM.getFont(10).drawString(g, "MoreCosmetics",
                x + PADDING + 9f, y + 11f,
                new Color(230, 230, 230, headerAlpha).getRGB());

        // Subtitle / hint
        Fonts.MEDIUM.getFont(7).drawString(g, "Кликни для переключения",
                x + PADDING + 9f, y + 21f,
                new Color(150, 150, 150, headerAlpha).getRGB());

        // Rows
        float rowY = y + HEADER + PADDING;
        for (Row row : rows) {
            row.x = x + PADDING;
            row.y = rowY;
            row.width = WIDTH - PADDING * 2f;
            row.height = ROW_HEIGHT;
            row.draw(g, mouseX, mouseY, a, accent);
            rowY += ROW_HEIGHT + ROW_GAP;
        }
    }

    private boolean isInteractable() {
        return !closing && alphaAnimation.getValue() > 0.85f;
    }

    @Override
    public boolean mouseClicked(double mxD, double myD, int button) {
        if (!isInteractable()) return false;
        float mx = (float) mxD, my = (float) myD;
        for (Row row : rows) {
            if (row.contains(mx, my)) {
                row.click();
                return true;
            }
        }
        if (button == 0 && mx >= x && mx <= x + WIDTH && my >= y && my <= y + HEADER) {
            dragging = true;
            dragX = mx - x;
            dragY = my - y;
            return true;
        }
        return super.mouseClicked(mxD, myD, button);
    }

    @Override
    public boolean mouseReleased(double mxD, double myD, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mxD, myD, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            startFadeOut();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        startFadeOut();
    }

    public void startFadeOut() {
        if (closing) return;
        closing = true;
        alphaAnimation.setDuration(animDurationMs);
        alphaAnimation.setEasing(Easing.CUBIC_IN);
        alphaAnimation.animateTo(0f);
    }

    // ── Row ─────────────────────────────────────────────────────────────────

    private static final class Row {
        enum Kind { TOGGLE, BUTTON }

        final Kind kind;
        final String label;
        final BooleanSupplier state;
        final Consumer<Boolean> setter;
        final Runnable action;

        float x, y, width, height;

        private Row(Kind kind, String label, BooleanSupplier state,
                    Consumer<Boolean> setter, Runnable action) {
            this.kind = kind;
            this.label = label;
            this.state = state;
            this.setter = setter;
            this.action = action;
        }

        static Row toggle(String label, BooleanSupplier state, Consumer<Boolean> setter) {
            return new Row(Kind.TOGGLE, label, state, setter, null);
        }

        static Row button(String label, Runnable action) {
            return new Row(Kind.BUTTON, label, null, null, action);
        }

        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }

        void click() {
            try {
                if (kind == Kind.TOGGLE) {
                    setter.accept(!state.getAsBoolean());
                } else if (action != null) {
                    action.run();
                }
            } catch (Throwable ignored) {
                // MoreCosmetics may not be initialized yet — игнорируем
            }
        }

        void draw(DrawContext g, int mouseX, int mouseY, float a, ColorRGBA accent) {
            boolean hovered = contains(mouseX, mouseY);
            int rowAlpha = (int) (a * (hovered ? 200 : 165));
            int textAlpha = (int) (a * 235);

            int rowBg = new Color(28, 22, 28, rowAlpha).getRGB();
            int textColor = new Color(220, 220, 220, textAlpha).getRGB();

            RenderUtil.drawRoundedRect(g, x, y, width, height, 5f, rowBg);

            Fonts.MEDIUM.getFont(8).drawString(g, label, x + 8f,
                    y + (height / 2f) - 3.5f, textColor);

            if (kind == Kind.TOGGLE) {
                drawSwitch(g, a, accent);
            } else {
                drawArrow(g, a, accent);
            }
        }

        private void drawSwitch(DrawContext g, float a, ColorRGBA accent) {
            float sw = 22f, sh = 11f;
            float sx = x + width - sw - 8f;
            float sy = y + (height - sh) / 2f;

            boolean on;
            try { on = state.getAsBoolean(); } catch (Throwable t) { on = false; }

            int trackAlpha = (int) (a * (on ? 230 : 80));
            int trackOff = new Color(70, 60, 80, trackAlpha).getRGB();
            int trackOn = new Color(
                    accent.getRed(), accent.getGreen(), accent.getBlue(),
                    trackAlpha).getRGB();

            RenderUtil.drawRoundedRect(g, sx, sy, sw, sh, sh / 2f, on ? trackOn : trackOff);

            float knob = sh - 3f;
            float knobX = on ? sx + sw - knob - 1.5f : sx + 1.5f;
            int knobColor = new Color(245, 245, 245, (int) (a * 255)).getRGB();
            RenderUtil.drawRoundedRect(g, knobX, sy + 1.5f, knob, knob, knob / 2f, knobColor);
        }

        private void drawArrow(DrawContext g, float a, ColorRGBA accent) {
            int arrowColor = new Color(
                    accent.getRed(), accent.getGreen(), accent.getBlue(),
                    (int) (a * 235)).getRGB();
            Fonts.MEDIUM.getFont(9).drawString(g, "→",
                    x + width - 16f, y + (height / 2f) - 4f, arrowColor);
        }
    }
}

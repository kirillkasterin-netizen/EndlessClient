package endless.ere.client.modules.impl.misc.autobuy;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.font.Fonts;
import endless.ere.client.screens.panelgui.RenderUtil;
import endless.ere.utility.render.display.base.BorderRadius;

import java.awt.Color;
import java.util.List;

/**
 * GUI истории покупок в стиле Panel: blur + hud-grid + плавный fade-in.
 * Кнопка "Цены" в шапке — открывает {@link AutoBuyPriceScreen}.
 */
public class AutoBuyHistoryScreen extends Screen {

    private static final float WIDTH = 260f;
    private static final float HEADER_H = 30f;
    private static final float ITEM_H = 28f;
    private static final float ITEM_GAP = 3f;
    private static final float PAD = 8f;
    private static final int VISIBLE = 9;

    private static final float LIST_H = VISIBLE * ITEM_H + (VISIBLE - 1) * ITEM_GAP;
    private static final float HEIGHT = HEADER_H + LIST_H + PAD;

    private final Animation alpha = new Animation(220, 0.0f, Easing.CUBIC_OUT);
    private final double ANIM_S = 0.22f;

    private float x, y;
    private float scroll = 0f;
    private float smoothScroll = 0f;

    public AutoBuyHistoryScreen() {
        super(Text.literal("AutoBuy History"));
        alpha.setValue(0f);
    }

    @Override
    protected void init() {
        super.init();
        this.x = (client.getWindow().getScaledWidth() - WIDTH) / 2f;
        this.y = (client.getWindow().getScaledHeight() - HEIGHT) / 2f;
        alpha.setDuration((long) (ANIM_S * 1000));
        alpha.setEasing(Easing.CUBIC_OUT);
        alpha.animateTo(1.0f);
    }

    @Override
    public void tick() {
        super.tick();
        alpha.update();
        if (alpha.getTargetValue() == 0.0D && !(!alpha.isDone())) {
            client.setScreen(null);
        }
    }

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float partialTick) {
        float a = alpha.getValue();
        int bgAlpha = (int) (a * 200);
        RenderUtil.drawRoundedRect(g, 0, 0, client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight(), 0f, new Color(0, 0, 0, bgAlpha).getRGB());
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float partialTicks) {
        float a = alpha.getValue();
        if (a <= 0.01f) return;

        int panelAlpha = (int) (a * 230);
        int headerAlpha = (int) (a * 255);

        RenderUtil.Blur.drawBlur(g, x, y, WIDTH, HEIGHT, 8f, 10, -1);
        RenderUtil.drawRoundedRect(g, x, y, WIDTH, HEIGHT, 8f, new Color(18, 14, 22, panelAlpha).getRGB());
        endless.ere.utility.render.display.shader.DrawUtil.drawHudGrid(
                g.getMatrices(), x, y, WIDTH, HEIGHT,
                BorderRadius.all(8f), 14f, 0.6f, 0.045f);

        renderHeader(g, mouseX, mouseY, headerAlpha);
        renderList(g, a);
    }

    private void renderHeader(DrawContext g, int mouseX, int mouseY, int headerAlpha) {
        int themeColor = Endless.getInstance().getThemeManager().getCurrentTheme().getColor()
                .withAlpha(headerAlpha).getRGB();

        Fonts.MEDIUM.getFont(10).drawString(g, "AutoBuy",
                x + 14f, y + 10f, new Color(235, 235, 235, headerAlpha).getRGB());
        Fonts.MEDIUM.getFont(8).drawString(g, "История",
                x + 14f + Fonts.MEDIUM.getFont(10).getStringWidth("AutoBuy") + 5f,
                y + 12f, themeColor);

        // кнопка Цены справа
        String btn = "Цены";
        float bw = Fonts.MEDIUM.getFont(7).getStringWidth(btn) + 14f;
        float bh = 14f;
        float bx = x + WIDTH - bw - 14f;
        float by = y + 8f;
        boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int bg = hovered ? new Color(90, 36, 60, (int)(headerAlpha * 0.85f)).getRGB()
                : new Color(40, 32, 50, (int)(headerAlpha * 0.7f)).getRGB();
        RenderUtil.drawRoundedRect(g, bx, by, bw, bh, 3f, bg);
        Fonts.MEDIUM.getFont(7).drawString(g, btn, bx + 7f, by + 3f,
                new Color(255, 255, 255, headerAlpha).getRGB());

        int sepAlpha = (int) (headerAlpha * 0.12f);
        RenderUtil.drawRoundedRect(g, x + 10f, y + HEADER_H - 2f,
                WIDTH - 20f, 0.6f, 0.3f, new Color(255, 255, 255, sepAlpha).getRGB());
    }

    private void renderList(DrawContext g, float a) {
        float listX = x + PAD;
        float listY = y + HEADER_H + 2f;
        float listW = WIDTH - PAD * 2;

        smoothScroll += (scroll - smoothScroll) * 0.2f;

        List<PurchaseRecord> records = HistoryManager.getInstance().getHistory();

        g.enableScissor((int) listX, (int) listY, (int) (listX + listW), (int) (listY + LIST_H));

        if (records.isEmpty()) {
            String empty = "Пусто";
            float w = Fonts.MEDIUM.getFont(8).getStringWidth(empty);
            Fonts.MEDIUM.getFont(8).drawString(g, empty,
                    listX + (listW - w) / 2f,
                    listY + LIST_H / 2f - 5f,
                    new Color(140, 140, 140, (int)(a * 255)).getRGB());
        } else {
            float cy = listY + smoothScroll;
            for (PurchaseRecord rec : records) {
                if (cy + ITEM_H >= listY - ITEM_H && cy <= listY + LIST_H + ITEM_H) {
                    renderRow(g, rec, listX, cy, listW, a);
                }
                cy += ITEM_H + ITEM_GAP;
            }
        }

        g.disableScissor();

        // скроллбар
        float contentH = records.size() * (ITEM_H + ITEM_GAP) - ITEM_GAP;
        float maxScroll = Math.max(0f, contentH - LIST_H);
        if (maxScroll > 0) {
            float thumbH = Math.max(15f, LIST_H * (LIST_H / contentH));
            float scrollPercent = Math.abs(smoothScroll) / maxScroll;
            float thumbY = listY + (LIST_H - thumbH) * scrollPercent;
            float scrollX = listX + listW - 3f;
            int scrollColor = new Color(255, 255, 255, (int)(a * 120)).getRGB();
            RenderUtil.drawRoundedRect(g, scrollX, thumbY, 2f, thumbH, 1f, scrollColor);
        }
    }

    private void renderRow(DrawContext g, PurchaseRecord rec, float rx, float ry, float rw, float a) {
        RenderUtil.drawRoundedRect(g, rx, ry, rw, ITEM_H, 4f,
                new Color(28, 22, 36, (int)(a * 200)).getRGB());

        float iconSize = 14f;
        float iconX = rx + 6f;
        float iconY = ry + (ITEM_H - 16) / 2f;

        ItemStack stack = rec.getItem();
        if (stack == null || stack.isEmpty()) stack = new ItemStack(Items.PAPER);
        g.drawItem(stack, (int) iconX, (int) iconY);

        float textX = iconX + iconSize + 7f;

        Fonts.MEDIUM.getFont(7).drawString(g, rec.getDisplayName(),
                textX, ry + 6f, new Color(230, 230, 230, (int)(a * 255)).getRGB());

        Fonts.MEDIUM.getFont(6).drawString(g, rec.getSellerName() + " · " + rec.getFormattedTime(),
                textX, ry + 16f, new Color(140, 140, 140, (int)(a * 255)).getRGB());

        // цена справа в цвете темы
        int themeColor = Endless.getInstance().getThemeManager().getCurrentTheme().getColor()
                .withAlpha((int)(a * 255)).getRGB();
        String price = rec.getFormattedPrice();
        float pw = Fonts.MEDIUM.getFont(7).getStringWidth(price);
        Fonts.MEDIUM.getFont(7).drawString(g, price, rx + rw - pw - 8f, ry + 11f, themeColor);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // кнопка "Цены"
            String btn = "Цены";
            float bw = Fonts.MEDIUM.getFont(7).getStringWidth(btn) + 14f;
            float bh = 14f;
            float bx = x + WIDTH - bw - 14f;
            float by = y + 8f;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) {
                client.setScreen(new AutoBuyPriceScreen());
                return true;
            }
        }
        if (button == 1) {
            HistoryManager.getInstance().clearHistory();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        List<PurchaseRecord> records = HistoryManager.getInstance().getHistory();
        float contentH = records.size() * (ITEM_H + ITEM_GAP) - ITEM_GAP;
        float maxScroll = Math.max(0f, contentH - LIST_H);
        scroll += (float) (vertical * (ITEM_H + ITEM_GAP));
        if (scroll > 0f) scroll = 0f;
        if (scroll < -maxScroll) scroll = -maxScroll;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        alpha.setDuration((long) (ANIM_S * 1000));
        alpha.setEasing(Easing.CUBIC_IN);
        alpha.animateTo(0f);
    }
}

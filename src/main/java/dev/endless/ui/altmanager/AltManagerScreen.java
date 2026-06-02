package dev.endless.ui.altmanager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

/**
 * Offline alt manager.
 *
 * Layout (simplified):
 *   <ul>
 *     <li>Left column — current nickname display, username input, Login / Back buttons.</li>
 *     <li>Right column — scrollable history of recently used nicknames.</li>
 *   </ul>
 *
 * Each successful login is appended to {@link NicknameHistory}; clicking a
 * history entry copies it to the input field and right-clicking removes it.
 */
public class AltManagerScreen extends Screen {

    private static final int FORM_WIDTH    = 220;
    private static final int FIELD_HEIGHT  = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int HISTORY_WIDTH = 180;
    private static final int HISTORY_ROW   = 18;

    private final Screen parent;
    private final NicknameHistory history = new NicknameHistory();

    private TextFieldWidget usernameField;
    private NicknameHistoryListWidget historyList;
    private String currentUsername;
    private String statusMessage = "";
    private int statusColor = 0xFFCCCCCC;

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
        this.currentUsername = MinecraftClient.getInstance().getSession().getUsername();
    }

    @Override
    protected void init() {
        super.init();

        int totalWidth = FORM_WIDTH + 16 + HISTORY_WIDTH;
        int leftX  = (this.width - totalWidth) / 2;
        int rightX = leftX + FORM_WIDTH + 16;

        int formTop = this.height / 2 - 60;

        // ── Left: form ───────────────────────────────────────────────────
        usernameField = new TextFieldWidget(
                this.textRenderer,
                leftX, formTop + 50,
                FORM_WIDTH, FIELD_HEIGHT,
                Text.literal("Username")
        );
        usernameField.setMaxLength(16);
        usernameField.setText(currentUsername);
        usernameField.setPlaceholder(Text.literal("Введи ник…"));
        addDrawableChild(usernameField);

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Войти"),
                btn -> loginOffline()
        ).dimensions(leftX, formTop + 80, FORM_WIDTH, BUTTON_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Назад"),
                btn -> close()
        ).dimensions(leftX, formTop + 110, FORM_WIDTH, BUTTON_HEIGHT).build());

        // ── Right: history list ──────────────────────────────────────────
        int listHeight = 150;
        historyList = new NicknameHistoryListWidget(
                this.client,
                HISTORY_WIDTH,
                listHeight,
                formTop + 30,
                HISTORY_ROW,
                this::pickFromHistory,
                this::deleteFromHistory
        );
        historyList.setX(rightX);
        historyList.setEntries(history.getEntries());
        addDrawableChild(historyList);

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Очистить историю"),
                btn -> {
                    history.clear();
                    historyList.setEntries(history.getEntries());
                    setStatus("История очищена", 0xFFCCCCCC);
                }
        ).dimensions(rightX, formTop + 30 + listHeight + 6, HISTORY_WIDTH, BUTTON_HEIGHT).build());

        setInitialFocus(usernameField);
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private void pickFromHistory(String nickname) {
        if (usernameField == null) return;
        usernameField.setText(nickname);
        setInitialFocus(usernameField);
    }

    private void deleteFromHistory(String nickname) {
        history.remove(nickname);
        historyList.setEntries(history.getEntries());
        setStatus("Удалено: " + nickname, 0xFFCCCCCC);
    }

    private void loginOffline() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            setStatus("Введи ник", 0xFFFF8866);
            return;
        }
        if (username.length() < 3 || username.length() > 16) {
            setStatus("Длина ника: 3..16", 0xFFFF8866);
            return;
        }

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());

            Session newSession = new Session(
                    username,
                    uuid,
                    "",
                    Optional.empty(),
                    Optional.empty(),
                    Session.AccountType.LEGACY
            );

            // Reflectively swap the session — Minecraft has no public setter.
            Field sessionField = MinecraftClient.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            sessionField.set(mc, newSession);

            currentUsername = username;
            history.add(username);
            historyList.setEntries(history.getEntries());

            setStatus("Ник изменён: " + username, 0xFF66FF88);

            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("§aНик изменен: §e" + username), false);
            }
        } catch (ReflectiveOperationException e) {
            setStatus("Ошибка смены ника", 0xFFFF6666);
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("§cAlt Manager: смена ника не удалась"), false);
            }
        }
    }

    private void setStatus(String text, int color) {
        this.statusMessage = text;
        this.statusColor = color;
    }

    // ── Rendering ───────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int totalWidth = FORM_WIDTH + 16 + HISTORY_WIDTH;
        int leftX = (this.width - totalWidth) / 2;
        int rightX = leftX + FORM_WIDTH + 16;
        int formTop = this.height / 2 - 60;

        // Title
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                "Alt Manager",
                this.width / 2,
                formTop - 16,
                0xFFFFFFFF
        );

        // Current nickname (left column header)
        context.drawTextWithShadow(this.textRenderer,
                "Текущий ник:",
                leftX, formTop,
                0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                "§e" + currentUsername,
                leftX, formTop + 12,
                0xFFFFFFFF);

        // History column header
        context.drawTextWithShadow(this.textRenderer,
                "История ников (" + history.getEntries().size() + ")",
                rightX, formTop + 18,
                0xFFAAAAAA);

        // Status line
        if (!statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    statusMessage,
                    this.width / 2,
                    formTop + 140,
                    statusColor);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / NumpadEnter
            loginOffline();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

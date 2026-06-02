package dev.endless.ui.altmanager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Scrollable nickname-history list shown on the right side of the Alt Manager.
 *
 * Each row displays a saved nickname and exposes two actions:
 *   <ul>
 *     <li>Click — fill the username text field with the entry.</li>
 *     <li>Right-click / 'Удалить' button hover-removal — drop the entry from history.</li>
 *   </ul>
 */
public class NicknameHistoryListWidget extends AlwaysSelectedEntryListWidget<NicknameHistoryListWidget.Entry> {

    private final Consumer<String> onPick;
    private final Consumer<String> onDelete;

    public NicknameHistoryListWidget(MinecraftClient client,
                                     int width, int height, int top, int itemHeight,
                                     Consumer<String> onPick,
                                     Consumer<String> onDelete) {
        super(client, width, height, top, itemHeight);
        this.onPick = onPick;
        this.onDelete = onDelete;
    }

    /**
     * Replaces the displayed entries with a fresh snapshot of history.
     */
    public void setEntries(List<String> nicknames) {
        clearEntries();
        for (String nick : nicknames) {
            addEntry(new Entry(nick));
        }
    }

    @Override
    public int getRowWidth() {
        return this.width - 8;
    }

    @Override
    protected int getScrollbarX() {
        return this.getX() + this.width - 6;
    }

    public class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {

        private final String nickname;

        Entry(String nickname) {
            this.nickname = nickname;
        }

        public String getNickname() {
            return nickname;
        }

        @Override
        public Text getNarration() {
            return Text.literal(nickname);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x,
                            int entryWidth, int entryHeight,
                            int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Background tint when hovered/selected.
            int bg = hovered ? 0x44FFFFFF : 0x22000000;
            context.fill(x, y, x + entryWidth, y + entryHeight, bg);

            int textColor = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
            context.drawTextWithShadow(
                    NicknameHistoryListWidget.this.client.textRenderer,
                    Text.literal(nickname),
                    x + 6,
                    y + (entryHeight - 9) / 2,
                    textColor
            );

            // "✕" delete hint on the right when hovered.
            if (hovered) {
                String hint = "✕";
                int hintW = NicknameHistoryListWidget.this.client.textRenderer.getWidth(hint);
                context.drawTextWithShadow(
                        NicknameHistoryListWidget.this.client.textRenderer,
                        hint,
                        x + entryWidth - hintW - 6,
                        y + (entryHeight - 9) / 2,
                        0xFFFF6666
                );
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Right-click or click on the trailing "✕" hint area = delete.
            int rowRight = NicknameHistoryListWidget.this.getX()
                    + NicknameHistoryListWidget.this.getRowWidth();
            boolean inDeleteHint = mouseX >= rowRight - 18;

            if (button == 1 || inDeleteHint) {
                if (onDelete != null) onDelete.accept(nickname);
                return true;
            }
            if (button == 0) {
                if (onPick != null) onPick.accept(nickname);
                return true;
            }
            return false;
        }
    }
}

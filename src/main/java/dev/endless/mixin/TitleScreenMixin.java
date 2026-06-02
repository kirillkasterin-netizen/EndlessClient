package dev.endless.mixin;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.endless.ui.altmanager.AltManagerScreen;
import dev.endless.util.discord.DiscordRPC;

import java.util.List;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    private static final String OPTIONS_KEY = "menu.options";
    private static final String QUIT_KEY    = "menu.quit";

    /**
     * After vanilla TitleScreen finishes laying out its buttons, slide the
     * Options and Quit Game buttons aside and slot a new "Alt Manager" button
     * directly between them. This keeps the row visually balanced and removes
     * the bottom-right floating button.
     */
    @Inject(method = "init", at = @At("RETURN"))
    private void wraith$insertAltManagerBetweenOptionsAndQuit(CallbackInfo ci) {
        DiscordRPC.updateInMenu();

        Screen screen = (Screen) (Object) this;
        ButtonWidget options = findVanillaButton(screen, OPTIONS_KEY);
        ButtonWidget quit    = findVanillaButton(screen, QUIT_KEY);

        if (options == null || quit == null) {
            // Vanilla layout changed — fall back to a corner button so the user
            // still has access to Alt Manager.
            screen.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Alt Manager"),
                    btn -> openAltManager(screen)
            ).dimensions(screen.width - 110, screen.height - 28, 100, 20).build());
            return;
        }

        // Three buttons of width N each, gap = 4 between them.
        // Total = 3N + 8.  Pick N so the row keeps roughly its original width.
        final int btnW = 78;
        final int btnH = 20;
        final int gap  = 4;
        final int totalW = btnW * 3 + gap * 2;
        int rowY = options.getY();
        int rowX = (screen.width - totalW) / 2;

        options.setX(rowX);
        options.setY(rowY);
        options.setWidth(btnW);

        ButtonWidget altBtn = ButtonWidget.builder(
                Text.literal("Alt Manager"),
                btn -> openAltManager(screen)
        ).dimensions(rowX + btnW + gap, rowY, btnW, btnH).build();
        screen.addDrawableChild(altBtn);

        quit.setX(rowX + (btnW + gap) * 2);
        quit.setY(rowY);
        quit.setWidth(btnW);
    }

    /**
     * Locates a vanilla button by its translation key.
     */
    private ButtonWidget findVanillaButton(Screen screen, String translationKey) {
        List<? extends Element> children = screen.children();
        for (Element child : children) {
            if (child instanceof ButtonWidget btn) {
                Text msg = btn.getMessage();
                if (msg != null && translationKey.equals(msg.getContent() instanceof net.minecraft.text.TranslatableTextContent t ? t.getKey() : null)) {
                    return btn;
                }
            } else if (child instanceof ClickableWidget cw) {
                // No-op; only ButtonWidget carries our target message.
                if (cw instanceof ButtonWidget btn) {
                    Text msg = btn.getMessage();
                    if (msg != null && translationKey.equals(msg.getContent() instanceof net.minecraft.text.TranslatableTextContent t ? t.getKey() : null)) {
                        return btn;
                    }
                }
            }
        }
        return null;
    }

    private void openAltManager(Screen parent) {
        if (parent.client != null) {
            parent.client.setScreen(new AltManagerScreen(parent));
        }
    }
}

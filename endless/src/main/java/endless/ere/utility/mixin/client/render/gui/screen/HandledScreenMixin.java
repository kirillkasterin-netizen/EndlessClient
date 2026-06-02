package endless.ere.utility.mixin.client.render.gui.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import endless.ere.client.modules.impl.misc.AHHelper;
import endless.ere.utility.game.server.AutoBuyUtil;
import endless.ere.client.modules.impl.misc.AutoBuy;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Unique
    @Mutable
    private boolean isAuc;
    @Unique
    @Mutable
    private Slot lowSumSlotId = null;
    @Unique
    @Mutable
    private Slot lowAllSumSlotId = null;

    @Shadow
    public abstract ScreenHandler getScreenHandler();


    @Shadow @Final protected ScreenHandler handler;

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickScreen(CallbackInfo ci) {

        if (!isAuc &&AHHelper.INSTANCE.isEnabled()) {
            isAuc = AutoBuyUtil.isAuction(this.handler);

        }

        if (isAuc&&AHHelper.INSTANCE.isEnabled()) {
            int lowSum = Integer.MAX_VALUE;
            int allSum = Integer.MAX_VALUE;
            for (int i = 0; i < 44; i++) {
                Slot slot = this.getScreenHandler().slots.get(i);
                if (slot.getStack().isEmpty()) continue;
                int sum = AutoBuyUtil.getPrice(slot.getStack());
                if (sum < lowSum) {
                    lowSumSlotId = slot;
                    lowSum = sum;
                }
                if (sum / slot.getStack().getCount() < allSum) {
                    allSum = sum / slot.getStack().getCount();
                    lowAllSumSlotId = slot;
                }
            }
        }
    }



    @Inject(
            method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;)V",
            at = @At(
                    value = "HEAD"
            )
    )
    private void onDrawSlotInject(DrawContext context, Slot slot, CallbackInfo ci) {

        if (AHHelper.INSTANCE.isEnabled() ) {

            if((slot == lowSumSlotId) ){
                AHHelper.INSTANCE.renderCheat(context, slot);
            }else if((slot == lowAllSumSlotId) ){
                AHHelper.INSTANCE.renderGood(context, slot);
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (AutoBuy.INSTANCE.isEnabled()) {
            HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
            String titleStr = screen.getTitle().getString();
            if (titleStr.contains("Поиск:") || titleStr.contains("Аукцион")) {
                int x = 5;
                int y = 5;
                int w = 70;
                int h = 15;
                
                // Кнопка 1: Start AutoBuy / Stop AutoBuy
                boolean autoBuyOn = AutoBuy.INSTANCE.isAutoBuyActive();
                boolean hovered1 = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
                int bg1;
                String label1;
                if (autoBuyOn) {
                    bg1 = 0xC0AA0000;
                    label1 = "Stop AutoBuy";
                } else {
                    bg1 = hovered1 ? 0x80FFFFFF : 0x80000000;
                    label1 = "Start AutoBuy";
                }
                context.fill(x, y, x + w, y + h, bg1);
                context.drawText(MinecraftClient.getInstance().textRenderer, label1, x + 4, y + 4, -1, false);
                
                // Кнопка 2: Парсить всё (или прогресс парсинга)
                int x2 = x + w + 5;
                int w2 = 80;
                boolean parsing = AutoBuy.INSTANCE.isParsing();
                boolean batchHovered = mouseX >= x2 && mouseX <= x2 + w2 && mouseY >= y && mouseY <= y + h;
                int bg2;
                String label2;
                if (parsing) {
                    bg2 = 0xC0AA0000;
                    label2 = "Stop " + AutoBuy.INSTANCE.getParseProgress() + "/" + AutoBuy.INSTANCE.getParseTotal();
                } else {
                    bg2 = batchHovered ? 0x80AA00AA : 0x80330033;
                    label2 = "Парсить всё";
                }
                context.fill(x2, y, x2 + w2, y + h, bg2);
                context.drawText(MinecraftClient.getInstance().textRenderer, label2, x2 + 5, y + 4, -1, false);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (AutoBuy.INSTANCE.isEnabled() && button == 0) {
            HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
            String titleStr = screen.getTitle().getString();
            if (titleStr.contains("Поиск:") || titleStr.contains("Аукцион")) {
                int x = 5;
                int y = 5;
                int w = 70;
                int h = 15;
                // кнопка Start/Stop AutoBuy
                if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                    AutoBuy.INSTANCE.startAutoBuy(); // toggle
                    cir.setReturnValue(true);
                    return;
                }
                
                // кнопка "Парсить всё" / Stop парсинга
                int x2 = x + w + 5;
                int w2 = 80;
                if (mouseX >= x2 && mouseX <= x2 + w2 && mouseY >= y && mouseY <= y + h) {
                    if (AutoBuy.INSTANCE.isParsing()) {
                        AutoBuy.INSTANCE.stopParse();
                    } else {
                        AutoBuy.INSTANCE.parseAllEnabled();
                    }
                    cir.setReturnValue(true);
                }
            }
        }
    }
}

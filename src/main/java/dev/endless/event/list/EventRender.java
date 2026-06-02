package dev.endless.event.list;

import net.minecraft.client.util.math.MatrixStack;
import dev.endless.event.Event;

public class EventRender extends Event {
    private final MatrixStack matrixStack;
    private final float partialTicks;

    public EventRender(MatrixStack matrixStack, float partialTicks) {
        this.matrixStack = matrixStack;
        this.partialTicks = partialTicks;
    }

    public MatrixStack getMatrixStack() {
        return matrixStack;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}

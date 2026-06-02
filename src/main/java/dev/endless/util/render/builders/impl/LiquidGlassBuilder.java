package dev.endless.util.render.builders.impl;

import dev.endless.util.render.builders.AbstractBuilder;
import dev.endless.util.render.builders.states.QuadColorState;
import dev.endless.util.render.builders.states.QuadRadiusState;
import dev.endless.util.render.builders.states.SizeState;
import dev.endless.util.render.renderers.impl.BuiltLiquidGlass;

public final class LiquidGlassBuilder extends AbstractBuilder<BuiltLiquidGlass> {

    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;
    private float smoothness;

    public LiquidGlassBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public LiquidGlassBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public LiquidGlassBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public LiquidGlassBuilder smoothness(float smoothness) {
        this.smoothness = smoothness;
        return this;
    }

    @Override
    protected BuiltLiquidGlass _build() {
        return new BuiltLiquidGlass(
            this.size,
            this.radius,
            this.color,
            this.smoothness
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.color = QuadColorState.WHITE;
        this.smoothness = 1.0f;
    }
}

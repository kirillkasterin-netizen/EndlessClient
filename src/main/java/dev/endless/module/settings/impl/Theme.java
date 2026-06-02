package dev.endless.module.settings.impl;

import dev.endless.util.render.math.Animation;
import dev.endless.util.render.math.Easing;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

import java.awt.*;

public class Theme {
    public Animation animation = new Animation(Easing.QUINTIC_OUT, 550);
    public Animation checkAnimation = new Animation(Easing.QUINTIC_OUT, 350);

    public float x;
    public float y;
    public String name;
    public int color1;
    public int color2;

    public int colorMain, colorVisualModules, colorText, colorInactiveText;
    public int colorHeaderBg, colorHeaderText, colorSlider, colorSliderCircle;
    public int colorSliderWindow, colorIndicator, colorInactiveIndicator;
    public int colorButton, colorInactiveButton, colorSeparator;
    public int colorField, colorInactiveField, colorTooltipText;
    public int colorWindowBg, colorIcons, colorClient;

    private int fromColor1, fromColor2;

    public Theme(String name, int color1, int color2) {
        this.name = name;
        this.color1 = saturateColor(color1);
        this.color2 = saturateColor(color2);
        this.fromColor1 = this.color1;
        this.fromColor2 = this.color2;
        applyDefaultsFromColors(this.color1, this.color2);
    }

    private void applyDefaultsFromColors(int c1, int c2) {
        colorMain=0xFF0D0D10; colorVisualModules=c1; colorText=0xFFE8E8E8;
        colorInactiveText=0xFF888899; colorHeaderBg=0xFF0A0A0D; colorHeaderText=c1;
        colorSlider=c1; colorSliderCircle=0xFFFFFFFF; colorSliderWindow=0xFF2A2A35;
        colorIndicator=c1; colorInactiveIndicator=0xFF2A2A35; colorButton=c1;
        colorInactiveButton=0xFF2A2A35; colorSeparator=0xFF2A2A35; colorField=0xFF1A1A22;
        colorInactiveField=c2; colorTooltipText=0xFFFFFFFF; colorWindowBg=c2;
        colorIcons=c1; colorClient=c1;
    }

    public void setColors(int newColor1, int newColor2) {
        int sat1 = saturateColor(newColor1);
        int sat2 = saturateColor(newColor2);
        if (this.color1 != sat1 || this.color2 != sat2) {
            startAnimation(this.color1, this.color2);
            this.color1 = sat1;
            this.color2 = sat2;
        }
    }

    public void setAllColors(int c1, int c2,
                             int main, int visualModules, int text, int inactiveText,
                             int headerBg, int headerText, int slider, int sliderCircle,
                             int sliderWindow, int indicator, int inactiveIndicator,
                             int button, int inactiveButton, int separator,
                             int field, int inactiveField, int tooltipText,
                             int windowBg, int icons, int client) {
        int sat1 = saturateColor(c1), sat2 = saturateColor(c2);
        if (this.color1 != sat1 || this.color2 != sat2) {
            startAnimation(this.color1, this.color2);
            this.color1 = sat1; this.color2 = sat2;
        }
        colorMain=main; colorVisualModules=visualModules; colorText=text;
        colorInactiveText=inactiveText; colorHeaderBg=headerBg; colorHeaderText=headerText;
        colorSlider=slider; colorSliderCircle=sliderCircle; colorSliderWindow=sliderWindow;
        colorIndicator=indicator; colorInactiveIndicator=inactiveIndicator;
        colorButton=button; colorInactiveButton=inactiveButton; colorSeparator=separator;
        colorField=field; colorInactiveField=inactiveField; colorTooltipText=tooltipText;
        colorWindowBg=windowBg; colorIcons=icons; colorClient=client;
    }

    public void startAnimation(int oldColor1, int oldColor2) {
        this.fromColor1 = oldColor1; this.fromColor2 = oldColor2;
        animation.reset();
    }

    public int getColorFirst() {
        animation.run(true);
        float progress = (float) Math.max(0, Math.min(1, animation.getValue()));
        return interpolateColorClean(fromColor1, color1, progress);
    }

    public int getColorSecond() {
        float progress = (float) Math.max(0, Math.min(1, animation.getValue()));
        return interpolateColorClean(fromColor2, color2, progress);
    }

    public int getColorTheme(int index) {
        return getGradientClean(3, index, getColorFirst(), getColorSecond());
    }

    public int getStaticColorTheme(int index) {
        return getGradientClean(Integer.MAX_VALUE, index, getColorFirst(), getColorSecond());
    }

    public static int getGradientClean(int speed, int index, int c1, int c2) {
        if (speed == Integer.MAX_VALUE || speed == 0) return c1;
        int time = (int)(System.currentTimeMillis() / Math.max(1, speed) + index);
        float angle = (time % 360) / 360f;
        float factor = (float)(-Math.cos(angle * Math.PI * 2) * 0.5 + 0.5);
        return interpolateColorClean(c1, c2, factor);
    }

    public static int interpolateColorClean(int color1, int color2, float amount) {
        amount = Math.min(1.0f, Math.max(0.0f, amount));
        int a1=(color1>>24)&0xFF, r1=(color1>>16)&0xFF, g1=(color1>>8)&0xFF, b1=color1&0xFF;
        int a2=(color2>>24)&0xFF, r2=(color2>>16)&0xFF, g2=(color2>>8)&0xFF, b2=color2&0xFF;
        return ((int)(a1+(a2-a1)*amount)<<24)|((int)(r1+(r2-r1)*amount)<<16)
             |((int)(g1+(g2-g1)*amount)<<8)|(int)(b1+(b2-b1)*amount);
    }

    public void drawTheme(double alpha) {
        if (this.name != null && this.name.equals("Радужный")) {
            int[] rainbowColors = new int[4];
            for (int i = 0; i < 4; i++)
                rainbowColors[i] = Color.HSBtoRGB((float)(i / 4.0f), 0.8f, 1.0f);
            DrawUtil.drawRound(x, y, 16, 15, 1,
                ColorProvider.setAlpha(rainbowColors[0], alpha),
                ColorProvider.setAlpha(rainbowColors[1], alpha),
                ColorProvider.setAlpha(rainbowColors[2], alpha),
                ColorProvider.setAlpha(rainbowColors[3], alpha));
        } else {
            String renderName = this.name != null ? this.name : "Custom";
            DrawUtil.drawText(Fonts.SFREGULAR.get(), renderName, x+2, y+2.25f,
                ColorProvider.setAlpha(-1, (160 + checkAnimation.getValue() * 95) * alpha), 7);
            DrawUtil.drawRound(x+71.5f, y, 19, 9, 2,
                ColorProvider.brighter(ColorProvider.setAlpha(color1, alpha*255), (float)(0.5f+checkAnimation.getValue()*0.5f)),
                ColorProvider.brighter(ColorProvider.setAlpha(color1, alpha*255), (float)(0.5f+checkAnimation.getValue()*0.5f)),
                ColorProvider.brighter(ColorProvider.setAlpha(color2, alpha*255), (float)(0.5f+checkAnimation.getValue()*0.5f)),
                ColorProvider.brighter(ColorProvider.setAlpha(color2, alpha*255), (float)(0.5f+checkAnimation.getValue()*0.5f)));
        }
    }

    private int saturateColor(int color) {
        int alpha = (color >> 24) & 0xFF;
        float[] hsb = Color.RGBtoHSB((color>>16)&0xFF, (color>>8)&0xFF, color&0xFF, null);
        hsb[1] = Math.min(1.0f, hsb[1] * 1.5f);
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        return (rgb & 0x00FFFFFF) | (alpha << 24);
    }
}

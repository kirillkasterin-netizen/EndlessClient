/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.authlib.GameProfile
 *  lombok.Generated
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.client.network.PlayerListEntry
 *  net.minecraft.client.util.DefaultSkinHelper
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 *  net.minecraft.world.GameMode
 */
package endless.ere.client.hud.elements.component;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Generated;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.utility.game.other.TextUtil;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

public class StaffComponent
extends DraggableHudElement {
    private final Animation animationWidth = new Animation(200L, 100.0f, Easing.QUAD_IN_OUT);
    private final Animation animationScale = new Animation(200L, 0.0f, Easing.QUAD_IN_OUT);
    private final Animation animationVisible = new Animation(200L, 0.0f, Easing.QUAD_IN_OUT);
    private final Map<String, StaffModule> modules = new LinkedHashMap<String, StaffModule>();
    private final Set<String> staffPrefix = Set.of("helper", "\u1d00\u0434\u043c\u0438\u043d", "moder", "staff", "admin", "curator", "\u0441\u0442\u0430\u0436\u0451\u0440", "\u0441\u043e\u0442\u0440\u0443\u0434\u043d\u0438\u043a", "\u043f\u043e\u043c\u043e\u0449\u043d\u0438\u043a", "\u0430\u0434\u043c\u0438\u043d", "\u043c\u043e\u0434\u0435\u0440");
    private final Map<String, Identifier> skinTextureCache = new HashMap<String, Identifier>();
    private long lastStaffUpdate = 0L;
    private long lastSkinCacheClear = 0L;
    private final Set<String> currentStaffKeys = new HashSet<String>();

    public StaffComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    @Override
    public void render(CustomDrawContext ctx) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastStaffUpdate > 1000L && mc.getNetworkHandler() != null) {
            this.updateStaffList();
            this.lastStaffUpdate = currentTime;
        }
        if (currentTime - this.lastSkinCacheClear > 30000L) {
            this.skinTextureCache.clear();
            this.lastSkinCacheClear = currentTime;
        }
        this.modules.entrySet().removeIf(entry -> ((StaffModule)entry.getValue()).isDelete());
        boolean hidden = this.modules.size() == 1 && this.modules.values().iterator().next().animation.getTargetValue() == 0.0f;
        this.animationScale.update(!hidden ? 1 : 0);
        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        Font iconFont = Fonts.ICONS.getFont(6.0f);
        Font font = Fonts.MEDIUM.getFont(6.0f);
        float x = this.x;
        float y = this.y;
        float height = (float)(18.0 + this.modules.values().stream().mapToDouble(StaffModule::getHeight).sum());
        float width = (float)this.modules.values().stream().mapToDouble(StaffModule::updateWidth).max().orElse(100.0);
        this.width = width = this.animationWidth.update(width);
        this.height = height;
        ctx.pushMatrix();
        float borderAnim = this.animationScale.getValue() * 4.0f;
        BorderRadius radius = new BorderRadius(4.0f, 4.0f, borderAnim, borderAnim);
        this.animationVisible.update(StaffComponent.mc.currentScreen instanceof ChatScreen || !this.modules.isEmpty());
        ctx.getMatrices().translate(x + width / 2.0f, y + height / 2.0f, 0.0f);
        ctx.getMatrices().scale(this.animationVisible.getValue(), this.animationVisible.getValue(), 1.0f);
        ctx.getMatrices().translate(-(x + width / 2.0f), -(y + height / 2.0f), 0.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, height, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
        ctx.drawRoundedRect(x, y, width, height, radius, theme.getForegroundLight().mulAlpha(0.85f));
        BorderRadius headerRadius = height > 18.5f ? BorderRadius.top(4.0f, 4.0f) : BorderRadius.all(4.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, 18.0f, 35.0f, headerRadius, ColorRGBA.WHITE);
        ctx.drawRoundedRect(x, y, width, 18.0f, headerRadius, theme.getBackgroundColor().mulAlpha(0.65f));
        DrawUtil.drawHudBackground(ctx.getMatrices(), x, y, width, 18.0f, headerRadius, 0.18f);
        if (height > 18.5f) {
            ctx.drawRect(x, y + 18.0f, width, 1.0f, theme.getForegroundStroke());
        }
        ctx.drawText(iconFont, "P", x + 8.0f, y + (18.0f - iconFont.height()) / 2.0f, theme.getColor());
        ctx.drawText(iconFont, "M", x + width - 8.0f - iconFont.width("M"), y + (18.0f - iconFont.height()) / 2.0f, theme.getWhiteGray());
        ctx.drawText(font, "Staffs", x + 8.0f + 8.0f + 2.0f, y + (18.0f - font.height()) / 2.0f, theme.getWhite());
        if (this.animationVisible.getValue() == 1.0f) {
            float offsetY = y + 18.0f;
            int index = 0;
            for (Map.Entry<String, StaffModule> entry2 : this.modules.entrySet()) {
                StaffModule module = entry2.getValue();
                module.render(ctx, x, offsetY, width, index, this.currentStaffKeys.contains(entry2.getKey()));
                offsetY += module.getHeight();
                ++index;
            }
        }
        ctx.drawRoundedBorder(x, y, width, height, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
        DrawUtil.drawRoundedCorner(ctx.getMatrices(), x, y, width, height, 0.1f, Math.min(20.0f, Math.max(12.0f, height / 2.5f)), theme.getColor(), BorderRadius.all(4.0f));
        ctx.popMatrix();
    }

    private void updateStaffList() {
        if (mc.getNetworkHandler() == null) {
            return;
        }
        this.currentStaffKeys.clear();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String name;
            String display;
            String prefix;
            GameProfile profile = entry.getProfile();
            Text displayName = entry.getDisplayName();
            if (displayName == null || profile == null || (prefix = (display = displayName.getString()).replace(name = profile.getName(), "").trim()).length() < 2 || !this.containsAnyKeyword(prefix)) continue;
            String key = display;
            Status status = entry.getGameMode() == GameMode.SPECTATOR ? Status.VANISHED : Status.NONE;
            Text finalDisplayName = displayName = displayName.getString().contains(profile.getName()) ? TextUtil.truncateAfterSubstring(displayName, profile.getName(), false) : TextUtil.truncateAfterSecondSpace(displayName, false);
            this.modules.computeIfAbsent(key, k -> new StaffModule(finalDisplayName, key, name, status));
            this.currentStaffKeys.add(key);
        }
    }

    public boolean containsAnyKeyword(String text) {
        String lower = text.toLowerCase(Locale.US);
        for (String keyword : this.staffPrefix) {
            if (!lower.contains(keyword)) continue;
            return true;
        }
        return false;
    }

    private String formatTime(long ms) {
        long minutes = ms / 60000L;
        long seconds = ms % 60000L / 1000L;
        return String.format("%d:%02d", minutes, seconds);
    }

    public static class Staff {
        private Text prefix;
        private String name;
        private boolean isSpec;
        private Status status;

        @Generated
        public Text getPrefix() {
            return this.prefix;
        }

        @Generated
        public String getName() {
            return this.name;
        }

        @Generated
        public boolean isSpec() {
            return this.isSpec;
        }

        @Generated
        public Status getStatus() {
            return this.status;
        }

        @Generated
        public void setPrefix(Text prefix) {
            this.prefix = prefix;
        }

        @Generated
        public void setName(String name) {
            this.name = name;
        }

        @Generated
        public void setSpec(boolean isSpec) {
            this.isSpec = isSpec;
        }

        @Generated
        public void setStatus(Status status) {
            this.status = status;
        }

        @Generated
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Staff)) {
                return false;
            }
            Staff other = (Staff)o;
            if (!other.canEqual(this)) {
                return false;
            }
            if (this.isSpec() != other.isSpec()) {
                return false;
            }
            Text this$prefix = this.getPrefix();
            Text other$prefix = other.getPrefix();
            if (this$prefix == null ? other$prefix != null : !this$prefix.equals(other$prefix)) {
                return false;
            }
            String this$name = this.getName();
            String other$name = other.getName();
            if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
                return false;
            }
            Status this$status = this.getStatus();
            Status other$status = other.getStatus();
            return !(this$status == null ? other$status != null : !((Object)((Object)this$status)).equals((Object)other$status));
        }

        @Generated
        protected boolean canEqual(Object other) {
            return other instanceof Staff;
        }

        @Generated
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            result = result * 59 + (this.isSpec() ? 79 : 97);
            Text $prefix = this.getPrefix();
            result = result * 59 + ($prefix == null ? 43 : $prefix.hashCode());
            String $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Status $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : ((Object)((Object)$status)).hashCode());
            return result;
        }

        @Generated
        public String toString() {
            return "StaffComponent.Staff(prefix=" + String.valueOf(this.getPrefix()) + ", name=" + this.getName() + ", isSpec=" + this.isSpec() + ", status=" + String.valueOf((Object)this.getStatus()) + ")";
        }

        @Generated
        public Staff(Text prefix, String name, boolean isSpec, Status status) {
            this.prefix = prefix;
            this.name = name;
            this.isSpec = isSpec;
            this.status = status;
        }
    }

    private class StaffModule {
        private final Animation animation = new Animation(150L, 0.01f, Easing.QUAD_IN_OUT);
        private final Animation animationColor = new Animation(200L, Easing.QUAD_IN_OUT);
        private final Text displayNameText;
        private final String key;
        private final String name;
        private final Status status;
        private final long appearTime;

        public StaffModule(Text displayNameText, String key, String name, Status status) {
            this.displayNameText = displayNameText;
            this.key = key;
            this.name = name;
            this.status = status;
            this.appearTime = System.currentTimeMillis();
        }

        public float updateWidth() {
            float rightTextWidth;
            float width = 100.0f;
            Font font = Fonts.MEDIUM.getFont(6.0f);
            String time = StaffComponent.this.formatTime(System.currentTimeMillis() - this.appearTime);
            float leftTextWidth = 24.0f + font.width(this.displayNameText);
            if (width - (leftTextWidth + (rightTextWidth = font.width(time) + 8.0f)) < 8.0f) {
                width += leftTextWidth + rightTextWidth + 8.0f - width;
            }
            return width;
        }

        public float getHeight() {
            return 18.0f * this.animation.getValue();
        }

        public void render(CustomDrawContext ctx, float x, float y, float width, int i, boolean present) {
            PlayerListEntry player;
            Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
            Font font = Fonts.MEDIUM.getFont(6.0f);
            this.animation.update(present ? 1 : 0);
            ctx.pushMatrix();
            ctx.getMatrices().translate(x + width / 2.0f, y + 9.0f, 0.0f);
            ctx.getMatrices().scale(this.animation.getValue(), this.animation.getValue(), 1.0f);
            ctx.getMatrices().translate(-(x + width / 2.0f), -(y + 9.0f), 0.0f);
            this.animationColor.update(i % 2 == 0 ? 1 : 0);
            ColorRGBA background = new ColorRGBA(0, 0, 0, 255);
            ctx.drawRoundedRect(x, y, width, 18.0f, i == StaffComponent.this.modules.size() - 1 ? BorderRadius.bottom(4.0f, 4.0f) : BorderRadius.ZERO, background);
            Identifier skinTexture = StaffComponent.this.skinTextureCache.get(this.name);
            if (skinTexture == null && mc.getNetworkHandler() != null && (player = (PlayerListEntry)mc.getNetworkHandler().getPlayerList().stream().filter(p -> p.getProfile() != null && this.name.equals(p.getProfile().getName())).findFirst().orElse(null)) != null && player.getSkinTextures() != null) {
                skinTexture = player.getSkinTextures().texture();
                StaffComponent.this.skinTextureCache.put(this.name, skinTexture);
            }
            if (skinTexture == null) {
                skinTexture = DefaultSkinHelper.getSteve().texture();
            }
            DrawUtil.drawPlayerHeadWithRoundedShader(ctx.getMatrices(), skinTexture, x + 6.0f, y + 6.0f, 6.0f, BorderRadius.all(1.6f), ColorRGBA.WHITE);
            ctx.drawText(Fonts.BOLD.getFont(8.0f), ".", x + 8.0f + 8.0f + 2.0f, y + 4.0f, theme.getWhiteGray());
            ctx.drawText(font, this.displayNameText, x + 8.0f + 8.0f + 8.0f, y + (18.0f - font.height()) / 2.0f);
            String timeText = StaffComponent.this.formatTime(System.currentTimeMillis() - this.appearTime);
            ctx.drawText(font, timeText, x + width - 8.0f - font.width(timeText), y + (18.0f - font.height()) / 2.0f, theme.getColor());
            ctx.popMatrix();
        }

        public boolean isDelete() {
            return this.animation.getValue() == 0.0f;
        }
    }

    public static enum Status {
        NONE,
        VANISHED;

    }
}


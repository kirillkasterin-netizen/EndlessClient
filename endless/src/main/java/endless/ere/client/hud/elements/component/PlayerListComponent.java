/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonObject
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.scoreboard.Scoreboard
 *  net.minecraft.scoreboard.ScoreboardDisplaySlot
 *  net.minecraft.scoreboard.ScoreboardObjective
 *  org.jetbrains.annotations.Nullable
 */
package endless.ere.client.hud.elements.component;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.jetbrains.annotations.Nullable;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.client.modules.impl.render.Interface;
import endless.ere.utility.render.display.base.CustomDrawContext;

public class PlayerListComponent
extends DraggableHudElement {
    private final Animation animation = new Animation(250L, Easing.BAKEK_SIZE);

    public PlayerListComponent(String name) {
        super(name, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, null);
    }

    @Override
    protected void renderXLine(CustomDrawContext ctx, DraggableHudElement.SheetCode nearest) {
    }

    @Override
    protected void renderYLine(CustomDrawContext ctx, DraggableHudElement.SheetCode nearest) {
    }

    @Override
    public void set(float x, float y) {
    }

    @Override
    public void update(float widthScreen, float heightScreen) {
    }

    @Override
    public void release() {
    }

    @Override
    public void windowResized(float newWindowWidth, float newWindowHeight) {
    }

    @Override
    public void set(CustomDrawContext ctx, float x, float y, Interface dragManager, float widthScreen, float heightScreen) {
    }

    @Override
    public JsonObject save() {
        return new JsonObject();
    }

    @Override
    public void load(JsonObject obj) {
    }

    @Override
    public void render(CustomDrawContext ctx) {
        Scoreboard scoreboard = PlayerListComponent.mc.world.getScoreboard();
        ScoreboardObjective scoreboardObjective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        this.animation.update(PlayerListComponent.mc.options.playerListKey.isPressed() && (!mc.isInSingleplayer() || PlayerListComponent.mc.player.networkHandler.getListedPlayerListEntries().size() > 1 || scoreboardObjective != null));
        if (this.animation.getValue() != 0.0f) {
            this.render(ctx, ctx.getScaledWindowWidth(), scoreboard, scoreboardObjective);
        }
        this.animation.setDuration(250L);
        this.animation.setEasing(Easing.BAKEK_SIZE);
    }

    public void render(DrawContext drawContext, int n, Scoreboard scoreboard, @Nullable ScoreboardObjective scoreboardObjective) {
        throw new Error("Unresolved compilation problems: \n\tThe method collectPlayerEntries() from the type PlayerListHud is not visible\n\tThe type PlayerListHud.ScoreDisplayEntry is not visible\n\tThe type PlayerListHud.ScoreDisplayEntry is not visible\n\tThe field PlayerListHud.hearts is not visible\n\tThe field PlayerListHud.hearts is not visible\n\tThe field PlayerListHud.header is not visible\n\tThe field PlayerListHud.header is not visible\n\tThe field PlayerListHud.footer is not visible\n\tThe field PlayerListHud.footer is not visible\n\tThe type PlayerListHud.ScoreDisplayEntry is not visible\n\tThe type PlayerListHud.ScoreDisplayEntry is not visible\n\tThe type PlayerListHud.ScoreDisplayEntry is not visible\n\tThe method renderScoreboardObjective(ScoreboardObjective, int, PlayerListHud.ScoreDisplayEntry, int, int, UUID, DrawContext) from the type PlayerListHud is not visible\n\tThe method renderLatencyIcon(DrawContext, int, int, int, PlayerListEntry) from the type PlayerListHud is not visible\n");
    }
}

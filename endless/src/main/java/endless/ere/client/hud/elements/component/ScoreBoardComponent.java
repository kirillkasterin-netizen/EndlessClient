/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.scoreboard.Scoreboard
 *  net.minecraft.scoreboard.ScoreboardDisplaySlot
 *  net.minecraft.scoreboard.ScoreboardObjective
 *  net.minecraft.scoreboard.Team
 *  net.minecraft.text.Text
 *  net.minecraft.util.Formatting
 */
package endless.ere.client.hud.elements.component;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.utility.render.display.base.CustomDrawContext;

public class ScoreBoardComponent
extends DraggableHudElement {
    public ScoreBoardComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    @Override
    public void render(CustomDrawContext ctx) {
        ScoreboardObjective scoreboardObjective2;
        ScoreboardDisplaySlot scoreboardDisplaySlot;
        Scoreboard scoreboard = ScoreBoardComponent.mc.world.getScoreboard();
        ScoreboardObjective scoreboardObjective = null;
        Team team = scoreboard.getScoreHolderTeam(ScoreBoardComponent.mc.player.getNameForScoreboard());
        if (team != null && (scoreboardDisplaySlot = ScoreboardDisplaySlot.fromFormatting((Formatting)team.getColor())) != null) {
            scoreboardObjective = scoreboard.getObjectiveForSlot(scoreboardDisplaySlot);
        }
        ScoreboardObjective scoreboardObjective3 = scoreboardObjective2 = scoreboardObjective != null ? scoreboardObjective : scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (scoreboardObjective2 != null) {
            this.renderScoreboardSidebar(ctx, scoreboardObjective2);
        }
    }

    private void renderScoreboardSidebar(DrawContext drawContext, ScoreboardObjective scoreboardObjective) {
        throw new Error("Unresolved compilation problem: \n\tSCOREBOARD_ENTRY_COMPARATOR cannot be resolved to a variable\n");
    }

    @Environment(value=EnvType.CLIENT)
    private static final class SidebarRow {
        private final Text name;
        private final Text score;
        private final int scoreWidth;

        private SidebarRow(Text name, Text score, int scoreWidth) {
            this.name = name;
            this.score = score;
            this.scoreWidth = scoreWidth;
        }

        public Text name() {
            return this.name;
        }

        public Text score() {
            return this.score;
        }

        public int scoreWidth() {
            return this.scoreWidth;
        }
    }
}

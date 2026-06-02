package dev.endless.util.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import org.apache.commons.lang3.StringUtils;

/**
 * Простая утилита для детекта типа сервера по brand + IP + scoreboard.
 * Аналог Network.java из essence fix, минимально нужное для AutoBuy / HolyWorldAutoJoin.
 */
public final class ServerDetector {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private ServerDetector() {}

    public static String getServer() {
        if (mc.getNetworkHandler() == null) return "Vanilla";

        String serverIp = "";
        if (mc.getNetworkHandler().getServerInfo() != null
                && mc.getNetworkHandler().getServerInfo().address != null) {
            serverIp = mc.getNetworkHandler().getServerInfo().address.toLowerCase();
        }

        String brand = mc.getNetworkHandler().getBrand() != null
                ? mc.getNetworkHandler().getBrand().toLowerCase()
                : "";

        if (brand.contains("holyworld") || brand.contains("vk.com/idwok")) return "HolyWorld";
        if (brand.contains("§6spooky§ccore")) return "SpookyTime";
        if (brand.contains("botfilter")) return "FunTime";
        if (serverIp.contains("funtime") || serverIp.contains("skytime") || serverIp.contains("space-times") || serverIp.contains("funsky")) return "CopyTime";
        if (serverIp.contains("reallyworld")) return "ReallyWorld";
        if (serverIp.contains("gulpvp")) return "GulPvP";
        if (serverIp.contains("holyworld") || serverIp.contains("holymc")) return "HolyWorld";

        // Fallback по scoreboard
        if (mc.world != null && mc.world.getScoreboard() != null) {
            ScoreboardObjective objective = mc.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (objective != null) {
                String title = objective.getDisplayName().getString();
                if (title != null) {
                    String low = title.toLowerCase();
                    if (low.contains("holyworld")) return "HolyWorld";
                }
            }
        }
        return "Vanilla";
    }

    public static boolean isHolyWorld() {
        return "HolyWorld".equals(getServer());
    }

    public static boolean isReallyWorld() {
        return "ReallyWorld".equals(getServer());
    }

    public static boolean isFunTime() {
        return "FunTime".equals(getServer());
    }

    public static boolean isSpookyTime() {
        return "SpookyTime".equals(getServer());
    }

    /**
     * Определяет номер анархии для текущего сервера. Возвращает -1 если не на анархии.
     */
    public static int getAnarchy() {
        if (mc.world == null) return -1;
        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) return -1;
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return -1;

        String server = getServer();
        switch (server) {
            case "FunTime", "SpookyTime", "CopyTime" -> {
                String title = objective.getDisplayName().getString();
                if (title != null && title.contains("Анархия-")) {
                    String[] parts = title.split("Анархия-");
                    if (parts.length > 1) {
                        try {
                            return Integer.parseInt(parts[1].trim().split(" ")[0]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                var entries = scoreboard.getScoreboardEntries(objective).stream()
                        .sorted((a, b) -> Integer.compare(b.value(), a.value()))
                        .toList();
                for (ScoreboardEntry entry : entries) {
                    String text = Team.decorateName(scoreboard.getScoreHolderTeam(entry.owner()), entry.name()).getString();
                    if (text.contains("Анархия-")) {
                        String[] parts = text.split("Анархия-");
                        if (parts.length > 1) {
                            try {
                                return Integer.parseInt(parts[1].trim().split(" ")[0]);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
            case "HolyWorld" -> {
                var entries = scoreboard.getScoreboardEntries(objective).stream()
                        .sorted((a, b) -> Integer.compare(b.value(), a.value()))
                        .toList();
                for (ScoreboardEntry e : entries) {
                    String text = Team.decorateName(scoreboard.getScoreHolderTeam(e.owner()), e.name()).getString();
                    if (text == null || text.isEmpty()) continue;
                    if (text.contains("Клан:") || text.contains("Трио:")) continue;
                    if (text.contains("#")) {
                        String afterHash = StringUtils.substringAfter(text, "#");
                        if (afterHash != null && !afterHash.isEmpty()) {
                            StringBuilder num = new StringBuilder();
                            for (char c : afterHash.trim().toCharArray()) {
                                if (Character.isDigit(c)) num.append(c);
                                else break;
                            }
                            if (num.length() > 0) {
                                try {
                                    return Integer.parseInt(num.toString());
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            }
            case "ReallyWorld" -> {
                String title = objective.getDisplayName().getString();
                if (title != null) {
                    String number = StringUtils.substringAfter(title, "#");
                    if (number != null && !number.isEmpty()) {
                        try {
                            return Integer.parseInt(number.trim().split(" ")[0]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return -1;
    }
}

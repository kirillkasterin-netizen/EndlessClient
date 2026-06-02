package dev.endless.util.auction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Сохраняет/загружает настройки AutoBuy в {@code wraith/autobuy.json}.
 */
public final class AutoBuySettingsManager {

    private static final Path FILE = Paths.get("endless/autobuy.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AutoBuySettingsManager() {}

    public static void save() {
        AutoBuyManager mgr = AutoBuyManager.get();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("enabled", mgr.isEnabled());
            root.addProperty("autoParser", mgr.isAutoParserEnabled());
            root.addProperty("globalMax", mgr.getGlobalMaxPrice());
            root.addProperty("buyDelayMin", mgr.getBuyDelayMin());
            root.addProperty("buyDelayMax", mgr.getBuyDelayMax());
            root.addProperty("refreshIntervalMin", mgr.getRefreshIntervalMin());
            root.addProperty("refreshIntervalMax", mgr.getRefreshIntervalMax());
            root.addProperty("parserIntervalMin", mgr.getParserIntervalMin());
            root.addProperty("parserIntervalMax", mgr.getParserIntervalMax());
            root.addProperty("parserMultiplier", mgr.getParserMultiplier());

            JsonObject items = new JsonObject();
            for (AutoBuyableItem it : mgr.getAllItems()) {
                AutoBuyItemSettings s = it.getSettings();
                JsonObject o = new JsonObject();
                o.addProperty("enabled", s.isEnabled());
                o.addProperty("buyBelow", s.getBuyBelow());
                o.addProperty("minCount", s.getMinCount());
                o.addProperty("minDurability", s.getMinDurability());
                items.add(it.getId(), o);
            }
            root.add("items", items);

            Files.createDirectories(FILE.getParent());
            Files.write(FILE, GSON.toJson(root).getBytes());
        } catch (IOException ignored) {}
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;

            AutoBuyManager mgr = AutoBuyManager.get();
            if (root.has("enabled")) mgr.setEnabled(root.get("enabled").getAsBoolean());
            if (root.has("autoParser")) mgr.setAutoParserEnabled(root.get("autoParser").getAsBoolean());
            if (root.has("globalMax")) mgr.setGlobalMaxPrice(root.get("globalMax").getAsInt());

            // legacy: единое значение
            if (root.has("buyDelayMs")) {
                long v = root.get("buyDelayMs").getAsLong();
                mgr.setBuyDelay(v, v);
            }
            if (root.has("buyDelayMin") && root.has("buyDelayMax")) {
                mgr.setBuyDelay(root.get("buyDelayMin").getAsLong(), root.get("buyDelayMax").getAsLong());
            }

            if (root.has("refreshIntervalMin") && root.has("refreshIntervalMax")) {
                mgr.setRefreshInterval(root.get("refreshIntervalMin").getAsLong(),
                        root.get("refreshIntervalMax").getAsLong());
            }

            if (root.has("parserIntervalMs")) {
                long v = root.get("parserIntervalMs").getAsLong();
                mgr.setParserInterval(v, v);
            }
            if (root.has("parserIntervalMin") && root.has("parserIntervalMax")) {
                mgr.setParserInterval(root.get("parserIntervalMin").getAsLong(),
                        root.get("parserIntervalMax").getAsLong());
            }
            if (root.has("parserMultiplier")) {
                mgr.setParserMultiplier(root.get("parserMultiplier").getAsDouble());
            }

            if (root.has("items")) {
                JsonObject items = root.getAsJsonObject("items");
                for (AutoBuyableItem it : mgr.getAllItems()) {
                    if (!items.has(it.getId())) continue;
                    JsonObject o = items.getAsJsonObject(it.getId());
                    AutoBuyItemSettings s = it.getSettings();
                    if (o.has("enabled")) s.setEnabled(o.get("enabled").getAsBoolean());
                    if (o.has("buyBelow")) s.setBuyBelow(o.get("buyBelow").getAsInt());
                    if (o.has("minCount")) s.setMinCount(o.get("minCount").getAsInt());
                    if (o.has("minDurability")) s.setMinDurability(o.get("minDurability").getAsInt());
                }
            }
        } catch (Exception ignored) {}
    }
}

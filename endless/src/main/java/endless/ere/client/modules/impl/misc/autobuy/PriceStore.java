package endless.ere.client.modules.impl.misc.autobuy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import endless.ere.Endless;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Хранит включенные предметы и цены покупки для AutoBuy.
 * Ключи внутри Map — это displayName предметов из {@link endless.ere.base.autobuy.AutoBuyManager}.
 * Сохраняется в {@code Endless/autobuy_prices.json}.
 */
public final class PriceStore {

    private static final PriceStore INSTANCE = new PriceStore();

    public static PriceStore getInstance() { return INSTANCE; }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // server -> (item display name -> entry)
    private final Map<String, Map<String, Entry>> data = new HashMap<>();

    private boolean loaded = false;

    private PriceStore() {}

    public Entry get(String server, String itemName) {
        return data.computeIfAbsent(server, k -> new HashMap<>())
                .computeIfAbsent(itemName, k -> new Entry());
    }

    public List<Map.Entry<String, Entry>> entriesFor(String server) {
        Map<String, Entry> m = data.get(server);
        if (m == null) return Collections.emptyList();
        return new ArrayList<>(m.entrySet());
    }

    public boolean isEnabled(String server, String itemName) {
        Map<String, Entry> m = data.get(server);
        if (m == null) return false;
        Entry e = m.get(itemName);
        return e != null && e.enabled;
    }

    public int getMaxPrice(String server, String itemName) {
        Map<String, Entry> m = data.get(server);
        if (m == null) return 0;
        Entry e = m.get(itemName);
        return e == null ? 0 : e.maxPrice;
    }

    public void setMaxPrice(String server, String itemName, int maxPrice) {
        Entry e = get(server, itemName);
        e.maxPrice = maxPrice;
    }

    // ===== persistence =====

    private File file() {
        File dir = Endless.DIRECTORY;
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "autobuy_prices.json");
    }

    public void load() {
        if (loaded) return;
        loaded = true;

        File f = file();
        if (!f.exists()) return;

        try (FileReader reader = new FileReader(f)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json == null) return;

            for (String server : json.keySet()) {
                if (!json.get(server).isJsonArray()) continue;
                Map<String, Entry> map = data.computeIfAbsent(server, k -> new HashMap<>());
                JsonArray arr = json.getAsJsonArray(server);
                for (var el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    if (!obj.has("name")) continue;
                    Entry e = new Entry();
                    e.enabled  = obj.has("enabled")  && obj.get("enabled").getAsBoolean();
                    e.maxPrice = obj.has("maxPrice") ? obj.get("maxPrice").getAsInt() : 0;
                    map.put(obj.get("name").getAsString(), e);
                }
            }
        } catch (Exception ignored) {}
    }

    public void save() {
        File f = file();
        JsonObject root = new JsonObject();
        for (var srv : data.entrySet()) {
            JsonArray arr = new JsonArray();
            for (var item : srv.getValue().entrySet()) {
                Entry e = item.getValue();
                if (!e.enabled && e.maxPrice == 0) continue;
                JsonObject o = new JsonObject();
                o.addProperty("name", item.getKey());
                o.addProperty("enabled", e.enabled);
                o.addProperty("maxPrice", e.maxPrice);
                arr.add(o);
            }
            root.add(srv.getKey(), arr);
        }

        try (FileWriter w = new FileWriter(f)) {
            gson.toJson(root, w);
        } catch (Exception ignored) {}
    }

    public static final class Entry {
        public boolean enabled;
        public int maxPrice;
    }
}

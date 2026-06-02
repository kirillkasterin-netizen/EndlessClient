package dev.endless.ui.altmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Persistent recently-used nickname store for the Alt Manager.
 *
 * Entries are kept ordered from newest to oldest. Adding an existing
 * nickname promotes it to the top instead of duplicating it. The store is
 * capped to {@link #MAX_ENTRIES} entries to keep the UI manageable.
 *
 * Storage location: {@code wraith/altmanager/history.json} relative to the
 * game working directory.
 */
public final class NicknameHistory {

    /** Hard cap on how many entries are remembered. */
    public static final int MAX_ENTRIES = 24;

    private static final Path STORE_DIR  = Paths.get("endless", "altmanager");
    private static final Path STORE_FILE = STORE_DIR.resolve("history.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<String> entries = new ArrayList<>();

    public NicknameHistory() {
        load();
    }

    /** Returns an immutable snapshot of the current history. */
    public List<String> getEntries() {
        return List.copyOf(entries);
    }

    /**
     * Adds (or promotes) a nickname to the top of the list, deduplicates
     * and persists to disk. Blank nicknames are ignored.
     */
    public void add(String nickname) {
        if (nickname == null) return;
        String trimmed = nickname.trim();
        if (trimmed.isEmpty()) return;

        // Case-insensitive dedup, preserve first-seen casing of new entry.
        entries.removeIf(existing -> existing.equalsIgnoreCase(trimmed));
        entries.add(0, trimmed);

        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        save();
    }

    /**
     * Removes an entry by case-insensitive match.
     */
    public void remove(String nickname) {
        if (nickname == null) return;
        if (entries.removeIf(existing -> existing.equalsIgnoreCase(nickname))) {
            save();
        }
    }

    public void clear() {
        if (entries.isEmpty()) return;
        entries.clear();
        save();
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    private void load() {
        entries.clear();
        if (!Files.exists(STORE_FILE)) return;

        try {
            String json = Files.readString(STORE_FILE, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return;

            JsonArray arr = root.getAsJsonArray();
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (int i = 0; i < arr.size() && unique.size() < MAX_ENTRIES; i++) {
                JsonElement el = arr.get(i);
                if (el == null || el.isJsonNull()) continue;
                String value = el.getAsString();
                if (value != null && !value.isBlank()) unique.add(value.trim());
            }
            entries.addAll(unique);
        } catch (Exception ignored) {
            // Corrupted file — start fresh; we don't want a single bad write
            // to brick the alt manager.
        }
    }

    private void save() {
        try {
            Files.createDirectories(STORE_DIR);
            String json = GSON.toJson(entries);
            Files.writeString(STORE_FILE, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Best-effort persistence; failure is non-fatal.
        }
    }
}

package dev.endless.util.neuro.trainer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton-менеджер NeuroProfile: загружает все json-файлы из
 * {@code <game>/wraith/neuro_profiles/}, отдаёт активный профиль для KillAura.
 */
public final class NeuroProfileManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PROFILES_DIR = FabricLoader.getInstance().getGameDir()
            .resolve("endless").resolve("neuro_profiles");

    private static NeuroProfileManager INSTANCE;

    public static NeuroProfileManager get() {
        if (INSTANCE == null) INSTANCE = new NeuroProfileManager();
        return INSTANCE;
    }

    private final Map<String, NeuroProfile> profiles = new LinkedHashMap<>();
    private NeuroProfile active;

    private NeuroProfileManager() {
        try {
            Files.createDirectories(PROFILES_DIR);
            reload();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reload() {
        profiles.clear();
        File dir = PROFILES_DIR.toFile();
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;
        for (File f : files) {
            try (FileReader r = new FileReader(f)) {
                NeuroProfile p = GSON.fromJson(r, NeuroProfile.class);
                if (p != null) {
                    if (p.name == null || p.name.isEmpty()) {
                        p.name = f.getName().replace(".json", "");
                    }
                    profiles.put(p.name, p);
                }
            } catch (Exception e) {
                System.err.println("[NeuroProfileManager] Не могу прочитать " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    public List<NeuroProfile> getAll() {
        return new ArrayList<>(profiles.values());
    }

    public List<String> getNames() {
        return new ArrayList<>(profiles.keySet());
    }

    public NeuroProfile get(String name) {
        return profiles.get(name);
    }

    public NeuroProfile getActive() {
        return active;
    }

    public void setActive(String name) {
        this.active = profiles.get(name);
    }

    public void setActive(NeuroProfile profile) {
        this.active = profile;
        if (profile != null) profiles.put(profile.name, profile);
    }

    public boolean save(NeuroProfile profile) {
        try {
            Files.createDirectories(PROFILES_DIR);
            File file = new File(PROFILES_DIR.toFile(), profile.name + ".json");
            try (FileWriter w = new FileWriter(file)) {
                GSON.toJson(profile, w);
            }
            profiles.put(profile.name, profile);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean delete(String name) {
        File f = new File(PROFILES_DIR.toFile(), name + ".json");
        boolean ok = !f.exists() || f.delete();
        profiles.remove(name);
        if (active != null && name.equals(active.name)) active = null;
        return ok;
    }

    public Path getProfilesDir() {
        return PROFILES_DIR;
    }
}

package dev.endless.util.rotation;

import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import dev.endless.Endless;
import dev.endless.event.list.EventTick;
import dev.endless.util.IMinecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Менеджер паттернов ротации.
 * <p>
 * Формат v2 хранит метаданные + список кадров с углами, дельтами, FOV до цели,
 * скоростью мыши и маркером удара. Автоматически читает старые файлы (v1/голый список).
 */
public class PatternManager implements IMinecraft {
    public static final int SCHEMA_VERSION = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATTERNS_DIR = FabricLoader.getInstance().getGameDir()
            .resolve("endless").resolve("patterns");
    private static final PatternManager INSTANCE = new PatternManager();

    private final Map<String, RotationPattern> loadedPatterns = new HashMap<>();
    private final Random random = new Random();

    // Воспроизведение
    private RotationPattern activePattern = null;
    private String activePatternName = "None";
    private LivingEntity lastTarget = null;
    private int playbackIndex = 0;
    private boolean mirrored = false;
    private float noisePhase = 0f;

    // Запись
    private boolean recording = false;
    private String recordingName = null;
    private RotationPattern currentRecording = null;
    private LivingEntity recordingTarget = null;
    private long lastRecordedTimeMs = 0L;

    // Настройки воспроизведения
    private float variationStrength = 0.25f;   // 0..1, множитель случайного шума поверх паттерна
    private boolean allowMirror = true;        // разрешить инвертировать yaw-дельты
    private float targetBlend = 0.85f;         // 0..1, насколько жёстко якорим на реальную цель (1=полный ресинк)

    private PatternManager() {
        try {
            Files.createDirectories(PATTERNS_DIR);
            loadAllPatterns();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Endless.getInstance().getEventBus().register(this);
    }

    public static PatternManager getInstance() {
        return INSTANCE;
    }

    // ---------------- Загрузка ----------------

    public void loadAllPatterns() {
        loadedPatterns.clear();
        File dir = PATTERNS_DIR.toFile();
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                RotationPattern pattern = parsePattern(reader);
                if (pattern != null && !pattern.getFrames().isEmpty()) {
                    loadedPatterns.put(file.getName().replace(".json", ""), pattern);
                }
            } catch (Exception e) {
                System.err.println("[PatternManager] Ошибка загрузки " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Парсит паттерн с поддержкой трёх форматов:
     * - v2: {schemaVersion:2, frames:[...]}
     * - v1: {frames:[...]}
     * - legacy: [{frames:[...]}] (список из PatternRecorder или одиночный)
     */
    private RotationPattern parsePattern(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);

        // legacy: массив паттернов — берём первый
        if (root.isJsonArray()) {
            JsonArray arr = root.getAsJsonArray();
            if (arr.isEmpty()) return null;
            return GSON.fromJson(arr.get(0), RotationPattern.class);
        }

        if (!root.isJsonObject()) return null;
        JsonObject obj = root.getAsJsonObject();

        // v2 / v1 — одинаковая структура, v1 просто не имеет schemaVersion
        return GSON.fromJson(obj, RotationPattern.class);
    }

    // ---------------- Запись ----------------

    public void startRecording(String name) {
        this.recordingName = name;
        this.currentRecording = new RotationPattern();
        this.currentRecording.schemaVersion = SCHEMA_VERSION;
        this.recording = true;
        this.recordingTarget = null;
        this.lastRecordedTimeMs = System.currentTimeMillis();
    }

    public void stopRecording() {
        if (!recording || currentRecording == null) return;

        recording = false;
        currentRecording.totalTicks = currentRecording.getFrames().size();
        loadedPatterns.put(recordingName, currentRecording);

        try (FileWriter writer = new FileWriter(new File(PATTERNS_DIR.toFile(), recordingName + ".json"))) {
            GSON.toJson(currentRecording, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        currentRecording = null;
        recordingName = null;
        recordingTarget = null;
    }

    /**
     * Маркирует текущий фрейм как «удар». Вызывается из модулей когда игрок/аура атакуют.
     */
    public void markAttack() {
        if (!recording || currentRecording == null) return;
        List<PatternFrame> frames = currentRecording.getFrames();
        if (!frames.isEmpty()) {
            frames.get(frames.size() - 1).isAttack = true;
        }
    }

    @Subscribe
    public void onTick(EventTick event) {
        if (!recording || mc.player == null || mc.world == null || currentRecording == null) return;

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        List<PatternFrame> frames = currentRecording.getFrames();
        float prevYaw = frames.isEmpty() ? yaw : frames.get(frames.size() - 1).yaw;
        float prevPitch = frames.isEmpty() ? pitch : frames.get(frames.size() - 1).pitch;

        float deltaYaw = MathHelper.wrapDegrees(yaw - prevYaw);
        float deltaPitch = pitch - prevPitch;

        long now = System.currentTimeMillis();
        float dtSeconds = Math.max(0.001f, (now - lastRecordedTimeMs) / 1000.0f);
        float mouseSpeed = (float) Math.hypot(deltaYaw, deltaPitch) / dtSeconds;
        lastRecordedTimeMs = now;

        // Отслеживание цели: фиксируем её на первый раз и держимся за неё пока валидна.
        LivingEntity target = (recordingTarget != null && recordingTarget.isAlive()
                && mc.player.distanceTo(recordingTarget) <= 7.0)
                ? recordingTarget
                : findNearestTarget(6.5);
        recordingTarget = target;

        float recordedDistance = 0f;
        float fovToTarget = 0f;
        float aimPointY = 0.5f;

        if (target != null) {
            Vec3d eye = mc.player.getEyePos();
            Vec3d targetMid = target.getBoundingBox().getCenter();
            recordedDistance = (float) eye.distanceTo(targetMid);

            float[] wanted = dev.endless.util.math.RotationUtil.calculateAngle(targetMid);
            fovToTarget = angularDistance(yaw, pitch, wanted[0], wanted[1]);

            double bodyHeight = Math.max(0.1, target.getBoundingBox().getLengthY());
            double aimY = eye.y + mc.player.getRotationVec(1.0f).y * recordedDistance;
            aimPointY = (float) MathHelper.clamp((aimY - target.getY()) / bodyHeight, 0.0, 1.0);
        }

        PatternFrame frame = new PatternFrame(yaw, pitch, deltaYaw, deltaPitch, recordedDistance);
        frame.fovToTarget = fovToTarget;
        frame.mouseSpeed = mouseSpeed;
        frame.aimPointY = aimPointY;
        frames.add(frame);
    }

    public boolean loadPattern(String name) {
        RotationPattern pattern = loadedPatterns.get(name);
        if (pattern == null) return false;
        activePattern = pattern;
        activePatternName = name;
        resetPlayback();
        return true;
    }

    private void resetPlayback() {
        playbackIndex = 0;
        mirrored = allowMirror && random.nextBoolean();
        noisePhase = random.nextFloat() * (float) (Math.PI * 2);
    }

    // ---------------- Воспроизведение ----------------

    public void updateRotation(LivingEntity target, float lastYaw, float lastPitch) {
        if (activePattern == null || target == null) return;
        List<PatternFrame> frames = activePattern.getFrames();
        if (frames.isEmpty()) return;

        if (target != lastTarget) {
            lastTarget = target;
            resetPlayback();
        }

        if (playbackIndex >= frames.size()) {
            // цикличность: слегка изменяем параметры повторения
            resetPlayback();
        }

        PatternFrame frame = frames.get(playbackIndex);

        // Анкер: реальная цель (eye). Используем самую ближнюю точку хитбокса по вертикали,
        // чтобы не уходить в пустоту из-за animation-y-offset.
        Vec3d anchor = target.getEyePos();
        float[] idealAngles = dev.endless.util.math.RotationUtil.calculateAngle(anchor);
        float targetYaw = idealAngles[0];
        float targetPitch = idealAngles[1];

        // Масштаб дельт по дистанции — чем ближе мы сейчас, тем больше амплитуда.
        float currentDist = (float) mc.player.getEyePos().distanceTo(anchor);
        float scale = 1.0f;
        if (frame.recordedDistance > 0.5f && currentDist > 0.5f) {
            scale = frame.recordedDistance / currentDist;
        }
        scale = MathHelper.clamp(scale, 0.5f, 2.5f);

        float deltaYaw = frame.deltaYaw * scale;
        float deltaPitch = frame.deltaPitch * scale;

        if (mirrored) {
            deltaYaw = -deltaYaw;
        }

        // Мягкий шум: гауссов на yaw, медленная синусоида на pitch (разные характеры).
        if (variationStrength > 0f) {
            float yawNoise = (float) (random.nextGaussian() * 0.35f * variationStrength);
            noisePhase += 0.22f;
            float pitchNoise = (float) Math.sin(noisePhase) * 0.25f * variationStrength;
            deltaYaw += yawNoise;
            deltaPitch += pitchNoise;
        }

        // Ресинк: подмешиваем реальное направление на цель,
        // чтобы паттерн не «плыл» если цель сдвинулась.
        float anchoredYaw = targetYaw + deltaYaw;
        float anchoredPitch = targetPitch + deltaPitch;

        float blendedYaw = MathHelper.wrapDegrees(
                lastYaw + MathHelper.wrapDegrees(anchoredYaw - lastYaw) * targetBlend);
        float blendedPitch = lastPitch + (anchoredPitch - lastPitch) * targetBlend;
        blendedPitch = MathHelper.clamp(blendedPitch, -89f, 89f);

        // GCD-фикс для анти-тестов ротации.
        float gcd = dev.endless.util.render.math.GCDFixer.getGCDValue();
        if (gcd > 0.0001f) {
            blendedYaw -= (blendedYaw - lastYaw) % gcd;
            blendedPitch -= (blendedPitch - lastPitch) % gcd;
        }

        RotationComponent.update(new Rotation(blendedYaw, blendedPitch),
                360, 360, 360, 360, 0, 1, false);

        playbackIndex++;
    }

    // ---------------- Утилиты ----------------

    private LivingEntity findNearestTarget(double maxDistance) {
        if (mc.world == null || mc.player == null) return null;

        LivingEntity nearest = null;
        double bestDist = maxDistance;
        Vec3d eye = mc.player.getEyePos();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le == mc.player || !le.isAlive() || le instanceof ArmorStandEntity) continue;

            double d = eye.distanceTo(le.getBoundingBox().getCenter());
            if (d < bestDist) {
                bestDist = d;
                nearest = le;
            }
        }
        return nearest;
    }

    private static float angularDistance(float yaw, float pitch, float targetYaw, float targetPitch) {
        float dy = MathHelper.wrapDegrees(targetYaw - yaw);
        float dp = targetPitch - pitch;
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    // ---------------- Геттеры / сеттеры ----------------

    public boolean isRecording() {
        return recording;
    }

    public String getActivePatternName() {
        return activePatternName;
    }

    public Map<String, RotationPattern> getLoadedPatterns() {
        return loadedPatterns;
    }

    public Path getPatternsDir() {
        return PATTERNS_DIR;
    }

    public void setVariationStrength(float v) {
        this.variationStrength = MathHelper.clamp(v, 0f, 1.5f);
    }

    public void setAllowMirror(boolean allow) {
        this.allowMirror = allow;
    }

    public void setTargetBlend(float blend) {
        this.targetBlend = MathHelper.clamp(blend, 0.1f, 1f);
    }

    // ---------------- DTO ----------------

    public static class RotationPattern {
        public int schemaVersion = 1;
        public int totalTicks = 0;
        private final List<PatternFrame> frames = new ArrayList<>();

        public void addFrame(PatternFrame frame) {
            frames.add(frame);
        }

        public List<PatternFrame> getFrames() {
            return frames;
        }
    }

    public static class PatternFrame {
        public float yaw;
        public float pitch;
        public float deltaYaw;
        public float deltaPitch;
        public float recordedDistance;

        // v2 поля: при чтении v1 остаются 0 — воспроизведение работает корректно.
        public float fovToTarget;
        public float mouseSpeed;
        public float aimPointY;
        public boolean isAttack;

        public PatternFrame(float yaw, float pitch, float deltaYaw, float deltaPitch, float recordedDistance) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.deltaYaw = deltaYaw;
            this.deltaPitch = deltaPitch;
            this.recordedDistance = recordedDistance;
        }
    }
}

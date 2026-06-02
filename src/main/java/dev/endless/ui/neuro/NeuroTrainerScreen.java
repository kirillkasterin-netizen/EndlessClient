package dev.endless.ui.neuro;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import dev.endless.util.IMinecraft;
import dev.endless.util.cursor.CursorManager;
import dev.endless.util.neuro.trainer.NeuroProfile;
import dev.endless.util.neuro.trainer.NeuroProfileManager;
import dev.endless.util.render.helper.HoverUtil;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Aim-тренажёр — в квадрате появляются кружки, игрок наводится мышью и кликает.
 * Параллельно собираются метрики: скорость движения, реакция, точность,
 * траектория. По завершении — сохраняем как NeuroProfile.
 */
public class NeuroTrainerScreen extends Screen implements IMinecraft {

    private static final int TRAIN_SECONDS_DEFAULT = 30;

    // ─── Размеры арены ───
    private static final int ARENA_W = 540;
    private static final int ARENA_H = 360;
    private static final float TARGET_RADIUS = 14f;

    // ─── Состояние ───
    private enum Phase { READY, TRAINING, FINISHED, NAMING }
    private Phase phase = Phase.READY;

    private int trainSeconds = TRAIN_SECONDS_DEFAULT;
    private long startMs = 0;
    private long phaseStartMs = 0;

    private final Random random = new Random();
    private float targetX, targetY;
    private long targetSpawnMs = 0;

    // Стартовая позиция мыши при спавне цели — для расчёта прогресса
    private float pathInitX, pathInitY;
    private float pathInitDistance;

    // Метрики
    private int targetsHit = 0;
    private int targetsMissed = 0;
    private final List<Float> reactionTimes = new ArrayList<>(); // мс от спавна до первого движения мыши
    private final List<Float> aimTimes = new ArrayList<>();      // мс от спавна до клика
    private final List<Float> clickErrors = new ArrayList<>();   // расстояние от клика до центра, px
    private final List<Float> speeds = new ArrayList<>();        // средняя скорость на пути к цели, px/sec
    private final List<Float> hSpeeds = new ArrayList<>();       // мгновенная горизонтальная скорость, px/sec
    private final List<Float> vSpeeds = new ArrayList<>();       // мгновенная вертикальная скорость, px/sec
    private float maxHSpeed = 0;
    private float maxVSpeed = 0;

    /** Сырые сэмплы движений для kNN. */
    private final java.util.List<dev.endless.util.neuro.trainer.NeuroProfile.MovementSample> samples = new ArrayList<>();

    /** Полные траектории (от спавна до клика). */
    private final java.util.List<dev.endless.util.neuro.trainer.NeuroProfile.Trajectory> trajectories = new ArrayList<>();
    /** Текущая собираемая траектория. */
    private dev.endless.util.neuro.trainer.NeuroProfile.Trajectory currentTrajectory;

    /** Тайминги между кликами (ISI). */
    private final java.util.List<Integer> clickIntervals = new ArrayList<>();
    private long lastClickMs = 0;

    /** Стартовые углы (в градусах) при спавне цели — для траектории. */
    private float startYawDeg, startPitchDeg, targetYawDeg, targetPitchDeg;
    private long lastFrameMs = 0;

    // Track of mouse path to current target
    private final List<float[]> currentPath = new ArrayList<>(); // [dt_ms, x, y]
    private float pathStartX, pathStartY;
    private long pathStartMs;
    private boolean firstMoveSeen = false;

    private double lastMouseX = 0, lastMouseY = 0;

    // Поле имени профиля
    private String profileName = "";
    private boolean nameFocused = true;

    public NeuroTrainerScreen() {
        super(Text.literal("Neuro Trainer"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CursorManager.reset();
        CursorManager.resetIBeam();
        CursorManager.resetClick();

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // Фон
        DrawUtil.drawRound(0, 0, sw, sh, 0, ColorProvider.rgba(15, 15, 20, 220));

        // Заголовок
        DrawUtil.drawText(Fonts.SFBOLD.get(), "Neuro Trainer",
                sw / 2f - 30, 18, ColorProvider.getColorClient(), 11);

        switch (phase) {
            case READY -> renderReady(context, mouseX, mouseY, sw, sh);
            case TRAINING -> renderTraining(context, mouseX, mouseY, sw, sh);
            case FINISHED -> renderFinished(context, mouseX, mouseY, sw, sh);
            case NAMING -> renderNaming(context, mouseX, mouseY, sw, sh);
        }

        long w = mc.getWindow().getHandle();
        if (CursorManager.shouldBeHand()) GLFW.glfwSetCursor(w, GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR));
        else if (CursorManager.shouldIBeam()) GLFW.glfwSetCursor(w, GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR));
        else GLFW.glfwSetCursor(w, GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR));
    }

    // ─────────────────── PHASES ───────────────────

    private float[] arenaBounds(int sw, int sh) {
        float ax = (sw - ARENA_W) / 2f;
        float ay = (sh - ARENA_H) / 2f + 10;
        return new float[]{ax, ay, ARENA_W, ARENA_H};
    }

    private void renderReady(DrawContext ctx, int mouseX, int mouseY, int sw, int sh) {
        float[] arena = arenaBounds(sw, sh);
        DrawUtil.drawRound(arena[0], arena[1], arena[2], arena[3], 8,
                ColorProvider.rgba(25, 25, 30, 200));

        // Инструкция
        DrawUtil.drawText(Fonts.SFBOLD.get(), "Aim Trainer",
                arena[0] + arena[2] / 2f - 30, arena[1] + 60,
                ColorProvider.rgba(255, 255, 255, 255), 14);
        DrawUtil.drawText(Fonts.SFREGULAR.get(),
                "Кликай по кружкам в течение " + trainSeconds + " секунд.",
                arena[0] + arena[2] / 2f - 90, arena[1] + 90,
                ColorProvider.rgba(200, 200, 210, 255), 8);
        DrawUtil.drawText(Fonts.SFREGULAR.get(),
                "Нейросеть запомнит твой стиль наводки.",
                arena[0] + arena[2] / 2f - 90, arena[1] + 105,
                ColorProvider.rgba(180, 180, 190, 255), 8);

        // Старт-кнопка
        float bx = arena[0] + arena[2] / 2f - 60;
        float by = arena[1] + arena[3] / 2f - 15;
        boolean hov = HoverUtil.isHovered(mouseX, mouseY, bx, by, 120, 30);
        if (hov) CursorManager.requestHand();
        DrawUtil.drawRound(bx, by, 120, 30, 6,
                hov ? ColorProvider.setAlpha(ColorProvider.getColorClient(), 220)
                        : ColorProvider.setAlpha(ColorProvider.getColorClient(), 180));
        DrawUtil.drawText(Fonts.SFBOLD.get(), "Начать обучение",
                bx + 18, by + 11, ColorProvider.rgba(255, 255, 255, 255), 9);

        // Esc подсказка
        DrawUtil.drawText(Fonts.SFREGULAR.get(), "ESC — выход",
                arena[0] + 8, arena[1] + arena[3] - 16,
                ColorProvider.rgba(140, 140, 150, 200), 6.5f);
    }

    private void renderTraining(DrawContext ctx, int mouseX, int mouseY, int sw, int sh) {
        float[] arena = arenaBounds(sw, sh);

        // Фон арены
        DrawUtil.drawRound(arena[0], arena[1], arena[2], arena[3], 8,
                ColorProvider.rgba(20, 20, 25, 220));
        DrawUtil.drawRound(arena[0], arena[1], arena[2], 1, 0,
                ColorProvider.setAlpha(ColorProvider.getColorClient(), 180));

        // Прогресс времени
        long now = System.currentTimeMillis();
        long elapsed = now - startMs;
        float progress = Math.min(1f, elapsed / (trainSeconds * 1000f));
        DrawUtil.drawRound(arena[0], arena[1] + arena[3] - 3, arena[2] * progress, 3, 0,
                ColorProvider.getColorClient());

        // Таймер
        int remainMs = (int) Math.max(0, trainSeconds * 1000L - elapsed);
        String timeStr = String.format("%.1f сек", remainMs / 1000f);
        DrawUtil.drawText(Fonts.SFBOLD.get(), timeStr,
                arena[0] + 8, arena[1] + 6, ColorProvider.rgba(255, 255, 255, 255), 9);

        // Статистика
        String stats = "Попаданий: " + targetsHit + " | Промахов: " + targetsMissed;
        DrawUtil.drawText(Fonts.SFREGULAR.get(), stats,
                arena[0] + arena[2] - 130, arena[1] + 6,
                ColorProvider.rgba(200, 200, 210, 255), 7);

        // Цель
        if (targetSpawnMs == 0) spawnTarget(arena);
        renderTarget(targetX, targetY);

        // Track mouse path
        if (firstMoveSeen || (mouseX != lastMouseX || mouseY != lastMouseY)) {
            if (!firstMoveSeen && (Math.abs(mouseX - pathStartX) + Math.abs(mouseY - pathStartY) > 2)) {
                firstMoveSeen = true;
                reactionTimes.add((float) (now - targetSpawnMs));
            }
            // Мгновенные скорости (горизонтальная/вертикальная) px/sec
            if (!currentPath.isEmpty()) {
                float[] last = currentPath.get(currentPath.size() - 1);
                float dt = ((now - pathStartMs) - last[0]) / 1000f;
                if (dt > 0.001f) {
                    float dx = (float) (mouseX - last[1]);
                    float dy = (float) (mouseY - last[2]);
                    float vh = Math.abs(dx) / dt;
                    float vv = Math.abs(dy) / dt;
                    if (vh > 0.5f) {
                        hSpeeds.add(vh);
                        if (vh > maxHSpeed) maxHSpeed = vh;
                    }
                    if (vv > 0.5f) {
                        vSpeeds.add(vv);
                        if (vv > maxVSpeed) maxVSpeed = vv;
                    }

                    // ─── Сэмпл для kNN ───
                    // Фичи: оставшееся расстояние до цели по yaw/pitch + прогресс пути.
                    float distYaw = (float) (targetX - mouseX);
                    float distPitch = (float) (targetY - mouseY);
                    float currentDist = (float) Math.hypot(targetX - mouseX, targetY - mouseY);
                    float pathProgress = pathInitDistance > 0.01f
                            ? Math.max(0f, Math.min(1f, 1f - currentDist / pathInitDistance))
                            : 1f;
                    // Записываем только если был реальный сдвиг и оставшееся расстояние > 1px
                    if ((Math.abs(dx) > 0.1f || Math.abs(dy) > 0.1f) && currentDist > 1f) {
                        samples.add(new dev.endless.util.neuro.trainer.NeuroProfile.MovementSample(
                                distYaw, distPitch, pathProgress, dx, dy));
                    }

                    // ─── Фрейм траектории (в градусах) ───
                    if (currentTrajectory != null && (Math.abs(dx) > 0.1f || Math.abs(dy) > 0.1f)) {
                        float pxToDeg = 0.13f;
                        int frameDt = (int) Math.max(1, now - lastFrameMs);
                        currentTrajectory.frames.add(
                                new dev.endless.util.neuro.trainer.NeuroProfile.TrajectoryFrame(
                                        frameDt, dx * pxToDeg, dy * pxToDeg));
                        lastFrameMs = now;
                    }
                }
            }
            currentPath.add(new float[]{(now - pathStartMs), mouseX, mouseY});
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        // Конец обучения
        if (elapsed >= trainSeconds * 1000L) {
            phase = Phase.FINISHED;
            phaseStartMs = now;
        }
    }

    private void renderTarget(float cx, float cy) {
        // Внешний круг
        DrawUtil.drawRound(cx - TARGET_RADIUS - 2, cy - TARGET_RADIUS - 2,
                (TARGET_RADIUS + 2) * 2, (TARGET_RADIUS + 2) * 2,
                TARGET_RADIUS + 2, ColorProvider.setAlpha(ColorProvider.getColorClient(), 120));
        // Внутренний
        DrawUtil.drawRound(cx - TARGET_RADIUS, cy - TARGET_RADIUS,
                TARGET_RADIUS * 2, TARGET_RADIUS * 2,
                TARGET_RADIUS, ColorProvider.setAlpha(ColorProvider.getColorClient(), 220));
        // Центр
        DrawUtil.drawRound(cx - 3, cy - 3, 6, 6, 3,
                ColorProvider.rgba(255, 255, 255, 255));
    }

    private void spawnTarget(float[] arena) {
        float pad = TARGET_RADIUS + 6;
        targetX = arena[0] + pad + random.nextFloat() * (arena[2] - 2 * pad);
        targetY = arena[1] + pad + random.nextFloat() * (arena[3] - 2 * pad);
        targetSpawnMs = System.currentTimeMillis();
        currentPath.clear();
        pathStartMs = targetSpawnMs;
        // Текущая мышь
        double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
        double my = mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
        pathStartX = (float) mx;
        pathStartY = (float) my;
        pathInitX = (float) mx;
        pathInitY = (float) my;
        pathInitDistance = (float) Math.hypot(targetX - mx, targetY - my);
        firstMoveSeen = false;

        // ─── Старт новой траектории ───
        float pxToDeg = 0.13f;
        startYawDeg = (float) (mx * pxToDeg);
        startPitchDeg = (float) (my * pxToDeg);
        targetYawDeg = targetX * pxToDeg;
        targetPitchDeg = targetY * pxToDeg;

        currentTrajectory = new dev.endless.util.neuro.trainer.NeuroProfile.Trajectory();
        currentTrajectory.startDistance = pathInitDistance * pxToDeg;
        currentTrajectory.dirYaw = (float) ((targetX - mx) * pxToDeg);
        currentTrajectory.dirPitch = (float) ((targetY - my) * pxToDeg);
        currentTrajectory.reactionMs = 0;
        currentTrajectory.totalMs = 0;
        lastFrameMs = targetSpawnMs;
    }

    private void renderFinished(DrawContext ctx, int mouseX, int mouseY, int sw, int sh) {
        float[] arena = arenaBounds(sw, sh);
        DrawUtil.drawRound(arena[0], arena[1], arena[2], arena[3], 8,
                ColorProvider.rgba(25, 25, 30, 220));

        DrawUtil.drawText(Fonts.SFBOLD.get(), "Обучение завершено",
                arena[0] + arena[2] / 2f - 60, arena[1] + 50,
                ColorProvider.rgba(255, 255, 255, 255), 13);

        // Статистика
        float baseY = arena[1] + 95;
        drawStat(arena[0] + 80, baseY, "Попаданий", String.valueOf(targetsHit));
        drawStat(arena[0] + 80, baseY + 18, "Промахов", String.valueOf(targetsMissed));
        drawStat(arena[0] + 80, baseY + 36, "Реакция (ср)",
                avg(reactionTimes) + " мс");
        drawStat(arena[0] + 80, baseY + 54, "Время наводки (ср)",
                avg(aimTimes) + " мс");
        drawStat(arena[0] + 80, baseY + 72, "Точность (ср)",
                String.format("%.1f px", avgFloat(clickErrors)));
        drawStat(arena[0] + 80, baseY + 90, "Гориз. скорость",
                String.format("%.0f px/с (пик %.0f)", avgFloat(hSpeeds), maxHSpeed));
        drawStat(arena[0] + 80, baseY + 108, "Верт. скорость",
                String.format("%.0f px/с (пик %.0f)", avgFloat(vSpeeds), maxVSpeed));

        // Кнопка "Сохранить"
        float bx = arena[0] + arena[2] / 2f - 60;
        float by = arena[1] + arena[3] - 50;
        boolean hov = HoverUtil.isHovered(mouseX, mouseY, bx, by, 120, 28);
        if (hov) CursorManager.requestHand();
        DrawUtil.drawRound(bx, by, 120, 28, 5,
                hov ? ColorProvider.setAlpha(ColorProvider.getColorClient(), 220)
                        : ColorProvider.setAlpha(ColorProvider.getColorClient(), 180));
        DrawUtil.drawText(Fonts.SFBOLD.get(), "Сохранить профиль",
                bx + 14, by + 10, ColorProvider.rgba(255, 255, 255, 255), 9);
    }

    private void renderNaming(DrawContext ctx, int mouseX, int mouseY, int sw, int sh) {
        float[] arena = arenaBounds(sw, sh);
        DrawUtil.drawRound(arena[0], arena[1], arena[2], arena[3], 8,
                ColorProvider.rgba(25, 25, 30, 220));

        DrawUtil.drawText(Fonts.SFBOLD.get(), "Введи имя профиля",
                arena[0] + arena[2] / 2f - 55, arena[1] + 80,
                ColorProvider.rgba(255, 255, 255, 255), 11);

        // Поле ввода
        float fx = arena[0] + arena[2] / 2f - 100;
        float fy = arena[1] + 130;
        float fw = 200;
        float fh = 26;
        DrawUtil.drawRound(fx, fy, fw, fh, 5, ColorProvider.rgba(35, 35, 45, 220));
        DrawUtil.drawRound(fx, fy, fw, 0.7f, 0,
                ColorProvider.setAlpha(ColorProvider.getColorClient(), 220));

        String shown = profileName + (nameFocused && System.currentTimeMillis() % 1000 > 500 ? "|" : "");
        DrawUtil.drawText(Fonts.SFREGULAR.get(),
                profileName.isEmpty() ? "Имя профиля..." : shown,
                fx + 8, fy + 9,
                profileName.isEmpty()
                        ? ColorProvider.rgba(120, 120, 130, 200)
                        : ColorProvider.rgba(220, 220, 230, 255), 9);

        // Кнопка
        float bx = arena[0] + arena[2] / 2f - 50;
        float by = arena[1] + 175;
        boolean canSave = !profileName.trim().isEmpty();
        boolean hov = HoverUtil.isHovered(mouseX, mouseY, bx, by, 100, 26);
        if (hov && canSave) CursorManager.requestHand();
        DrawUtil.drawRound(bx, by, 100, 26, 5,
                canSave
                        ? (hov ? ColorProvider.setAlpha(ColorProvider.getColorClient(), 230)
                              : ColorProvider.setAlpha(ColorProvider.getColorClient(), 180))
                        : ColorProvider.rgba(45, 45, 50, 180));
        DrawUtil.drawText(Fonts.SFBOLD.get(), "Сохранить",
                bx + 22, by + 9, ColorProvider.rgba(255, 255, 255, 255), 9);
    }

    private void drawStat(float x, float y, String label, String value) {
        DrawUtil.drawText(Fonts.SFREGULAR.get(), label,
                x, y, ColorProvider.rgba(160, 160, 170, 255), 7.5f);
        DrawUtil.drawText(Fonts.SFBOLD.get(), value,
                x + 200, y, ColorProvider.rgba(255, 255, 255, 255), 7.5f);
    }

    private static int avg(List<Float> list) {
        if (list.isEmpty()) return 0;
        float s = 0;
        for (Float f : list) s += f;
        return (int) (s / list.size());
    }

    private static float avgFloat(List<Float> list) {
        if (list.isEmpty()) return 0;
        float s = 0;
        for (Float f : list) s += f;
        return s / list.size();
    }

    // ─────────────────── INPUT ───────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float[] arena = arenaBounds(sw, sh);

        if (phase == Phase.READY) {
            float bx = arena[0] + arena[2] / 2f - 60;
            float by = arena[1] + arena[3] / 2f - 15;
            if (HoverUtil.isHovered(mouseX, mouseY, bx, by, 120, 30)) {
                phase = Phase.TRAINING;
                startMs = System.currentTimeMillis();
                phaseStartMs = startMs;
                targetSpawnMs = 0;
                return true;
            }
        } else if (phase == Phase.TRAINING) {
            // Клик по цели?
            float dx = (float) mouseX - targetX;
            float dy = (float) mouseY - targetY;
            float dist = (float) Math.hypot(dx, dy);
            long now = System.currentTimeMillis();
            if (dist <= TARGET_RADIUS + 4) {
                targetsHit++;
                aimTimes.add((float) (now - targetSpawnMs));
                clickErrors.add(dist);
                // Скорость = пройденная дистанция / время
                if (!currentPath.isEmpty()) {
                    float total = 0;
                    for (int i = 1; i < currentPath.size(); i++) {
                        float[] p = currentPath.get(i - 1);
                        float[] c = currentPath.get(i);
                        total += (float) Math.hypot(c[1] - p[1], c[2] - p[2]);
                    }
                    float duration = (now - targetSpawnMs) / 1000f;
                    if (duration > 0.05f) speeds.add(total / duration);
                }
                // ─── Закрываем траекторию ───
                if (currentTrajectory != null) {
                    currentTrajectory.totalMs = (int) (now - targetSpawnMs);
                    if (!reactionTimes.isEmpty()) {
                        currentTrajectory.reactionMs = reactionTimes.get(reactionTimes.size() - 1).intValue();
                    }
                    if (!currentTrajectory.frames.isEmpty()) {
                        trajectories.add(currentTrajectory);
                    }
                    currentTrajectory = null;
                }
                // ─── ISI ───
                if (lastClickMs > 0) {
                    clickIntervals.add((int) (now - lastClickMs));
                }
                lastClickMs = now;
                spawnTarget(arena);
            } else {
                targetsMissed++;
            }
            return true;
        } else if (phase == Phase.FINISHED) {
            // Клик по «Сохранить»
            float bx = arena[0] + arena[2] / 2f - 60;
            float by = arena[1] + arena[3] - 50;
            if (HoverUtil.isHovered(mouseX, mouseY, bx, by, 120, 28)) {
                phase = Phase.NAMING;
                profileName = "Aim_" + System.currentTimeMillis() % 100000;
                nameFocused = true;
                return true;
            }
        } else if (phase == Phase.NAMING) {
            float fx = arena[0] + arena[2] / 2f - 100;
            float fy = arena[1] + 130;
            nameFocused = HoverUtil.isHovered(mouseX, mouseY, fx, fy, 200, 26);

            float bx = arena[0] + arena[2] / 2f - 50;
            float by = arena[1] + 175;
            if (HoverUtil.isHovered(mouseX, mouseY, bx, by, 100, 26)
                    && !profileName.trim().isEmpty()) {
                saveProfile();
                this.close();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        if (phase == Phase.NAMING && nameFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !profileName.isEmpty()) {
                profileName = profileName.substring(0, profileName.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER && !profileName.trim().isEmpty()) {
                saveProfile();
                this.close();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (phase == Phase.NAMING && nameFocused) {
            if ((chr >= 'a' && chr <= 'z') || (chr >= 'A' && chr <= 'Z')
                    || (chr >= '0' && chr <= '9') || chr == '_' || chr == '-') {
                if (profileName.length() < 24) profileName += chr;
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    private void saveProfile() {
        NeuroProfile profile = new NeuroProfile();
        profile.name = profileName.trim();
        profile.trainSeconds = trainSeconds;
        profile.targetsHit = targetsHit;
        profile.avgClickAccuracy = avgFloat(clickErrors);

        if (!reactionTimes.isEmpty()) profile.reactionTimeMs = avg(reactionTimes);
        if (!aimTimes.isEmpty()) profile.aimTimeMs = avg(aimTimes);

        if (!speeds.isEmpty()) {
            // Конвертируем px/sec в условные град/sec (примерно 1px ≈ 0.13°)
            float pxToDeg = 0.13f;
            profile.avgSpeed = avgFloat(speeds) * pxToDeg;
            // std dev
            float mean = avgFloat(speeds);
            float varSum = 0;
            for (Float s : speeds) varSum += (s - mean) * (s - mean);
            profile.speedStdDev = (float) Math.sqrt(varSum / speeds.size()) * pxToDeg;
        }

        // Раздельно горизонталь и вертикаль
        float pxToDeg = 0.13f;
        if (!hSpeeds.isEmpty()) {
            profile.horizontalSpeed = avgFloat(hSpeeds) * pxToDeg;
            float mean = avgFloat(hSpeeds);
            float v = 0;
            for (Float s : hSpeeds) v += (s - mean) * (s - mean);
            profile.horizontalSpeedStdDev = (float) Math.sqrt(v / hSpeeds.size()) * pxToDeg;
            profile.horizontalPeakSpeed = maxHSpeed * pxToDeg;
        }
        if (!vSpeeds.isEmpty()) {
            profile.verticalSpeed = avgFloat(vSpeeds) * pxToDeg;
            float mean = avgFloat(vSpeeds);
            float v = 0;
            for (Float s : vSpeeds) v += (s - mean) * (s - mean);
            profile.verticalSpeedStdDev = (float) Math.sqrt(v / vSpeeds.size()) * pxToDeg;
            profile.verticalPeakSpeed = maxVSpeed * pxToDeg;
        }

        // Эвристики на основе reaction/aim/error
        profile.startAccel = MathHelper.clamp(profile.avgSpeed * 6f, 800f, 3000f);
        profile.endDecel = MathHelper.clamp(profile.avgSpeed * 8f, 1000f, 4000f);
        profile.overshootFactor = MathHelper.clamp(profile.avgClickAccuracy / 30f, 0.05f, 0.4f);
        profile.jitter = MathHelper.clamp(profile.avgClickAccuracy / 50f, 0.1f, 1.0f);
        profile.curvature = 0.4f;

        // Сырые сэмплы для kNN — сохраняем все
        profile.samples = new java.util.ArrayList<>(samples);
        // Полные траектории и ISI
        profile.trajectories = new java.util.ArrayList<>(trajectories);
        profile.clickIntervals = new java.util.ArrayList<>(clickIntervals);

        boolean ok = NeuroProfileManager.get().save(profile);
        if (ok) {
            NeuroProfileManager.get().setActive(profile);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}

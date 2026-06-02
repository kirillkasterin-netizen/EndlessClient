package dev.endless.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import dev.endless.Endless;
import dev.endless.event.list.FireworkEvent;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.list.combat.KillAura;
import dev.endless.module.settings.ModeSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.player.combat.PredictUtils;

@ModuleInformation(moduleName = "Super Firework", moduleCategory = ModuleCategory.MOVEMENT)
public class ElytraBooster extends Module {

    private final ModeSetting mode = new ModeSetting("Mode", "Rage", "Rage", "Custom", "CustomPitch");
    private final SliderSetting customSpeed = new SliderSetting("Custom Speed", 1.61f, 0.1f, 2.5f, 0.01f)
            .setVisible(() -> mode.is("Custom"));
    private final SliderSetting[] customPitchBins = new SliderSetting[18];

    private static final float BASE_HORIZONTAL = 1.61f;
    private static final float BASE_VERTICAL = 1.50f;

    private static final float[] YAW_TABLE = {
            1.61f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f, 1.62f, 1.62f, 1.62f, 1.63f,
            1.63f, 1.64f, 1.65f, 1.65f, 1.66f, 1.67f, 1.68f, 1.69f, 1.70f, 1.71f,
            1.72f, 1.73f, 1.73f, 1.75f, 1.76f, 1.78f, 1.79f, 1.81f, 1.83f, 1.85f,
            1.87f, 1.89f, 1.91f, 1.93f, 1.95f, 1.98f, 2.01f, 2.03f, 2.06f, 2.09f,
            2.12f, 2.16f, 2.19f, 2.23f, 2.27f, 2.31f, 2.35f, 2.31f, 2.27f, 2.23f,
            2.19f, 2.16f, 2.12f, 2.09f, 2.06f, 2.03f, 2.01f, 1.98f, 1.95f, 1.93f,
            1.89f, 1.87f, 1.85f, 1.83f, 1.81f, 1.79f, 1.78f, 1.76f, 1.75f, 1.73f,
            1.72f, 1.71f, 1.70f, 1.69f, 1.68f, 1.67f, 1.66f, 1.65f, 1.64f, 1.63f,
            1.63f, 1.63f, 1.62f, 1.62f, 1.62f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f,
            1.61f
    };

    private static final float[] PITCH_TABLE = {
            1.61f, 1.61f, 1.61f, 1.62f, 1.62f, 1.62f, 1.63f, 1.63f, 1.64f, 1.65f,
            1.65f, 1.66f, 1.67f, 1.68f, 1.69f, 1.70f, 1.71f, 1.72f, 1.73f, 1.73f,
            1.75f, 1.76f, 1.78f, 1.79f, 1.81f, 1.83f, 1.85f, 1.87f, 1.89f, 1.91f,
            1.93f, 1.95f, 1.98f, 2.01f, 2.03f, 2.06f, 2.09f, 2.12f, 2.16f, 2.19f,
            2.23f, 2.24f, 2.21f, 2.21f, 2.21f, 2.23f, 2.23f, 2.19f, 2.16f, 2.12f,
            2.09f, 2.06f, 2.03f, 2.01f, 1.98f, 1.95f, 1.93f, 1.89f, 1.87f, 1.85f,
            1.83f, 1.81f, 1.79f, 1.78f, 1.76f, 1.75f, 1.73f, 1.72f, 1.71f, 1.70f,
            1.69f, 1.68f, 1.67f, 1.66f, 1.65f, 1.64f, 1.63f, 1.63f, 1.63f, 1.62f,
            1.62f, 1.62f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f, 1.61f,
            1.61f
    };

    public ElytraBooster() {
        for (int i = 0; i < 18; i++) {
            int minPitch = i * 10 - 90;
            int maxPitch = minPitch + 10;
            customPitchBins[i] = new SliderSetting("Pitch " + minPitch + "°→" + maxPitch + "°", 1.61f, 0f, 3f, 0.01f)
                    .setVisible(() -> mode.is("CustomPitch"));
        }
    }

    private float activePitch() {
        return MathHelper.wrapDegrees(mc.player.getPitch());
    }

    private float activeYaw() {
        return MathHelper.wrapDegrees(mc.player.getYaw());
    }

    private static float foldYaw(float yawAbs) {
        float folded180 = yawAbs > 180f ? 360f - yawAbs : yawAbs;
        return folded180 > 90f ? 180f - folded180 : folded180;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }

    private static int pitchBin(float pitch) {
        int idx = (int) ((pitch + 90f) / 10f);
        return Math.max(0, Math.min(17, idx));
    }

    private float getRageSpeed() {
        float yawAbs = Math.abs(MathHelper.wrapDegrees(activeYaw()));
        float yawFolded = foldYaw(yawAbs);
        float pitchAbs = Math.abs(clampPitch(activePitch()));

        if (pitchAbs >= 70f && pitchAbs <= 90f) {
            return 1.615f;
        }

        float yawSpeed = YAW_TABLE[Math.min((int) Math.ceil(yawFolded), 90)];
        int pitchIndex = Math.min((int) Math.ceil(pitchAbs), PITCH_TABLE.length - 1);
        float pitchSpeed = PITCH_TABLE[pitchIndex];
        float speed = pitchAbs >= 75f ? pitchSpeed : Math.max(yawSpeed, pitchSpeed);
        return Math.max(speed, pitchAbs >= 75f ? BASE_VERTICAL : BASE_HORIZONTAL);
    }

    private float getCustomPitchSpeed() {
        float pitch = clampPitch(activePitch());
        float pitchAbs = Math.abs(pitch);
        float binValue = Math.max(0f, customPitchBins[pitchBin(pitch)].getFloatValue());
        float speed = Math.max(binValue, pitchAbs >= 75f ? BASE_VERTICAL : BASE_HORIZONTAL);
        float yawBased = computeLegitYawSpeed(activeYaw(), pitch);
        return Math.max(speed, yawBased);
    }

    private float computeLegitYawSpeed(float yaw, float pitch) {
        float result = 1.615f;
        if (isYawInRange(yaw, 45, 13) || isYawInRange(yaw, 135, 13) || isYawInRange(yaw, 225, 13) || isYawInRange(yaw, 315, 13)) {
            result = (pitch >= 1 && pitch <= 90) ? 1.86f : 1.965f;
        }
        if (isYawInRange(yaw, 45, 14) && !isYawInRange(yaw, 45, 13)) result = 1.91f;
        if (isYawInRange(yaw, 45, 18) && !isYawInRange(yaw, 45, 14)) result = 1.85f;
        if (isYawInRange(yaw, 45, 19) && !isYawInRange(yaw, 45, 18)) result = 1.83f;
        if (isYawInRange(yaw, 45, 20) && !isYawInRange(yaw, 45, 19)) result = 1.8f;
        if (isYawInRange(yaw, 45, 22) && !isYawInRange(yaw, 45, 20)) result = 1.78f;
        if (isYawInRange(yaw, 45, 23) && !isYawInRange(yaw, 45, 22)) result = 1.76f;
        if (isYawInRange(yaw, 45, 25) && !isYawInRange(yaw, 45, 24)) result = 1.73f;
        if (isYawInRange(yaw, 45, 26) && !isYawInRange(yaw, 45, 25)) result = 1.72f;
        if (isYawInRange(yaw, 45, 29) && !isYawInRange(yaw, 45, 26)) result = 1.7f;

        if (isYawInRange(yaw, 135, 14) && !isYawInRange(yaw, 135, 13)) result = 1.91f;
        if (isYawInRange(yaw, 135, 17) && !isYawInRange(yaw, 135, 14)) result = 1.85f;
        if (isYawInRange(yaw, 135, 18) && !isYawInRange(yaw, 135, 17)) result = 1.82f;
        if (isYawInRange(yaw, 135, 19) && !isYawInRange(yaw, 135, 18)) result = 1.8f;
        if (isYawInRange(yaw, 135, 23) && !isYawInRange(yaw, 135, 19)) result = 1.77f;
        if (isYawInRange(yaw, 135, 24) && !isYawInRange(yaw, 135, 23)) result = 1.75f;
        if (isYawInRange(yaw, 135, 28) && !isYawInRange(yaw, 135, 24)) result = 1.7f;

        if (isYawInRange(yaw, 225, 14) && !isYawInRange(yaw, 225, 13)) result = 1.91f;
        if (isYawInRange(yaw, 225, 17) && !isYawInRange(yaw, 225, 14)) result = 1.85f;
        if (isYawInRange(yaw, 225, 18) && !isYawInRange(yaw, 225, 17)) result = 1.82f;
        if (isYawInRange(yaw, 225, 19) && !isYawInRange(yaw, 225, 18)) result = 1.8f;
        if (isYawInRange(yaw, 225, 23) && !isYawInRange(yaw, 225, 19)) result = 1.77f;
        if (isYawInRange(yaw, 225, 24) && !isYawInRange(yaw, 225, 23)) result = 1.75f;
        if (isYawInRange(yaw, 225, 28) && !isYawInRange(yaw, 225, 24)) result = 1.7f;

        if (isYawInRange(yaw, 315, 14) && !isYawInRange(yaw, 315, 13)) result = 1.91f;
        if (isYawInRange(yaw, 315, 17) && !isYawInRange(yaw, 315, 14)) result = 1.85f;
        if (isYawInRange(yaw, 315, 18) && !isYawInRange(yaw, 315, 17)) result = 1.82f;
        if (isYawInRange(yaw, 315, 19) && !isYawInRange(yaw, 315, 18)) result = 1.8f;
        if (isYawInRange(yaw, 315, 23) && !isYawInRange(yaw, 315, 19)) result = 1.77f;
        if (isYawInRange(yaw, 315, 24) && !isYawInRange(yaw, 315, 23)) result = 1.75f;
        if (isYawInRange(yaw, 315, 28) && !isYawInRange(yaw, 315, 24)) result = 1.7f;

        if (pitch <= -30 || pitch >= 30) result = 1.615f;
        return result;
    }

    public static boolean isYawInRange(float yaw, float firstValue, float radiusValue) {
        yaw = (yaw % 360 + 360) % 360;
        firstValue = (firstValue % 360 + 360) % 360;
        float minValue = (firstValue - radiusValue + 360) % 360;
        float maxValue = (firstValue + radiusValue) % 360;
        if (minValue < maxValue) return yaw >= minValue && yaw <= maxValue;
        return yaw >= minValue || yaw <= maxValue;
    }

    @Subscribe
    private void onFirework(FireworkEvent event) {
        LivingEntity boosted = event.getBoostedEntity();
        if (mc.player == null || boosted != mc.player) return;

        float speed;
        if (mode.is("Custom")) {
            speed = Math.max(customSpeed.getFloatValue(), 0f);
        } else if (mode.is("CustomPitch")) {
            speed = getCustomPitchSpeed();
        } else {
            speed = getRageSpeed();
        }

        KillAura aura = Endless.getInstance().getModuleStorage().get(KillAura.class);

        event.setSpeed(speed);
    }
}

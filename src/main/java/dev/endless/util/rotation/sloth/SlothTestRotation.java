package dev.endless.util.rotation.sloth;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import dev.endless.util.IMinecraft;
import dev.endless.util.rotation.Rotation;
import dev.endless.util.rotation.RotationComponent;

/**
 * "Sloth Test" rotation, ported one-to-one from the reference {@code SlothRotation}
 * inside this project's {@code wonderful-1.21.4-new/} reference tree, but adapted
 * to the Endless rotation API:
 *  <ul>
 *    <li>{@code RotationStorage.update(...)} → {@link RotationComponent#update}</li>
 *    <li>{@code fun.wonderful.api.utils.rotate.Rotation} → {@link Rotation}</li>
 *    <li>{@code Aura.clientLook.isState()} → caller-supplied flag</li>
 *    <li>{@code shouldUseElytraPredict / getPredictedPoint} → caller-supplied
 *        predicted bounding-box centre via {@link #updateRotations}.</li>
 *  </ul>
 *
 * The class keeps the same per-target tracking, reaction delay, hit-pitch
 * animation, spring/damper interpolation, mouse-GCD quantisation and noise
 * profile as the reference implementation. Tweaks of any kind are deliberately
 * avoided — this is a 1:1 port.
 */
public class SlothTestRotation implements IMinecraft {

    // ── Per-target tracking ─────────────────────────────────────────────────
    private LivingEntity trackedTarget;

    private float currentYaw;
    private float currentPitch;

    private float velocityYaw;
    private float velocityPitch;

    /** Random offset inside the target's bounding box that we aim at. */
    private double aimPointX;
    private double aimPointY;
    private double aimPointZ;

    // ── Hand-tremor noise generator ─────────────────────────────────────────
    private float noiseAngle;
    private final float noiseAmplitude = 1.8F;

    // ── Hit (post-attack) pitch animation ──────────────────────────────────
    private int hitPhase;   // 0 idle, 1 raising pitch up, 2 returning down
    private int hitTimer;
    private float pitchBeforeHit;

    // ── Reaction delay (first-seen → first move) ───────────────────────────
    private long firstSeenTime;
    private int reactionMs;
    private boolean reactionComplete;

    // ── Smoothing / mouse-pixel locking ────────────────────────────────────
    private float lastSentYaw;
    private float lastSentPitch;
    private float smoothYaw;
    private float smoothPitch;

    // ────────────────────────────────────────────────────────────────────────
    //   Public API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Resets all internal state. Call when the rotation switches off, the
     * target is lost, or the aura disables.
     */
    public void reset() {
        trackedTarget = null;
        velocityYaw = velocityPitch = 0.0F;
        aimPointX = aimPointY = aimPointZ = 0.0;
        noiseAngle = 0.0F;
        hitPhase = hitTimer = 0;
        firstSeenTime = 0;
        reactionComplete = false;
        reactionMs = 0;

        if (mc.player != null) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
            lastSentYaw = currentYaw;
            lastSentPitch = currentPitch;
            smoothYaw = currentYaw;
            smoothPitch = currentPitch;
        } else {
            currentYaw = currentPitch = 0.0F;
            lastSentYaw = lastSentPitch = 0.0F;
            smoothYaw = smoothPitch = 0.0F;
        }
    }

    /**
     * Triggers the post-attack pitch animation. Call once per swing.
     */
    public void onAttack() {
        hitPhase = 1;
        hitTimer = 0;
        pitchBeforeHit = currentPitch;
    }

    /**
     * Main per-frame entry point. Sends one fresh rotation through
     * {@link RotationComponent} every call.
     *
     * @param target          Current Aura target; must be non-null.
     * @param predictedCenter Predicted target bounding-box centre (caller
     *                        decides whether to apply elytra predict). When
     *                        elytra predict isn't desired, pass the plain
     *                        {@code target.getBoundingBox().getCenter()}.
     * @param clientLook      Whether the rotation should also rotate the
     *                        client camera.
     */
    public void updateRotations(LivingEntity target, Vec3d predictedCenter, boolean clientLook) {
        if (mc.player == null || target == null) return;

        boolean playerFlying = mc.player.isGliding();

        // ── Target switch ───────────────────────────────────────────────────
        if (trackedTarget != target) {
            trackedTarget = target;

            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
            lastSentYaw = currentYaw;
            lastSentPitch = currentPitch;
            smoothYaw = currentYaw;
            smoothPitch = currentPitch;
            velocityYaw = velocityPitch = 0.0F;

            pickAimPoint(target);

            hitPhase = hitTimer = 0;
            noiseAngle = (float) (Math.random() * Math.PI * 2);

            float angleDiff = measureAngle(target);
            reactionMs = computeReaction(angleDiff);
            firstSeenTime = System.currentTimeMillis();
            reactionComplete = false;
        }

        Vec3d eyePos = mc.player.getEyePos();
        float distance = (float) eyePos.distanceTo(predictedCenter);
        float gcd = calcGcd();

        // ── Reaction-delay phase: tiny camera jitter, no aiming yet ─────────
        if (!reactionComplete) {
            long elapsed = System.currentTimeMillis() - firstSeenTime;

            if (elapsed < reactionMs) {
                float jitterY = ((float) Math.random() - 0.5F) * 0.22F;
                float jitterP = ((float) Math.random() - 0.5F) * 0.14F;

                float outY = lastSentYaw + jitterY;
                float outP = MathHelper.clamp(lastSentPitch + jitterP, -89.0F, 89.0F);

                outY -= (outY - lastSentYaw) % gcd;
                outP -= (outP - lastSentPitch) % gcd;

                lastSentYaw = outY;
                lastSentPitch = outP;

                RotationComponent.update(new Rotation(outY, outP),
                        360.0F, 45.0F, 45.0F, 45.0F, 0, 1, clientLook);
                return;
            }

            reactionComplete = true;
        }

        float[] noise = generateNoise(distance);

        // ── Hit (post-attack) pitch animation ──────────────────────────────
        if (hitPhase > 0) {
            hitTimer++;

            int upDuration = 25;
            int downDuration = 20;
            float targetPitchUp = -89.0F;

            if (hitPhase == 1) {
                float t = hitTimer / (float) upDuration;
                t = MathHelper.clamp(t, 0.0F, 1.0F);
                float curved = accelCurve(t);
                currentPitch = MathHelper.lerp(curved, pitchBeforeHit, targetPitchUp);

                if (hitTimer >= upDuration) {
                    hitPhase = 2;
                    hitTimer = 0;
                }
            } else if (hitPhase == 2) {
                float goal = pitchBeforeHit;
                float t = hitTimer / (float) downDuration;
                t = MathHelper.clamp(t, 0.0F, 1.0F);
                float curved = smoothStep(t);
                currentPitch = MathHelper.lerp(curved, targetPitchUp, goal);

                if (hitTimer >= downDuration) {
                    hitPhase = 0;
                    hitTimer = 0;
                }
            }

            float outY = currentYaw + noise[0];
            float outP = MathHelper.clamp(currentPitch + noise[1], -89.0F, 89.0F);

            outY -= (outY - lastSentYaw) % gcd;
            outP -= (outP - lastSentPitch) % gcd;

            lastSentYaw = outY;
            lastSentPitch = outP;

            RotationComponent.update(new Rotation(outY, outP),
                    360.0F, 45.0F, 45.0F, 45.0F, 0, 1, clientLook);
            return;
        }

        // Occasionally re-roll the random aim spot to mimic a human shifting focus.
        if (Math.random() < 0.015) {
            pickAimPoint(target);
        }

        // ── Compute desired yaw/pitch via 2-tick velocity predict ──────────
        Vec3d targetVel = new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );

        // Reference impl uses 2-tick predict for grounded targets, 0 for elytra.
        int predictTicks = target.isGliding() ? 0 : 2;
        Vec3d aheadCenter = predictedCenter.add(targetVel.multiply(predictTicks));
        Vec3d aimPos = aheadCenter.add(aimPointX, aimPointY, aimPointZ);
        Vec3d direction = aimPos.subtract(eyePos);

        float wantYaw = (float) MathHelper.wrapDegrees(
                Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(
                Math.atan2(direction.y, direction.horizontalLength()));

        float diffYaw = MathHelper.wrapDegrees(wantYaw - currentYaw);
        float diffPitch = wantPitch - currentPitch;

        // ── Speed multiplier (elytra-aware, also damped while moving fwd) ──
        float speedMultiplier = 1.0F;

        if (playerFlying) {
            float currentAngle = calculateCurrentAngle(wantYaw, wantPitch);

            if (currentAngle > 120.0F) {
                speedMultiplier = 0.18F;
            } else if (currentAngle > 80.0F) {
                float t = (currentAngle - 80.0F) / 40.0F;
                speedMultiplier = MathHelper.lerp(smoothStep(t), 0.35F, 0.18F);
            } else if (currentAngle > 25.0F) {
                float t = (currentAngle - 25.0F) / 55.0F;
                speedMultiplier = MathHelper.lerp(smoothStep(t), 0.65F, 0.35F);
            } else {
                speedMultiplier = 0.65F + (0.35F * (1.0F - currentAngle / 25.0F));
            }
        } else {
            boolean movingForward = isMovingForward();
            boolean overtaking = isOvertakingTarget(target);
            if (movingForward || overtaking) {
                speedMultiplier = 0.5F;
            }
        }

        // ── Spring/damper integration ──────────────────────────────────────
        float stiffness = (0.038F + (float) Math.random() * 0.009F) * speedMultiplier;
        float damping = 0.68F + (0.12F * (1.0F - speedMultiplier));

        float totalDiff = (float) Math.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);

        if (totalDiff > 32.0F) {
            stiffness += 0.018F * speedMultiplier;
        } else if (totalDiff < 4.2F) {
            stiffness *= 0.48F;
        }

        stiffness += MathHelper.clamp((distance - 1.6F) / 7.5F, 0.0F, 0.045F) * speedMultiplier;

        velocityYaw = springInterp(currentYaw, currentYaw + diffYaw, velocityYaw, stiffness, damping);
        velocityPitch = springInterp(currentPitch, wantPitch, velocityPitch, stiffness * 0.87F, damping);

        float maxVelYaw = 7.5F * speedMultiplier;
        float maxVelPitch = 5.8F * speedMultiplier;

        velocityYaw = MathHelper.clamp(velocityYaw, -maxVelYaw, maxVelYaw);
        velocityPitch = MathHelper.clamp(velocityPitch, -maxVelPitch, maxVelPitch);

        currentYaw += velocityYaw;
        currentPitch += velocityPitch;
        currentPitch = MathHelper.clamp(currentPitch, -89.0F, 89.0F);

        float smoothFactor = playerFlying ? (0.3F + speedMultiplier * 0.4F) : 0.85F;

        smoothYaw = smoothLerp(smoothYaw, currentYaw, smoothFactor);
        smoothPitch = smoothLerp(smoothPitch, currentPitch, smoothFactor * 0.95F);

        float outY = smoothYaw + noise[0];
        float outP = smoothPitch + noise[1];
        outP = MathHelper.clamp(outP, -89.0F, 89.0F);

        outY -= (outY - lastSentYaw) % gcd;
        outP -= (outP - lastSentPitch) % gcd;

        lastSentYaw = outY;
        lastSentPitch = outP;

        RotationComponent.update(new Rotation(outY, outP),
                360.0F, 45.0F, 45.0F, 45.0F, 0, 1, clientLook);
    }

    // ────────────────────────────────────────────────────────────────────────
    //   Helpers — verbatim from reference SlothRotation
    // ────────────────────────────────────────────────────────────────────────

    private float calcGcd() {
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (s * s * s * 1.2);
    }

    private void pickAimPoint(LivingEntity e) {
        Box bb = e.getBoundingBox();
        double w = bb.maxX - bb.minX;
        double h = bb.maxY - bb.minY;
        double d = bb.maxZ - bb.minZ;

        aimPointX = (Math.random() - 0.5) * w * 0.12;
        aimPointY = (Math.random() - 0.5) * h * 0.11;
        aimPointZ = (Math.random() - 0.5) * d * 0.12;
    }

    private float measureAngle(LivingEntity e) {
        if (mc.player == null) return 0.0F;

        Vec3d eyes = mc.player.getEyePos();
        Vec3d mid = e.getBoundingBox().getCenter();
        Vec3d delta = mid.subtract(eyes);

        float needYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float needPitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength()));

        float dYaw = Math.abs(MathHelper.wrapDegrees(needYaw - mc.player.getYaw()));
        float dPitch = Math.abs(needPitch - mc.player.getPitch());

        return dYaw + dPitch;
    }

    private int computeReaction(float angle) {
        if (angle > 130.0F) return 140 + (int) (Math.random() * 90);
        if (angle > 70.0F)  return 90  + (int) (Math.random() * 60);
        if (angle > 30.0F)  return 45  + (int) (Math.random() * 35);
        return 12 + (int) (Math.random() * 20);
    }

    private boolean isMovingForward() {
        if (mc.player == null) return false;
        return mc.options.forwardKey.isPressed();
    }

    private boolean isOvertakingTarget(LivingEntity target) {
        if (mc.player == null || target == null) return false;

        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = target.getPos();

        Vec3d playerVel = new Vec3d(
                mc.player.getX() - mc.player.prevX,
                mc.player.getY() - mc.player.prevY,
                mc.player.getZ() - mc.player.prevZ);

        Vec3d targetVel = new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ);

        Vec3d toTarget = targetPos.subtract(playerPos).normalize();

        double playerSpeedToTarget = playerVel.dotProduct(toTarget);
        double targetSpeedToPlayer = targetVel.dotProduct(toTarget.multiply(-1));
        double relativeSpeed = playerSpeedToTarget + targetSpeedToPlayer;

        double distance = Math.sqrt(
                Math.pow(playerPos.x - targetPos.x, 2) +
                        Math.pow(playerPos.z - targetPos.z, 2));

        return relativeSpeed > 0.05 && distance < 4.0;
    }

    private float[] generateNoise(float dist) {
        noiseAngle += 0.042F + (float) (Math.random() * 0.018F);

        float scale = MathHelper.clamp(dist / 4.5F, 0.25F, 1.0F);
        float amp = noiseAmplitude * scale;

        float n1 = (float) Math.sin(noiseAngle * 0.87) * 0.38F;
        float n2 = (float) Math.sin(noiseAngle * 1.43 + 0.75) * 0.28F;
        float n3 = (float) Math.cos(noiseAngle * 1.18 + 0.35) * 0.32F;
        float n4 = (float) Math.cos(noiseAngle * 1.76 + 1.42) * 0.23F;

        float yawNoise = (n1 + n2) * amp;
        float pitchNoise = (n3 + n4) * amp * 0.52F;

        yawNoise += ((float) Math.random() - 0.5F) * amp * 0.13F;
        pitchNoise += ((float) Math.random() - 0.5F) * amp * 0.09F;

        return new float[]{yawNoise, pitchNoise};
    }

    private float smoothStep(float x) {
        x = MathHelper.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private float accelCurve(float x) {
        x = MathHelper.clamp(x, 0.0F, 1.0F);
        return 1.0F - (1.0F - x) * (1.0F - x);
    }

    private float springInterp(float current, float target, float vel, float stiffness, float damping) {
        float diff = target - current;
        float acc = diff * stiffness - vel * damping;
        return vel + acc;
    }

    private float smoothLerp(float from, float to, float alpha) {
        alpha = MathHelper.clamp(alpha, 0.0F, 1.0F);
        float delta = MathHelper.wrapDegrees(to - from);
        return from + delta * alpha;
    }

    private float calculateCurrentAngle(float targetYaw, float targetPitch) {
        float dYaw = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw));
        float dPitch = Math.abs(targetPitch - currentPitch);
        return dYaw + dPitch;
    }
}

package dev.endless.util.neuro.rotation;

import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import dev.endless.Endless;
import dev.endless.event.list.EventTick;
import dev.endless.module.list.combat.KillAura;
import dev.endless.util.IMinecraft;
import dev.endless.util.math.BestPoint;
import dev.endless.util.math.RotationUtil;
import dev.endless.util.rotation.Rotation;

import java.util.ArrayList;
import java.util.List;

public class AIRotationRecorder implements IMinecraft {
    
    @Getter
    private static boolean recording = false;
    private static final List<TrainingSample> samples = new ArrayList<>();
    
    private static Rotation previousRotation = null;
    private static float previousDeltaYaw = 0;
    private static float previousDeltaPitch = 0;

    @Subscribe
    public void onTick(EventTick event) {
        if (!recording || mc.player == null) return;

        
        LivingEntity target = KillAura.lastTarget;
        if (target == null || !target.isAlive()) return;

        
        KillAura killAura = Endless.getInstance().getModuleStorage().get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) return;

        
        Rotation currentRotation = new Rotation(
            MathHelper.wrapDegrees(mc.player.getYaw()), 
            mc.player.getPitch()
        );

        if (previousRotation == null) {
            previousRotation = currentRotation;
            System.out.println("AI RECORDER: Initialized with rotation " + 
                String.format("%.2f, %.2f", currentRotation.getYaw(), currentRotation.getPitch()));
            return;
        }

        
        float actualDeltaYaw = MathHelper.wrapDegrees(currentRotation.getYaw() - previousRotation.getYaw());
        float actualDeltaPitch = currentRotation.getPitch() - previousRotation.getPitch();

        
        double distance = killAura.distance.getValue();
        Vec3d targetPoint = BestPoint.getMultipoint(target, distance);
        Rotation targetRotation = new Rotation(RotationUtil.calculate(targetPoint));

        
        float targetDeltaYaw = MathHelper.wrapDegrees(targetRotation.getYaw() - currentRotation.getYaw());
        float targetDeltaPitch = targetRotation.getPitch() - currentRotation.getPitch();

        
        if (!isValidData(actualDeltaYaw, actualDeltaPitch, targetDeltaYaw, targetDeltaPitch)) {
            System.out.println("AI RECORDER: Invalid data, skipping sample");
            
            previousRotation = currentRotation;
            previousDeltaYaw = actualDeltaYaw;
            previousDeltaPitch = actualDeltaPitch;
            return;
        }

        
        float[] input = new float[]{
            previousDeltaYaw,
            previousDeltaPitch,
            targetDeltaYaw,
            targetDeltaPitch
        };

        
        float[] output = new float[]{
            actualDeltaYaw,
            actualDeltaPitch
        };

        
        samples.add(new TrainingSample(input, output));

        
        if (samples.size() % 20 == 0) {
            System.out.println("AI RECORDER: Sample " + samples.size() + 
                " | Input: [" + String.format("%.2f, %.2f, %.2f, %.2f", input[0], input[1], input[2], input[3]) + 
                "] | Output: [" + String.format("%.2f, %.2f", output[0], output[1]) + "]");
        }

        
        previousRotation = currentRotation;
        previousDeltaYaw = actualDeltaYaw;
        previousDeltaPitch = actualDeltaPitch;
    }

    private static boolean isValidData(float actualDeltaYaw, float actualDeltaPitch, 
                                     float targetDeltaYaw, float targetDeltaPitch) {
        
        if (!Float.isFinite(actualDeltaYaw) || !Float.isFinite(actualDeltaPitch) ||
            !Float.isFinite(targetDeltaYaw) || !Float.isFinite(targetDeltaPitch)) {
            return false;
        }

        
        if (Math.abs(actualDeltaYaw) > 180 || Math.abs(actualDeltaPitch) > 90 ||
            Math.abs(targetDeltaYaw) > 180 || Math.abs(targetDeltaPitch) > 90) {
            return false;
        }

        
        if (Math.abs(actualDeltaYaw) < 0.01f && Math.abs(actualDeltaPitch) < 0.01f) {
            return false;
        }

        return true;
    }

    public static void startRecording() {
        recording = true;
        samples.clear();
        previousRotation = null;
        previousDeltaYaw = 0;
        previousDeltaPitch = 0;
        System.out.println("AI RECORDER: Started recording");
    }

    public static int stopRecording() {
        recording = false;
        int count = samples.size();
        previousRotation = null;
        System.out.println("AI RECORDER: Stopped recording, collected " + count + " samples");
        return count;
    }

    public static List<TrainingSample> getSamples() {
        return new ArrayList<>(samples);
    }

    public static void clearSamples() {
        samples.clear();
    }
}

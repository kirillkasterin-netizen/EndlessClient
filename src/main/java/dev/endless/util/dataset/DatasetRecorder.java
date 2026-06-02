package dev.endless.util.dataset;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import dev.endless.Endless;
import dev.endless.event.list.EventTick;
import dev.endless.module.list.combat.KillAura;
import dev.endless.util.IMinecraft;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Записывает датасет в CSV формат
 */
public class DatasetRecorder implements IMinecraft {
    
    private static boolean recording = false;
    private static DatasetRecorder instance = null;
    private static final List<DatasetFrame> frames = new ArrayList<>();
    private static long startTime = 0;
    
    @Subscribe
    public void onTick(EventTick event) {
        if (!recording || mc.player == null || mc.world == null) return;
        
        // Записываем только когда KillAura ВЫКЛЮЧЕН
        KillAura killAura = Endless.getInstance().getModuleStorage().get(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            return;
        }
        
        // Ищем ближайшую цель
        LivingEntity target = findNearestTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        
        // Записываем фрейм
        long timestamp = System.currentTimeMillis() - startTime;
        float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
        float pitch = mc.player.getPitch();
        
        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = target.getPos();
        float distance = (float) playerPos.distanceTo(targetPos);
        
        boolean onGround = mc.player.isOnGround();
        boolean attacking = mc.options.attackKey.isPressed();
        
        Vec3d targetVel = new Vec3d(
            target.getX() - target.prevX,
            target.getY() - target.prevY,
            target.getZ() - target.prevZ
        );
        float targetVelocity = (float) targetVel.length();
        
        DatasetFrame frame = new DatasetFrame(
            timestamp, yaw, pitch, distance, onGround, attacking, targetVelocity
        );
        
        frames.add(frame);
        
        // Логируем каждые 100 фреймов
        if (frames.size() % 100 == 0) {
            System.out.println("[DatasetRecorder] Записано фреймов: " + frames.size());
        }
    }
    
    private LivingEntity findNearestTarget() {
        if (mc.world == null || mc.player == null) return null;
        
        LivingEntity nearest = null;
        double nearestDistance = 6.0;
        
        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == mc.player) continue;
            if (!living.isAlive()) continue;
            
            double distance = mc.player.distanceTo(living);
            if (distance < nearestDistance) {
                nearest = living;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }
    
    public static void startRecording() {
        if (recording) return;
        
        recording = true;
        frames.clear();
        startTime = System.currentTimeMillis();
        
        if (instance == null) {
            instance = new DatasetRecorder();
        }
        Endless.getInstance().getEventBus().register(instance);
        
        System.out.println("[DatasetRecorder] ========================================");
        System.out.println("[DatasetRecorder] Запись датасета начата");
        System.out.println("[DatasetRecorder] ВЫКЛЮЧИ KillAura и играй ВРУЧНУЮ!");
        System.out.println("[DatasetRecorder] ========================================");
    }
    
    public static int stopRecording() {
        if (!recording) return 0;
        
        recording = false;
        
        if (instance != null) {
            Endless.getInstance().getEventBus().unregister(instance);
        }
        
        int count = frames.size();
        
        if (count > 0) {
            saveToCsv();
        }
        
        System.out.println("[DatasetRecorder] Запись остановлена. Фреймов: " + count);
        return count;
    }
    
    private static void saveToCsv() {
        try {
            Path dataDir = Paths.get("endless/datasets");
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String filename = String.format("endless/datasets/dataset_%s.csv", timestamp);
            
            try (FileWriter writer = new FileWriter(filename)) {
                // Заголовок
                writer.write("timestamp,yaw,pitch,distance,onGround,attacking,targetVelocity\n");
                
                // Данные
                for (DatasetFrame frame : frames) {
                    writer.write(frame.toCsv() + "\n");
                }
            }
            
            System.out.println("[DatasetRecorder] Датасет сохранен: " + filename);
            System.out.println("[DatasetRecorder] Фреймов: " + frames.size());
            
        } catch (IOException e) {
            System.err.println("[DatasetRecorder] Ошибка сохранения: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static boolean isRecording() {
        return recording;
    }
    
    public static int getFrameCount() {
        return frames.size();
    }
}

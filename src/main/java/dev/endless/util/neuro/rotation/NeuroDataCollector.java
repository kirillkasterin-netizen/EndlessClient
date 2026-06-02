package dev.endless.util.neuro.rotation;

import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Улучшенный сборщик данных для ML-ротации
 * Записывает детальные параметры за 10 тиков до удара
 */
public class NeuroDataCollector implements IMinecraft {
    
    private static boolean recording = false;
    private static NeuroDataCollector instance = null;
    
    private static final List<NeuroRotationData> collectedData = new ArrayList<>();
    private static final Queue<NeuroRotationData> tickBuffer = new LinkedList<>();
    private static final int BUFFER_SIZE = 10; // Храним последние 10 тиков
    
    private static float previousYaw = 0;
    private static float previousPitch = 0;
    private static float previousDeltaYaw = 0;
    private static float previousDeltaPitch = 0;
    private static long previousTime = 0;
    private static int ticksUntilAttack = 0;
    private static int lastAttackCooldown = 0;
    private static int ticksSinceLastHit = 0;
    private static boolean wasAttacking = false;
    
    @Subscribe
    public void onTick(EventTick event) {
        if (!recording || mc.player == null || mc.world == null) return;
        
        // ВАЖНО: Записываем только когда KillAura ВЫКЛЮЧЕН!
        // Нужно записывать твою легитимную игру БЕЗ читов
        KillAura killAura = Endless.getInstance().getModuleStorage().get(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            // KillAura включен - не записываем, это не твои движения!
            return;
        }
        
        // Ищем ближайшую цель вручную
        LivingEntity target = findNearestTarget();
        if (target == null || !target.isAlive()) {
            tickBuffer.clear();
            ticksUntilAttack = 0;
            return;
        }
        
        // Текущие параметры
        float currentYaw = MathHelper.wrapDegrees(mc.player.getYaw());
        float currentPitch = mc.player.getPitch();
        long currentTime = System.currentTimeMillis();
        
        // Вычисляем дельты
        float deltaYaw = MathHelper.wrapDegrees(currentYaw - previousYaw);
        float deltaPitch = currentPitch - previousPitch;
        
        // Скорость движения мыши (пикселей в секунду)
        float timeDelta = (currentTime - previousTime) / 1000.0f;
        float mouseSpeed = timeDelta > 0 ? (float) Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch) / timeDelta : 0;
        
        // Ускорение поворота (изменение скорости)
        float rotationAcceleration = (float) Math.sqrt(
            Math.pow(deltaYaw - previousDeltaYaw, 2) + 
            Math.pow(deltaPitch - previousDeltaPitch, 2)
        );
        
        // Параметры цели
        Vec3d targetPos = target.getPos();
        Vec3d playerPos = mc.player.getPos();
        Vec3d eyePos = mc.player.getEyePos();
        float distance = (float) playerPos.distanceTo(targetPos);
        
        Vec3d targetVelocity = new Vec3d(
            target.getX() - target.prevX,
            target.getY() - target.prevY,
            target.getZ() - target.prevZ
        );
        
        // Вычисляем точку прицеливания на хитбоксе (0=ноги, 0.5=тело, 1=голова)
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Vec3d targetBoxCenter = target.getBoundingBox().getCenter();
        double targetHeight = target.getBoundingBox().getLengthY();
        
        // Проецируем взгляд на вертикальную ось цели
        double aimY = eyePos.y + lookVec.y * distance;
        double targetBottomY = target.getY();
        double targetTopY = targetBottomY + targetHeight;
        float aimPointY = (float) MathHelper.clamp((aimY - targetBottomY) / targetHeight, 0.0, 1.0);
        
        // Угол до цели
        Vec3d toTarget = targetBoxCenter.subtract(eyePos);
        double angleToTargetRad = Math.atan2(toTarget.z, toTarget.x);
        float angleToTarget = (float) Math.toDegrees(angleToTargetRad) - 90.0f;
        angleToTarget = MathHelper.wrapDegrees(angleToTarget - currentYaw);
        
        // Определяем движение к цели
        Vec3d playerVelocity = mc.player.getVelocity();
        Vec3d directionToTarget = toTarget.normalize();
        double dotProduct = playerVelocity.normalize().dotProduct(directionToTarget);
        boolean isMovingToTarget = dotProduct > 0.3;
        
        // Определяем направление стрейфа
        float strafeDirection = 0.0f;
        if (mc.player.sidewaysSpeed != 0) {
            strafeDirection = mc.player.sidewaysSpeed > 0 ? 1.0f : -1.0f;
        }
        
        // Определяем тики до атаки по кулдауну
        float cooldown = mc.player.getAttackCooldownProgress(0.0f);
        int currentCooldownTicks = (int) (cooldown * 20); // Примерно
        
        // Отслеживаем атаки
        boolean isAttacking = mc.options.attackKey.isPressed();
        if (isAttacking && !wasAttacking && cooldown > 0.9f) {
            // Новая атака
            ticksUntilAttack = 10;
            ticksSinceLastHit = 0;
        } else if (ticksUntilAttack > 0) {
            ticksUntilAttack--;
        }
        ticksSinceLastHit++;
        wasAttacking = isAttacking;
        
        // Если кулдаун только что сбросился - была атака
        if (lastAttackCooldown > 15 && currentCooldownTicks < 5) {
            ticksUntilAttack = 10;
            ticksSinceLastHit = 0;
        }
        
        lastAttackCooldown = currentCooldownTicks;
        
        // Создаем запись данных
        NeuroRotationData data = new NeuroRotationData();
        data.setDeltaYaw(deltaYaw);
        data.setDeltaPitch(deltaPitch);
        data.setDistanceToTarget(distance);
        data.setTargetVelocityX((float) targetVelocity.x);
        data.setTargetVelocityY((float) targetVelocity.y);
        data.setTargetVelocityZ((float) targetVelocity.z);
        data.setMouseMovementSpeed(mouseSpeed);
        data.setAttackCooldown(mc.player.getAttackCooldownProgress(1.0f));
        data.setPlayerGliding(mc.player.isGliding());
        data.setTargetGliding(target.isGliding());
        data.setTicksBeforeHit(ticksUntilAttack);
        
        // Новые параметры
        data.setAimPointY(aimPointY);
        data.setRotationAcceleration(rotationAcceleration);
        data.setPreviousDeltaYaw(previousDeltaYaw);
        data.setPreviousDeltaPitch(previousDeltaPitch);
        data.setTimeSinceLastHit(ticksSinceLastHit);
        data.setAngleToTarget(angleToTarget);
        data.setMovingToTarget(isMovingToTarget);
        data.setStrafeDirection(strafeDirection);
        
        // Добавляем в буфер
        tickBuffer.offer(data);
        if (tickBuffer.size() > BUFFER_SIZE) {
            tickBuffer.poll();
        }
        
        // Если это был последний тик перед ударом, сохраняем все данные из буфера
        if (ticksUntilAttack == 1 && tickBuffer.size() >= 2) {
            saveBufferData();
        }
        
        // Обновляем предыдущие значения
        previousYaw = currentYaw;
        previousPitch = currentPitch;
        previousDeltaYaw = deltaYaw;
        previousDeltaPitch = deltaPitch;
        previousTime = currentTime;
    }
    
    /**
     * Сохраняет данные из буфера с установленными выходными значениями
     */
    private void saveBufferData() {
        List<NeuroRotationData> bufferList = new ArrayList<>(tickBuffer);
        
        int savedCount = 0;
        // Для каждого тика устанавливаем следующее движение как выход
        for (int i = 0; i < bufferList.size() - 1; i++) {
            NeuroRotationData current = bufferList.get(i);
            NeuroRotationData next = bufferList.get(i + 1);
            
            current.setNextDeltaYaw(next.getDeltaYaw());
            current.setNextDeltaPitch(next.getDeltaPitch());
            
            if (current.isValid()) {
                collectedData.add(current);
                savedCount++;
            }
        }
        
        if (savedCount > 0) {
            System.out.println("[NeuroCollector] Сохранено " + savedCount + " сэмплов из буфера. Всего: " + collectedData.size());
        }
        
        if (collectedData.size() % 100 == 0 && collectedData.size() > 0) {
            System.out.println("[NeuroCollector] ✓ Собрано " + collectedData.size() + " сэмплов");
        }
    }
    
    /**
     * Находит ближайшую цель для записи данных
     */
    private LivingEntity findNearestTarget() {
        if (mc.world == null || mc.player == null) return null;
        
        LivingEntity nearest = null;
        double nearestDistance = 6.0; // Максимальная дистанция
        
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
    
    /**
     * Начинает запись данных
     */
    public static void startRecording() {
        if (recording) return;
        
        recording = true;
        collectedData.clear();
        tickBuffer.clear();
        previousYaw = 0;
        previousPitch = 0;
        previousDeltaYaw = 0;
        previousDeltaPitch = 0;
        previousTime = System.currentTimeMillis();
        ticksUntilAttack = 0;
        lastAttackCooldown = 0;
        ticksSinceLastHit = 0;
        wasAttacking = false;
        
        // Регистрируем в EventBus
        if (instance == null) {
            instance = new NeuroDataCollector();
        }
        Endless.getInstance().getEventBus().register(instance);
        
        System.out.println("[NeuroCollector] ========================================");
        System.out.println("[NeuroCollector] Начата запись данных");
        System.out.println("[NeuroCollector] ========================================");
        System.out.println("[NeuroCollector] ВАЖНО: ВЫКЛЮЧИТЕ ВСЕ ЧИТЫ!");
        System.out.println("[NeuroCollector] Играйте ВРУЧНУЮ как обычно:");
        System.out.println("[NeuroCollector] - Двигайте мышью сами");
        System.out.println("[NeuroCollector] - Атакуйте сами (ЛКМ)");
        System.out.println("[NeuroCollector] - Играйте естественно 15-20 минут");
        System.out.println("[NeuroCollector] ========================================");
        System.out.println("[NeuroCollector] Зарегистрирован в EventBus");
    }
    
    /**
     * Останавливает запись и сохраняет данные в JSON
     * @return количество собранных сэмплов
     */
    public static int stopRecording() {
        if (!recording) return 0;
        
        recording = false;
        
        // Отписываемся от EventBus
        if (instance != null) {
            Endless.getInstance().getEventBus().unregister(instance);
            System.out.println("[NeuroCollector] Отписан от EventBus");
        }
        
        int count = collectedData.size();
        
        if (count > 0) {
            saveToJson();
        } else {
            System.out.println("[NeuroCollector] Нет данных для сохранения!");
            System.out.println("[NeuroCollector] Убедитесь что:");
            System.out.println("[NeuroCollector] 1. KillAura включен");
            System.out.println("[NeuroCollector] 2. Вы атакуете мобов/игроков");
            System.out.println("[NeuroCollector] 3. Цель в радиусе атаки");
        }
        
        System.out.println("[NeuroCollector] Остановлена запись. Собрано " + count + " сэмплов");
        return count;
    }
    
    /**
     * Сохраняет собранные данные в JSON файл
     */
    private static void saveToJson() {
        try {
            // Создаем директорию если не существует
            Path dataDir = Paths.get("endless/neuro_data");
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            
            // Генерируем имя файла с датой и временем
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String filename = String.format("endless/neuro_data/rotation_data_%s.json", timestamp);
            
            // Сохраняем в JSON
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(filename)) {
                gson.toJson(collectedData, writer);
            }
            
            System.out.println("[NeuroCollector] Данные сохранены в " + filename);
            System.out.println("[NeuroCollector] Всего сэмплов: " + collectedData.size());
            
        } catch (IOException e) {
            System.err.println("[NeuroCollector] Ошибка сохранения данных: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Получает собранные данные
     * @return список данных
     */
    public static List<NeuroRotationData> getCollectedData() {
        return new ArrayList<>(collectedData);
    }
    
    /**
     * Очищает собранные данные
     */
    public static void clearData() {
        collectedData.clear();
        tickBuffer.clear();
    }
    
    /**
     * Проверяет, идет ли запись
     * @return true если запись активна
     */
    public static boolean isRecording() {
        return recording;
    }
}

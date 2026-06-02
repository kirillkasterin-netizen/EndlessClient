package dev.endless.util.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class RenderUtils {
    
    public static Vec2f worldToScreen(Vec3d pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();
        
        if (!camera.isReady() || mc.getWindow() == null) {
            return null;
        }
        
        Vec3d cameraPos = camera.getPos();
        Vec3d relativePos = pos.subtract(cameraPos);
        
        // Получаем матрицы проекции и вида
        Matrix4f projectionMatrix = mc.gameRenderer.getBasicProjectionMatrix(mc.gameRenderer.getFov(camera, mc.getRenderTickCounter().getTickDelta(false), true));
        Matrix4f modelViewMatrix = new Matrix4f();
        
        // Применяем вращение камеры
        modelViewMatrix.rotate((float) Math.toRadians(camera.getPitch()), 1, 0, 0);
        modelViewMatrix.rotate((float) Math.toRadians(camera.getYaw() + 180), 0, 1, 0);
        
        // Создаем вектор позиции
        Vector4f position = new Vector4f(
            (float) relativePos.x,
            (float) relativePos.y,
            (float) relativePos.z,
            1.0f
        );
        
        // Применяем трансформации
        position = modelViewMatrix.transform(position);
        position = projectionMatrix.transform(position);
        
        // Проверяем, находится ли точка за камерой
        if (position.w <= 0) {
            return null;
        }
        
        // Нормализуем координаты
        position.x /= position.w;
        position.y /= position.w;
        
        // Проверяем, находится ли точка в пределах экрана
        if (Math.abs(position.x) > 1.5f || Math.abs(position.y) > 1.5f) {
            return null;
        }
        
        // Преобразуем в экранные координаты
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        
        float screenX = (position.x + 1.0f) * 0.5f * width;
        float screenY = (1.0f - position.y) * 0.5f * height;
        
        return new Vec2f(screenX, screenY);
    }
}

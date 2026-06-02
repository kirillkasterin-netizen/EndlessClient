package dev.endless.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import dev.endless.event.list.EventTick;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;

/**
 * High Jump — высокий прыжок при открытии шалкера рядом.
 *
 * Работает только когда:
 *  1. рядом (XZ ≤ 1 блок) есть шалкер с прогрессом анимации > 0 и < 1 (открывается);
 *  2. игрок на одной высоте с шалкером (|dy| ≤ 2 блока, или ≤ 30 если уже летим вверх);
 *  3. игрок не падает (fallDistance == 0).
 *
 * При выполнении условий velocity Y → 2.0, что даёт высокий прыжок.
 *
 * Адаптировано из etc1337 HighJump.
 */
@ModuleInformation(moduleName = "High Jump",
        moduleDesc = "Высокий прыжок от открывающегося шалкера",
        moduleCategory = ModuleCategory.MOVEMENT)
public class HighJump extends Module {

    private static final float SHULKER_PUSH = 2.0f;
    private static final int SCAN_RADIUS = 6;

    @Subscribe
    public void onUpdate(final EventTick ignored) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.fallDistance != 0.0f) return;

        BlockPos playerPos = mc.player.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(
                playerPos.add(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                playerPos.add(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {

            BlockEntity be = mc.world.getBlockEntity(pos);
            if (!(be instanceof ShulkerBoxBlockEntity shulker)) continue;

            // Шалкер должен быть в фазе открытия (progress > 0 и < 1).
            float progress = shulker.getAnimationProgress(1.0f);
            if (progress <= 0.0f || progress >= 1.0f) continue;

            // Расстояние до шалкера.
            double dx = mc.player.getX() - (pos.getX() + 0.5);
            double dz = mc.player.getZ() - (pos.getZ() + 0.5);
            double dy = mc.player.getY() - (pos.getY() + 0.5);

            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance > 1.0) continue;

            // Допуск по вертикали: если уже летим вверх — большая зона.
            double maxAbsDy = mc.player.getVelocity().y > 1.0 ? 30.0 : 2.0;
            if (Math.abs(dy) > maxAbsDy) continue;

            // Все проверки прошли — пушим.
            Vec3d vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x, SHULKER_PUSH, vel.z);
            mc.player.velocityDirty = true;
            return; // одного шалкера достаточно
        }
    }
}

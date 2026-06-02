package endless.ere.base.player;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;
import endless.ere.utility.game.player.PlayerIntersectionUtil;
import endless.ere.utility.game.player.SimulatedPlayer;
import endless.ere.utility.interfaces.IClient;
import endless.ere.utility.math.Timer;


@Setter
@Getter
@UtilityClass
public class AttackUtil implements IClient {
    private final Timer attackTimer = new Timer();
    private int count = 0;




    public void attackEntity(Entity entity) {
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        attackTimer.reset();
        count++;

    }




    public boolean hasMovementRestrictions() {
        return mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || PlayerIntersectionUtil.isPlayerInBlock(Blocks.COBWEB)
                || mc.player.isSubmergedInWater()
                || mc.player.isInLava()
                || mc.player.isClimbing()
                || !PlayerIntersectionUtil.canChangeIntoPose(EntityPose.STANDING) && mc.player.isInSneakingPose()
                || mc.player.getAbilities().flying;
    }

    public boolean hasPreMovementRestrictions(SimulatedPlayer simulatedPlayer) {
        return simulatedPlayer.hasStatusEffect(StatusEffects.BLINDNESS)
                || simulatedPlayer.hasStatusEffect(StatusEffects.LEVITATION)
                || PlayerIntersectionUtil.isBoxInBlock(simulatedPlayer.boundingBox, Blocks.COBWEB)
                || simulatedPlayer.isSubmergedInWater()
                || simulatedPlayer.isInLava()
                || simulatedPlayer.isClimbing()
                || !PlayerIntersectionUtil.canChangeIntoPose(EntityPose.STANDING) && mc.player.isInSneakingPose()
                || mc.player.getAbilities().flying;
    }

    public boolean isPlayerInCriticalState() {
        boolean crit = mc.player.fallDistance > 0 && (mc.player.fallDistance < 0.08 || !SimulatedPlayer.simulateLocalPlayer(1).onGround);
        return !mc.player.isOnGround() && (crit );
    }

    public boolean isPrePlayerInCriticalState(  SimulatedPlayer simulatedPlayer) {
        boolean crit = simulatedPlayer.fallDistance > 0 && (simulatedPlayer.fallDistance < 0.08 || !SimulatedPlayer.simulateLocalPlayer(2).onGround);
        return !simulatedPlayer.onGround && (crit );
    }

    public void resetSprintRage() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        // 1. Шлём явный пакет STOP_SPRINTING СНАЧАЛА (до изменения состояния)
        // Это гарантирует что Grim увидит сброс спринта раньше любых других пакетов
        mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(
                mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        // 2. Обновляем клиентское состояние
        mc.player.setSprinting(false);
        // 3. lastSprinting = текущему чтобы ванильный sendSprintingPacket в onGameEvent не дублировал
        mc.player.lastSprinting = false;
    }

}

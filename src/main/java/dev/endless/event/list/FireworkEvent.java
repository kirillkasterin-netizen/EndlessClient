package dev.endless.event.list;

import net.minecraft.entity.LivingEntity;
import dev.endless.event.Event;

public class FireworkEvent extends Event {
    private final LivingEntity boostedEntity;
    private float speed;

    public FireworkEvent(LivingEntity boostedEntity, float speed) {
        this.boostedEntity = boostedEntity;
        this.speed = speed;
    }

    public LivingEntity getBoostedEntity() { return boostedEntity; }

    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }
}

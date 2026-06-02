package dev.endless.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import dev.endless.event.Event;

@Getter
@AllArgsConstructor
public class EventRightClickBlock extends Event {
    private final Hand hand;
    private final BlockHitResult hitResult;
}

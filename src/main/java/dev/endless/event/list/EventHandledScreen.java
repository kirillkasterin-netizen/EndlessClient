package dev.endless.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.screen.slot.Slot;
import dev.endless.event.Event;

@Getter
@AllArgsConstructor
public class EventHandledScreen extends Event {
    private final Slot slotHover;
}

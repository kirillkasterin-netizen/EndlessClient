package dev.endless.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import dev.endless.event.Event;

@Getter
@Setter
@AllArgsConstructor
public class EventChangeSprint extends Event {
    private boolean sprinting;
}

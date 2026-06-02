package dev.endless.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import dev.endless.event.Event;

@Getter
@AllArgsConstructor
public class ChatEvent extends Event {
    private final String message;
}

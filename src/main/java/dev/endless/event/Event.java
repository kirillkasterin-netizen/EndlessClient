package dev.endless.event;

import lombok.Data;
import dev.endless.Endless;

@Data
public class Event {
    private boolean cancelled;

    public void post() {
        Endless.getInstance().getEventBus().post(this);
    }

    public void cancelEvent() {
        setCancelled(true);
    }
}

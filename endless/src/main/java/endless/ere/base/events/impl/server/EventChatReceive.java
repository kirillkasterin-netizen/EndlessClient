package endless.ere.base.events.impl.server;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.component.Component;
import net.minecraft.text.Text;
import endless.ere.base.events.callables.EventCancellable;
import endless.ere.utility.render.display.base.CustomDrawContext;


@Getter
@Setter
@AllArgsConstructor
public class EventChatReceive extends EventCancellable {

    private Text message;


}
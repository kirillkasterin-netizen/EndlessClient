package endless.ere.utility.render.display.base;

import lombok.Getter;
import net.minecraft.util.Identifier;
import endless.ere.Endless;

@Getter
public class CustomSprite {

    private final Identifier texture;

    public CustomSprite(String path) {
        if (path.contains(":")) {
            this.texture = Identifier.of(path);
        } else if (path.contains("/")) {
            this.texture = Endless.id(path);
        } else {
            this.texture = Endless.id("icons/category/" + path);
        }
    }
}
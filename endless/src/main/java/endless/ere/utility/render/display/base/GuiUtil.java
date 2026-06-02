package endless.ere.utility.render.display.base;

import lombok.experimental.UtilityClass;
import net.minecraft.client.util.math.Vector2f;
import endless.ere.utility.interfaces.IMinecraft;

@UtilityClass
public class GuiUtil implements IMinecraft {

    public boolean isHovered(double x, double y, double width, double height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean isHovered(double x, double y, double width, double height, UIContext context) {
        return isHovered(x, y, width, height, context.getMouseX(), context.getMouseY());
    }

    public boolean isHovered(double x, double y, double width, double height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public Vector2f getMouse(double customScale) {
        double x = mc.mouse.getX() * mc.getWindow().getFramebufferWidth() / mc.getWindow().getWidth() / customScale;
        double y = mc.mouse.getY() * mc.getWindow().getFramebufferHeight() / mc.getWindow().getHeight() / customScale;
        return new Vector2f((float) x, (float) y);
    }
}

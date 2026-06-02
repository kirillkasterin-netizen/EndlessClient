package endless.ere.utility.render.display.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import endless.ere.utility.math.MathUtil;
@AllArgsConstructor
@Data
public class ChangeRect {
    float x; float y; float width; float height;
    public boolean contains(double mx, double my) {
        return MathUtil.isHovered(mx,my,x,y,width,height);
    }
}

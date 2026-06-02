package endless.ere.client.hud.elements.draggable;

import com.google.gson.JsonObject;
import lombok.Generated;
import org.joml.Vector2f;
import endless.ere.Endless;
import endless.ere.client.modules.impl.render.Interface;
import endless.ere.utility.interfaces.IMinecraft;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;

public abstract class DraggableHudElement implements IMinecraft {
   private final String name;
   protected float x;
   protected float y;
   protected float width;
   protected float height;
   private float windowWidth;
   private float windowHeight;
   protected float newX = -1.0F;
   protected float newY = -1.0F;
   private DraggableHudElement.Align align;
   private float offsetX;
   private float offsetY;

   public void tick() {
   }

   public DraggableHudElement(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
      this.align = DraggableHudElement.Align.TOP_LEFT;
      this.offsetX = 0.0F;
      this.offsetY = 0.0F;
      this.name = name;
      this.x = initialX;
      this.y = initialY;
      this.windowWidth = windowWidth;
      this.windowHeight = windowHeight;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.align = align;
   }

   public abstract void render(CustomDrawContext var1);

   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height);
   }

   protected void drawBorder(CustomDrawContext ctx) {
      float borderThickness = 5.5F;
      float borderRadius = 6.0F;
      ColorRGBA borderColor = new ColorRGBA(179, 145, 255, 255);
      ctx.drawRoundedBorder(this.x, this.y, this.width, this.height, borderThickness, BorderRadius.all(borderRadius), borderColor);
   }

   public void set(CustomDrawContext ctx, float x, float y, Interface dragManager, float widthScreen, float heightScreen) {
      Vector2f nerest = dragManager.getNearest(x, y);
      DraggableHudElement.SheetCode x0 = new DraggableHudElement.SheetCode(this, nerest.x, 0.0F);
      DraggableHudElement.SheetCode y0 = new DraggableHudElement.SheetCode(this, nerest.y, 0.0F);
      Vector2f nerest2 = dragManager.getNearest(x + this.width, y + this.height);
      DraggableHudElement.SheetCode x1 = new DraggableHudElement.SheetCode(this, nerest2.x, -this.width);
      DraggableHudElement.SheetCode y1 = new DraggableHudElement.SheetCode(this, nerest2.y, -this.height);
      Vector2f nerest3 = dragManager.getNearest(x + this.width / 2.0F, y + this.height / 2.0F);
      DraggableHudElement.SheetCode x2 = new DraggableHudElement.SheetCode(this, nerest3.x, -this.width / 2.0F);
      DraggableHudElement.SheetCode y2 = new DraggableHudElement.SheetCode(this, nerest3.y, -this.height / 2.0F);
      this.x = x;
      this.y = y;
      this.windowWidth = widthScreen;
      this.windowHeight = heightScreen;
      DraggableHudElement.SheetCode x3 = this.getValue(x0, x1, x2);
      DraggableHudElement.SheetCode y3 = this.getValue(y0, y1, y2);
      this.renderXLine(ctx, x3);
      this.renderYLine(ctx, y3);
      this.update(widthScreen, heightScreen);
   }

   private DraggableHudElement.SheetCode getValue(DraggableHudElement.SheetCode value, DraggableHudElement.SheetCode value2, DraggableHudElement.SheetCode value3) {
      if (value.pos != -1.0F) {
         return value;
      } else {
         return value2.pos != -1.0F ? value2 : value3;
      }
   }

   protected void renderYLine(CustomDrawContext ctx, DraggableHudElement.SheetCode nearest) {
      if (nearest.pos == -1.0F) {
         this.newY = nearest.pos;
      } else {
         ctx.drawRoundedRect(0.0F, nearest.pos, (float)ctx.getScaledWindowWidth(), 1.0F, BorderRadius.ZERO, Endless.getInstance().getThemeManager().getCurrentTheme().getColor().mulAlpha(1.0F));
         this.newY = nearest.pos + nearest.offset;
      }
   }

   protected void renderXLine(CustomDrawContext ctx, DraggableHudElement.SheetCode nearest) {
      if (nearest.pos == -1.0F) {
         this.newX = nearest.pos;
      } else {
         this.newX = nearest.pos + nearest.offset;
      }
   }

   public void set(float x, float y) {
      this.x = x;
      this.y = y;
   }

   public void windowResized(float newWindowWidth, float newWindowHeight) {
      if (!(newWindowHeight <= 0.0F) && !(newWindowWidth <= 0.0F)) {
         float baseX = this.alignToX(this.align, newWindowWidth);
         float baseY = this.alignToY(this.align, newWindowHeight);
         this.x = baseX + this.offsetX;
         this.y = baseY + this.offsetY;
         this.windowWidth = newWindowWidth;
         this.windowHeight = newWindowHeight;
         this.update(newWindowWidth, newWindowHeight);
      }
   }

   public void update(float widthScreen, float heightScreen) {
      if (this.x < 0.0F) {
         this.x = 0.0F;
      }

      if (this.y < 0.0F) {
         this.y = 0.0F;
      }

      if (this.x + this.width > widthScreen) {
         this.x = widthScreen - this.width;
      }

      if (this.y + this.height > heightScreen) {
         this.y = heightScreen - this.height;
      }

   }

   public void release() {
      if (this.newX != -1.0F) {
         this.x = this.newX;
      }

      if (this.newY != -1.0F) {
         this.y = this.newY;
      }

      DraggableHudElement.Align newAlign = this.determineAlign(this.x, this.y, this.windowWidth, this.windowHeight);
      float baseX = this.alignToX(newAlign, this.windowWidth);
      float baseY = this.alignToY(newAlign, this.windowHeight);
      this.align = newAlign;
      this.offsetX = this.x - baseX;
      this.offsetY = this.y - baseY;
   }

   private DraggableHudElement.Align determineAlign(float x, float y, float screenWidth, float screenHeight) {
      boolean left = x + this.width / 2.0F < screenWidth / 3.0F;
      boolean right = x + this.width / 2.0F > screenWidth * 2.0F / 3.0F;
      boolean centerX = !left && !right;
      boolean top = y + this.height / 2.0F < screenHeight / 3.0F;
      boolean bottom = y + this.height / 2.0F > screenHeight * 2.0F / 3.0F;
      boolean centerY = !top && !bottom;
      if (top) {
         if (left) {
            return DraggableHudElement.Align.TOP_LEFT;
         } else {
            return centerX ? DraggableHudElement.Align.TOP_CENTER : DraggableHudElement.Align.TOP_RIGHT;
         }
      } else if (centerY) {
         if (left) {
            return DraggableHudElement.Align.CENTER_LEFT;
         } else {
            return centerX ? DraggableHudElement.Align.CENTER : DraggableHudElement.Align.CENTER_RIGHT;
         }
      } else if (left) {
         return DraggableHudElement.Align.BOTTOM_LEFT;
      } else {
         return centerX ? DraggableHudElement.Align.BOTTOM_CENTER : DraggableHudElement.Align.BOTTOM_RIGHT;
      }
   }

   private float alignToX(DraggableHudElement.Align align, float screenWidth) {
      float var10000;
      switch(align.ordinal()) {
      case 0:
      case 3:
      case 6:
         var10000 = 0.0F;
         break;
      case 1:
      case 4:
      case 7:
         var10000 = screenWidth / 2.0F - this.width / 2.0F;
         break;
      case 2:
      case 5:
      case 8:
         var10000 = screenWidth - this.width;
         break;
      default:
         throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private float alignToY(DraggableHudElement.Align align, float screenHeight) {
      float var10000;
      switch(align.ordinal()) {
      case 0:
      case 1:
      case 2:
         var10000 = 0.0F;
         break;
      case 3:
      case 4:
      case 5:
         var10000 = screenHeight / 2.0F - this.height / 2.0F;
         break;
      case 6:
      case 7:
      case 8:
         var10000 = screenHeight - this.height;
         break;
      default:
         throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public JsonObject save() {
      JsonObject obj = new JsonObject();
      obj.addProperty("x", this.x);
      obj.addProperty("y", this.y);
      obj.addProperty("width", this.width);
      obj.addProperty("height", this.height);
      obj.addProperty("windowWidth", this.windowWidth);
      obj.addProperty("windowHeight", this.windowHeight);
      obj.addProperty("offsetX", this.offsetX);
      obj.addProperty("offsetY", this.offsetY);
      obj.addProperty("align", this.align.name());
      return obj;
   }

   public void load(JsonObject obj) {
      if (obj.has("x")) {
         this.x = obj.get("x").getAsFloat();
      }

      if (obj.has("y")) {
         this.y = obj.get("y").getAsFloat();
      }

      if (obj.has("width")) {
         this.width = obj.get("width").getAsFloat();
      }

      if (obj.has("height")) {
         this.height = obj.get("height").getAsFloat();
      }

      if (obj.has("windowWidth")) {
         this.windowWidth = obj.get("windowWidth").getAsFloat();
      }

      if (obj.has("windowHeight")) {
         this.windowHeight = obj.get("windowHeight").getAsFloat();
      }

      if (obj.has("offsetX")) {
         this.offsetX = obj.get("offsetX").getAsFloat();
      }

      if (obj.has("offsetY")) {
         this.offsetY = obj.get("offsetY").getAsFloat();
      }

      if (obj.has("align")) {
         try {
            this.align = DraggableHudElement.Align.valueOf(obj.get("align").getAsString());
         } catch (IllegalArgumentException var3) {
            this.align = DraggableHudElement.Align.TOP_LEFT;
         }
      }

   }

   public void setPosition(float x, float y) {
      this.x = x;
      this.y = y;
   }

   @Generated
   public String getName() {
      return this.name;
   }

   @Generated
   public float getX() {
      return this.x;
   }

   @Generated
   public float getY() {
      return this.y;
   }

   @Generated
   public float getWidth() {
      return this.width;
   }

   @Generated
   public float getHeight() {
      return this.height;
   }

   public static enum Align {
      TOP_LEFT,
      TOP_CENTER,
      TOP_RIGHT,
      CENTER_LEFT,
      CENTER,
      CENTER_RIGHT,
      BOTTOM_LEFT,
      BOTTOM_CENTER,
      BOTTOM_RIGHT;

   
      private static DraggableHudElement.Align[] $values() {
         return new DraggableHudElement.Align[]{TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER_LEFT, CENTER, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT};
      }
   }

   protected class SheetCode {
      private float pos;
      private float offset;

      public SheetCode(final DraggableHudElement this$0, float pos, float offset) {
         this.pos = pos;
         this.offset = offset;
      }

      @Generated
      public float getPos() {
         return this.pos;
      }

      @Generated
      public float getOffset() {
         return this.offset;
      }

      @Generated
      public void setPos(float pos) {
         this.pos = pos;
      }

      @Generated
      public void setOffset(float offset) {
         this.offset = offset;
      }
   }
}

package dev.endless.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.gl.ShaderProgramKeys;
import dev.endless.event.list.EventHUD;
import dev.endless.event.list.EventPacket;
import dev.endless.event.list.EventRender;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.RenderUtils;
import dev.endless.util.time.Timer;

@ModuleInformation(moduleName = "ObjectInfo", moduleCategory = ModuleCategory.RENDER)
public class ObjectInfo extends Module {
    private final Map<BlockPos, Info> infos = new HashMap<>();
    private final Timer timer = new Timer();

    @Subscribe
    public void onPacket(EventPacket event) {
        if (event.getPacket() instanceof PlaySoundS2CPacket sound) {
            String soundName = sound.getSound().value().id().toString();
            if (soundName.contains("block.piston.extend") || soundName.contains("block.piston.contract")) {
                BlockPos pos = new BlockPos((int)sound.getX(), (int)sound.getY(), (int)sound.getZ());
                if ((sound.getVolume() == 0.5F || sound.getVolume() == 0.7F) && sound.getPitch() == 0.5F) {
                    this.infos.put(pos, new Info(pos.up().add(0, 0, 0), ObjType.TRAP));
                }
                this.timer.reset();
            }

            if (soundName.contains("block.anvil.place")) {
                BlockPos pos = new BlockPos((int)sound.getX(), (int)sound.getY(), (int)sound.getZ());
                if ((sound.getVolume() == 0.5F || sound.getVolume() == 0.7F) && (sound.getPitch() == 1.1F || sound.getPitch() == 0.5F)) {
                    this.infos.put(pos, new Info(pos.up().add(0, 0, 0), ObjType.PLAST));
                }
                this.timer.reset();
            }

            if (soundName.contains("entity.evoker_fangs.attack")) {
                BlockPos pos = new BlockPos((int)sound.getX(), (int)sound.getY(), (int)sound.getZ());
                if ((sound.getVolume() == 0.5F || sound.getVolume() == 0.7F) && sound.getPitch() != 0.85F && sound.getPitch() == 1.0F) {
                    // Логика для драконки
                }
                this.timer.reset();
            }
        }
    }

    @Subscribe
    public void onRender2D(EventHUD event) {
        BlockPos toRemove = null;

        for (Entry<BlockPos, Info> entry : this.infos.entrySet()) {
            Info info = entry.getValue();
            info.draw(event);
            if (info.start.hasReached(info.getType().getTime())) {
                toRemove = entry.getKey();
            }
        }

        if (toRemove != null) {
            this.infos.remove(toRemove);
        }
    }

    @Subscribe
    public void onRender3D(EventRender event) {
        BlockPos toRemove = null;

        for (Entry<BlockPos, Info> entry : this.infos.entrySet()) {
            Info info = entry.getValue();
            info.draw3D(event);
            if (info.start.hasReached(info.getType().getTime())) {
                toRemove = entry.getKey();
            }
        }

        if (toRemove != null) {
            this.infos.remove(toRemove);
        }
    }


    static class Info {
        final BlockPos pos;
        final ObjType type;
        Timer start = new Timer();

        Info(BlockPos pos, ObjType type) {
            this.pos = pos;
            this.type = type;
        }

        void draw(EventHUD e) {
            int remained = (int)((float)(this.type.getTime() - this.start.getElapsedTime()) / 1000.0F);
            MatrixStack matrices = e.getDrawContext().getMatrices();
            BlockPos renderPos = this.pos;
            Vec3d renderPosAdjusted = renderPos.add(0, 1, 0).toCenterPos();
            Vec2f screenPos = worldToScreen(renderPosAdjusted);
            
            if (screenPos != null) {
                float distance = (float)MinecraftClient.getInstance().player.getPos().distanceTo(Vec3d.of(renderPos));
                float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
                matrices.push();
                matrices.translate(screenPos.x, screenPos.y, 0.0F);
                matrices.scale(scale, scale, 1.0F);
                
                String text = this.type.getName() + " (" + remained + " sec)";
                float width = Fonts.SFMEDIUM.get().getWidth(text, 11.0F);
                int x = (int)(-width / 2 - 9);
                
                float fontHeight = 11.0F; // Высота шрифта
                
                // Фон
                DrawUtil.drawRoundedRect(matrices.peek().getPositionMatrix(),
                        (float)(x - 3), 2.0F, (float)(width + 24),
                        fontHeight + 9.0F,
                        3.0F, ColorProvider.rgba(0, 0, 0, 100));
                
                // Прогресс бар
                DrawUtil.drawRoundedRect(matrices.peek().getPositionMatrix(),
                        (float)(x - 3), fontHeight + 9.0F,
                        (width + 24) * (1.0F - (float)this.start.getElapsedTime() / (float)this.type.getTime()),
                        2.0F, 0.1F, ColorProvider.rgba(255, 0, 0, 255));
                
                // Текст
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), text, x + 14, 5.0F, ColorProvider.rgba(255, 255, 255, 255), 11.0F);
                
                // Предмет
                e.getDrawContext().drawItem(this.type.getItem().getDefaultStack(), x, 3);
                
                matrices.pop();
            }
        }

        void draw3D(EventRender e) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null && mc.player != null && this.type != ObjType.PLAST) {
                MatrixStack matrices = e.getMatrixStack();
                Camera camera = mc.gameRenderer.getCamera();
                Vec3d cameraPos = camera.getPos();
                matrices.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
                
                RenderSystem.enableBlend();
                RenderSystem.disableDepthTest();
                RenderSystem.disableCull();
                RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
                RenderSystem.lineWidth(2.0F);
                RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
                
                BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                int radius = 2;
                int color = ColorProvider.rgba(255, 0, 0, 110);
                
                switch (this.type) {
                    case TRAP:
                        color = ColorProvider.rgba(255, 0, 0, 110);
                        break;
                    case DRAGON_FT:
                    case DRAGON_ST:
                        radius = 4;
                        color = ColorProvider.rgba(255, 255, 0, 150);
                        break;
                }

                BlockPos minPos = this.pos.add(-radius, -radius, -radius);
                BlockPos maxPos = this.pos.add(radius, radius, radius);
                
                drawBox(matrices, buffer, new Box(minPos.getX(), minPos.getY(), minPos.getZ(), 
                        maxPos.getX() + 1, maxPos.getY() + 1, maxPos.getZ() + 1), color);
                
                BuiltBuffer builtBuffer = buffer.endNullable();
                if (builtBuffer != null) {
                    BufferRenderer.drawWithGlobalProgram(builtBuffer);
                }

                RenderSystem.enableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
            }
        }

        public BlockPos getPos() {
            return this.pos;
        }

        public ObjType getType() {
            return this.type;
        }

        public Timer getStart() {
            return this.start;
        }
    }

    static enum ObjType {
        TRAP("Трапка", Items.NETHERITE_SCRAP, 15000L),
        DRAGON_FT("Драконка", Items.NETHERITE_SCRAP, 30000L),
        DRAGON_ST("Драконка", Items.NETHERITE_SCRAP, 60000L),
        PLAST("Пласт", Items.DRIED_KELP, 20000L);

        final String name;
        final Item item;
        final long time;

        ObjType(final String name, final Item item, final long time) {
            this.name = name;
            this.item = item;
            this.time = time;
        }

        public String getName() {
            return this.name;
        }

        public Item getItem() {
            return this.item;
        }

        public long getTime() {
            return this.time;
        }
    }
    
    // Утилиты
    private static Vec2f worldToScreen(Vec3d pos) {
        return RenderUtils.worldToScreen(pos);
    }
    
    private static void drawBox(MatrixStack matrices, BufferBuilder buffer, Box box, int color) {
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = ((color >> 24) & 0xFF) / 255.0F;
        
        // Рисуем линии бокса
        // Нижняя грань
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        
        // Верхняя грань
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
        
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
        
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        
        // Вертикальные линии
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.maxX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
        
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        buffer.vertex(matrices.peek().getPositionMatrix(), (float)box.minX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
    }
}

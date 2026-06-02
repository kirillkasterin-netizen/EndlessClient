package dev.endless.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import dev.endless.Endless;
import dev.endless.event.list.EventHUD;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.list.misc.NameProtect;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.ModeListSetting;
import dev.endless.module.settings.ModeSetting;
import dev.endless.util.friend.FriendRepository;
import dev.endless.util.party.PartyPlayerPos;
import dev.endless.util.party.connection.PartyApiClient;
import dev.endless.util.render.builders.Builder;
import dev.endless.util.render.builders.states.QuadColorState;
import dev.endless.util.render.builders.states.QuadRadiusState;
import dev.endless.util.render.builders.states.SizeState;
import dev.endless.util.render.math.ProjectionUtil;
import dev.endless.util.render.renderers.IRenderer;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.msdf.MsdfFont;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;
import dev.endless.util.replace.ReplaceUtil;
import dev.endless.module.list.render.hud.Interface;
import org.joml.Vector4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInformation(moduleName = "Tags", moduleDesc = "Теги над игроками", moduleCategory = ModuleCategory.RENDER)
public class Tags extends Module {

    private final ModeListSetting entityTypes = new ModeListSetting("Типы",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Предметы", true)
    );

    private final BooleanSetting displayPartyFriends = new BooleanSetting("Участники пати", true);
    private final ModeSetting style = new ModeSetting("Стиль", "Дефолт", "Дефолт", "Nursultan");
    private final BooleanSetting blur = new BooleanSetting("Блюр", true);

    private final Map<UUID, Text> normalizedNames = new ConcurrentHashMap<>();
    private int clearCacheTicker = 0;

    private final List<ItemStack> equipmentCache = new ArrayList<>();

    private final Map<UUID, Float> itemTagWidths = new ConcurrentHashMap<>();
    public static Text normalizeSmallCaps(Text text) {
        String[] from = {"ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ", "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ꜱ", "ᴛ", "ᴜ", "ᴠ", "ᴡ", "x", "ʏ", "ᴢ", "◆", "┃ "};
        String[] to = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "●", ""};
        for (int i = 0; i < from.length; i++) {
            text = ReplaceUtil.replace(text, from[i], to[i]);
        }
        return text;
    }

    public static Text processName(PlayerEntity entity) {
        Text name = entity.getDisplayName();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (Endless.getInstance().getModuleStorage().get(NameProtect.class).isEnabled() && mc.player != null) {
            String scoreName = entity.getNameForScoreboard();
            String myName = mc.player.getNameForScoreboard();
            String customName = Endless.getInstance().getModuleStorage().get(NameProtect.class).getCustomName();

            if (scoreName.equals(myName) || entity.getUuid().equals(mc.player.getUuid())) {
                name = ReplaceUtil.replace(name, myName, customName);
            } else if (Endless.getInstance().getModuleStorage().get(NameProtect.class).hideFriends.getValue() && FriendRepository.isFriend(scoreName)) {
                name = ReplaceUtil.replace(name, scoreName, customName);
            }
        }

        String s = name.getString();
        if (s.contains("ꔲ")) name = ReplaceUtil.replace(name, "ꔲ", "§5BULL");
        if (s.contains("ꕓ")) name = ReplaceUtil.replace(name, "ꕓ", "§8GHOST");
        if (s.contains("ꔨ")) name = ReplaceUtil.replace(name, "ꔨ", "§dDRAGON");
        if (s.contains("ꔂ")) name = ReplaceUtil.replace(name, "ꔂ", "§9D.MODER");
        if (s.contains("ꔦ")) name = ReplaceUtil.replace(name, "ꔦ", "§9D.ML.ADMIN");
        if (s.contains("ꕀ")) name = ReplaceUtil.replace(name, "ꕀ", "§2HYDRA");
        if (s.contains("ꕖ")) name = ReplaceUtil.replace(name, "ꕖ", "§7BUNNY");
        if (s.contains("ꕒ")) name = ReplaceUtil.replace(name, "ꕒ", "§fRABBIT");
        if (s.contains("ꕈ")) name = ReplaceUtil.replace(name, "ꕈ", "§aCOBRA");
        if (s.contains("ꔶ")) name = ReplaceUtil.replace(name, "ꔶ", "§6TIGER");
        if (s.contains("ꕠ")) name = ReplaceUtil.replace(name, "ꕠ", "§eD.HELPER");
        if (s.contains("ꔉ")) name = ReplaceUtil.replace(name, "ꔉ", "§eHELPER");
        if (s.contains("ꔆ")) name = ReplaceUtil.replace(name, "ꔆ", "§7D.MODER");
        if (s.contains("ꕄ")) name = ReplaceUtil.replace(name, "ꕄ", "§4DRACULA");
        if (s.contains("ꔰ")) name = ReplaceUtil.replace(name, "ꔰ", "§7D.ML.ADMIN");
        if (s.contains("ꔐ")) name = ReplaceUtil.replace(name, "ꔐ", "§1D.GL.MODER");
        if (s.contains("ꔔ")) name = ReplaceUtil.replace(name, "ꔔ", "§7D.GL.MODER");
        if (s.contains("ꔢ")) name = ReplaceUtil.replace(name, "ꔢ", "§7D.ST.MODER");
        if (s.contains("ꕡ")) name = ReplaceUtil.replace(name, "ꕡ", "§6ST.HELPER");
        if (s.contains("ꕅ")) name = ReplaceUtil.replace(name, "ꕅ", "§5MEDIA+");
        if (s.contains("ꔗ")) name = ReplaceUtil.replace(name, "ꔗ", "§9MODER");
        if (s.contains("ꕗ")) name = ReplaceUtil.replace(name, "ꕗ", "§4D.ADMIN");
        if (s.contains("ꔘ")) name = ReplaceUtil.replace(name, "ꔘ", "§9D.ST.MODER");
        if (s.contains("ꔳ")) name = ReplaceUtil.replace(name, "ꔳ", "§bML.ADMIN");
        if (s.contains("ꔁ")) name = ReplaceUtil.replace(name, "ꔁ", "§5MEDIA");
        if (s.contains("ꔅ")) name = ReplaceUtil.replace(name, "ꔅ", "§cYT");
        if (s.contains("ꕁ")) name = ReplaceUtil.replace(name, "ꕁ", "§6LEGENDA");

        return normalizeSmallCaps(name);
    }

    private Text processNameInternal(PlayerEntity entity) {
        return processName(entity);
    }

    private Text getNormalizedName(PlayerEntity entity) {
        return normalizedNames.computeIfAbsent(entity.getUuid(), uuid -> processNameInternal(entity));
    }

    @Subscribe
    private void onRender(EventHUD e) {
        if (clearCacheTicker++ > 100) {
            normalizedNames.clear();
            clearCacheTicker = 0;
        }

        if (mc.world == null || mc.player == null) return;

        MsdfFont font = Fonts.SFMEDIUM.get();
        float tickDelta = e.getRenderTickCounter().getTickDelta(true);

        if (entityTypes.isEnabled("Игроки")) {
            renderPlayerTags(font, tickDelta, e);
        }

        if (entityTypes.isEnabled("Предметы")) {
            renderItemTags(font, tickDelta, e);
        }
    }

    private void renderPlayerTags(MsdfFont font, float tickDelta, EventHUD e) {
        List<AbstractClientPlayerEntity> worldPlayers = mc.world.getPlayers();

        for (PlayerEntity entity : worldPlayers) {
            if (entity == mc.player && !mc.getEntityRenderDispatcher().camera.isThirdPerson()) continue;

            double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) + entity.getHeight() + 0.5;
            double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

            Vector2f pos = ProjectionUtil.project(x, y, z);
            if (pos.getX() == Float.MAX_VALUE || pos.getY() == Float.MAX_VALUE) continue;

            final float screenYOffset = 5.7f;
            float posY = pos.getY() - screenYOffset;

            Text baseName = getNormalizedName(entity);

            int currentHp = (int) dev.endless.util.server.Server.getHealth(entity, false);
            int hpColor = 0xFF5555;

            boolean nursultanStyle = style.is("Nursultan");

            MutableText name = baseName.copy();

            ItemStack offHandStack = entity.getOffHandStack();
            if (offHandStack.getItem() == net.minecraft.item.Items.PLAYER_HEAD) {
                name.append(Text.literal(" [").setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                        .append(offHandStack.getName())
                        .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
            }

            name.append(Text.literal(" [").setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                    .append(Text.literal(String.valueOf(currentHp)).setStyle(Style.EMPTY.withColor(hpColor)))
                    .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.GRAY)));

            float textWidth = nursultanStyle ? font.getWidth(name.getString(), 6f) : mc.textRenderer.getWidth(name);
            float paddingX = nursultanStyle ? 3f : 4f;
            float headIconSize = nursultanStyle ? 8f : 0f;
            float headIconPadding = nursultanStyle ? 2f : 0f;
            float totalWidth = nursultanStyle ? textWidth + (paddingX * 2) + headIconSize + headIconPadding : textWidth + (paddingX * 2) - 3;
            float tagHeight = nursultanStyle ? 10.5f : 11.5f;
            float tagY = nursultanStyle ? posY + 4 : posY - 1.5f;

            float defaultTextWidth = mc.textRenderer.getWidth(name);
            float centerX = pos.getX();
            float bgX = nursultanStyle ? centerX - totalWidth / 2.0f : centerX - (defaultTextWidth / 2.0f) - paddingX;
            float bgY = nursultanStyle ? tagY : tagY;

            Interface iface = Endless.getInstance().getModuleStorage().get(Interface.class);
            if (iface != null) {
                Vector4f radii = new Vector4f(0.5f, 0.5f, 0.5f, 0.5f);
                if (blur.getValue()) {
                    int bgColor = ColorProvider.rgba(25, 25, 25, (int) (255 * 0.1f));
                    DrawUtil.drawRoundBlur(bgX, bgY, totalWidth, tagHeight, radii, ColorProvider.rgba(200, 200, 200, 255), 12);
                    DrawUtil.drawRound(bgX, bgY, totalWidth, tagHeight, radii, bgColor);
                } else {
                    int bgColor = ColorProvider.rgba(0, 0, 0, 125);
                    DrawUtil.drawRound(bgX, bgY, totalWidth, tagHeight, radii, bgColor);
                }
            }

            if (nursultanStyle) {

                float headSize = 8f;
                float headX = bgX + 1.5f;
                float headY = tagY + (tagHeight - headSize) / 2f;

                if (entity instanceof AbstractClientPlayerEntity clientPlayer) {
                    net.minecraft.client.texture.AbstractTexture skinTex = mc.getTextureManager().getTexture(clientPlayer.getSkinTextures().texture());
                    int texId = skinTex.getGlId();

                    Builder.texture()
                        .size(new SizeState(headSize, headSize))
                        .radius(new QuadRadiusState(1f))
                        .color(new QuadColorState(0xFFFFFFFF))
                        .texture(0.125f, 0.125f, 0.125f, 0.125f, texId)
                        .build()
                        .render(IRenderer.DEFAULT_MATRIX, headX, headY, 0);
                }

                DrawUtil.drawText(font, name, bgX + paddingX + 0.5f + headIconSize + headIconPadding, posY + 5.25f, 6, 220);
            } else {
                MatrixStack matrices = e.getDrawContext().getMatrices();
                matrices.push();
                matrices.translate(centerX - defaultTextWidth / 2.0f, posY + 0.5f, 0);
                e.getDrawContext().drawText(mc.textRenderer, name, 0, 0, -1, false);
                matrices.pop();
            }

            equipmentCache.clear();
            equipmentCache.add(entity.getEquippedStack(EquipmentSlot.HEAD));
            equipmentCache.add(entity.getEquippedStack(EquipmentSlot.CHEST));
            equipmentCache.add(entity.getEquippedStack(EquipmentSlot.LEGS));
            equipmentCache.add(entity.getEquippedStack(EquipmentSlot.FEET));
            equipmentCache.add(entity.getMainHandStack());
            equipmentCache.add(entity.getOffHandStack());
            equipmentCache.removeIf(ItemStack::isEmpty);

            if (!equipmentCache.isEmpty()) {
                float iconSize = 16;
                float spacing = 0;
                float itemsTotalWidth = equipmentCache.size() * iconSize + (equipmentCache.size() - 1) * spacing;
                float startX = pos.getX() - itemsTotalWidth / 2.0f + 13.5f;
                float iconY = posY - 2;

                MatrixStack matrices = e.getDrawContext().getMatrices();

                for (int i = 0; i < equipmentCache.size(); i++) {
                    ItemStack stack = equipmentCache.get(i);
                    float x2 = startX + i * (iconSize + spacing - 2);
                    float scale = 0.7f;
                    float half = -18;

                    matrices.push();
                    matrices.translate(x2 + half, iconY + half, 0);
                    matrices.scale(scale, scale, 1);
                    e.getDrawContext().drawItem(stack, (int) (-half), (int) (-half));
                    e.getDrawContext().drawStackOverlay(mc.textRenderer, stack, (int) (-half), (int) (-half));
                    matrices.pop();
                }
            }
        }

        if (!displayPartyFriends.getValue()) return;

        for (PartyPlayerPos player : PartyApiClient.getCached()) {
            boolean contains = false;
            for (PlayerEntity playerEntity : worldPlayers) {
                if (playerEntity.getNameForScoreboard().equals(player.playerId())) {
                    contains = true;
                    break;
                }
            }
            if (contains) continue;
            double x = player.x();
            double y = player.y() + 3;
            double z = player.z();

            Vector2f pos = ProjectionUtil.project(x, y, z);
            if (pos.getX() == Float.MAX_VALUE || pos.getY() == Float.MAX_VALUE) continue;

            final float screenYOffset = 5.7f;
            float posY = pos.getY() - screenYOffset;

            String name = player.playerId();

            float textWidth = font.getWidth(name, 8.3f);
            float totalWidth = textWidth + 6;
            float bgX = pos.getX() - totalWidth / 2.0f;

            DrawUtil.drawRoundBlur(bgX, posY - 2, totalWidth, 12.5f, 3, ColorProvider.rgba(0, 0, 0, 90), 8f);

            DrawUtil.drawText(font, name, bgX + 3, posY + 0.25f, -1, 8);
        }
    }
    private void renderItemTags(MsdfFont font, float tickDelta, EventHUD e) {

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) + entity.getHeight() + 0.5;
            double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

            Vector2f pos = ProjectionUtil.project(x, y, z);
            if (pos.getX() == Float.MAX_VALUE || pos.getY() == Float.MAX_VALUE) continue;

            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty()) continue;

            int rarityOrdinal = stack.getRarity().ordinal();
            Formatting rarityColor = switch (rarityOrdinal) {
                case 0 -> Formatting.WHITE;
                case 1 -> Formatting.YELLOW;
                case 2 -> Formatting.AQUA;
                case 3 -> Formatting.LIGHT_PURPLE;
                default -> Formatting.WHITE;
            };

            String itemName = stack.getName().getString();
            Text nameText = Text.literal(itemName).setStyle(Style.EMPTY.withColor(rarityColor));
            if (!stack.getName().getSiblings().isEmpty()) nameText = stack.getName();

            Text countComponent = stack.getCount() > 1
                    ? Text.literal(" [").setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                    .append(Text.literal(String.valueOf(stack.getCount())).setStyle(Style.EMPTY.withColor(Formatting.RED)))
                    .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                    : Text.empty();

            Text textComponent = nameText.copy().append(countComponent);
            Text normalized = normalizeSmallCaps(textComponent);

            boolean nursultanStyle = style.is("Nursultan");
            float paddingX = nursultanStyle ? 3f : 4f;

            float textWidth = nursultanStyle
                    ? font.getWidth(normalized.getString(), 6f)
                    : mc.textRenderer.getWidth(normalized);

            float targetWidth = textWidth + (paddingX * 2);
            float animatedWidth = itemTagWidths.compute(itemEntity.getUuid(), (uuid, prev) -> {
                if (prev == null) return targetWidth;
                return prev + (targetWidth - prev) * 0.15f;
            });

            float bgX = pos.getX() - (animatedWidth / 2.0f);
            float tagHeight = nursultanStyle ? 10.5f : 11.5f;
            float bgY = pos.getY() - 2f;

            Interface iface = Endless.getInstance().getModuleStorage().get(Interface.class);
            if (iface != null) {
                Vector4f radii = new Vector4f(0.5f, 0.5f, 0.5f, 0.5f);
                if (blur.getValue()) {
                    int bgColor = ColorProvider.rgba(25, 25, 25, (int) (255 * 0.1f));
                    DrawUtil.drawRoundBlur(bgX, pos.getY() - 2f, animatedWidth - 1.0f, 11f, radii, ColorProvider.rgba(200, 200, 200, 255), 12);
                    DrawUtil.drawRound(bgX, pos.getY() - 2f, animatedWidth - 1.0f, 11f, radii, bgColor);
                } else {
                    int bgColor = ColorProvider.rgba(0, 0, 0, 125);
                    DrawUtil.drawRound(bgX, pos.getY() - 2f, animatedWidth - 1.0f, 11f, radii, bgColor);
                }
            }

            if (nursultanStyle) {
                DrawUtil.drawText(font, normalized, bgX + paddingX + 1f, pos.getY() - 0.5f, 6, 220);
            } else {
                MatrixStack matrices = e.getDrawContext().getMatrices();
                matrices.push();
                matrices.translate(bgX + paddingX + 1f, pos.getY() - 0.5f, 0);
                e.getDrawContext().drawText(mc.textRenderer, normalized, 0, 0, -1, false);
                matrices.pop();
            }
        }
    }
}

package net.spell_engine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.util.math.MathHelper;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.util.Color;
import net.spell_engine.client.util.Rect;
import net.spell_engine.config.HudConfig;
import net.spell_engine.internals.SpellCasterClient;
import net.spell_engine.internals.SpellHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;

import java.util.List;
import java.util.stream.Collectors;

public class HudRenderHelper {

    public static void render(MatrixStack matrixStack, float tickDelta) {
        render(matrixStack, tickDelta, false);
    }
    public static void render(MatrixStack matrixStack, float tickDelta, boolean config) {
        var hudConfig = SpellEngineClient.hudConfig.value;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        if (player == null && !config) {
            return;
        }

        var targetViewModel = TargetWidget.ViewModel.mock();
        var hotbarViewModel = SpellHotBarWidget.ViewModel.mock();
        CastBarWidget.ViewModel castBarViewModel = null;
        if (config) {
            castBarViewModel = CastBarWidget.ViewModel.mock();
        } else {
            targetViewModel = TargetWidget.ViewModel.from(player);
        }

        if (player != null) {
            var caster = (SpellCasterClient) player;
            var container = caster.getCurrentContainer();
            if (container != null && container.isValid()) {
                var cooldownManager = caster.getCooldownManager();
                var spells = container.spell_ids.stream()
                        .map(spellId -> new SpellHotBarWidget.SpellViewModel(spellIconTexture(new Identifier(spellId)), cooldownManager.getCooldownProgress(new Identifier(spellId), tickDelta)))
                        .collect(Collectors.toList());
                int selected = caster.getSelectedSpellIndex(container);
                hotbarViewModel = new SpellHotBarWidget.ViewModel(spells, selected, Color.from(0xFFFFFF));
            } else {
                hotbarViewModel = SpellHotBarWidget.ViewModel.empty;
            }
            var spell = caster.getCurrentSpell();
            var spellId = caster.getCurrentSpellId();
            if (spell != null) {
                castBarViewModel = new CastBarWidget.ViewModel(
                        spell.school.color(),
                        caster.getCurrentCastProgress(),
                        spell.cast.duration,
                        spellIconTexture(spellId),
                        true,
                        SpellHelper.isChanneled(spell));
            }
        }

        var screenWidth = client.getWindow().getScaledWidth();
        var screenHeight = client.getWindow().getScaledHeight();
        var originPoint = hudConfig.base.origin.getPoint(screenWidth, screenHeight);
        var baseOffset = originPoint.add(hudConfig.base.offset);
        if (castBarViewModel != null) {
            CastBarWidget.render(matrixStack, tickDelta, hudConfig, baseOffset, castBarViewModel);
        }

        if (hudConfig.target.visible) {
            var targetOffset = baseOffset.add(hudConfig.target.offset);
            TargetWidget.render(matrixStack, tickDelta, targetOffset, targetViewModel);
        }

        SpellHotBarWidget.render(matrixStack, screenWidth, screenHeight, hotbarViewModel);
    }

    // Example: `spell_engine:fireball` -> `spell_engine:textures/spell/fireball.png`
    private static Identifier spellIconTexture(Identifier spellId) {
        return new Identifier(spellId.getNamespace(), "textures/spell/" + spellId.getPath() + ".png");
    }

    public static class TargetWidget {
        public static void render(MatrixStack matrixStack, float tickDelta, Vec2f starting, ViewModel viewModel) {
            MinecraftClient client = MinecraftClient.getInstance();
            var textRenderer = client.inGameHud.getTextRenderer();

            int textWidth = textRenderer.getWidth(viewModel.text);

            int x = (int) (starting.x - (textWidth / 2F));
            int y = (int) starting.y;
            int opacity = 255;

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            InGameHud.fill(matrixStack, x - 2, y - 2, x + textWidth + 2, y + textRenderer.fontHeight + 2, client.options.getTextBackgroundColor(0));
            textRenderer.drawWithShadow(matrixStack, viewModel.text, x, y, 0xFFFFFF);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        public record ViewModel(String text) {
            public static ViewModel mock() {
                return new ViewModel("Target name");
            }

            public static ViewModel from(ClientPlayerEntity player) {
                var caster = (SpellCasterClient)player;
                var target = caster.getCurrentFirstTarget();
                var text = "";
                if (target != null
                        && (SpellEngineClient.config.showTargetNameWhenMultiple || caster.getCurrentTargets().size() == 1)) {
                    text = target.getName().getString();
                }
                return new ViewModel(text);
            }
        }
    }

    public static class CastBarWidget {
        public static Rect lastRendered;
        private static final float tailWidth = 5;
        public static final float minWidth = 2 * tailWidth;
        private static final int textureWidth = 182;
        private static final int textureHeight = 10;
        private static final int barHeight = textureHeight / 2;
        private static final Identifier CAST_BAR = new Identifier(SpellEngineMod.ID, "textures/hud/castbar.png");
        private static final int spellIconSize = 16;

        public record ViewModel(int color, float progress, float castDuration, Identifier iconTexture, boolean allowTickDelta, boolean reverse) {
            public static ViewModel mock() {
                return new ViewModel(0xFF3300, 0.5F, 1, spellIconTexture(new Identifier("spell_engine", "fireball")), false, false);
            }
        }

        public static void render(MatrixStack matrixStack, float tickDelta, HudConfig hudConfig, Vec2f starting, ViewModel viewModel) {
            var barWidth = hudConfig.bar_width;
            var totalWidth = barWidth + minWidth;
            var totalHeight = barHeight;
            int x = (int) (starting.x - (totalWidth / 2));
            int y = (int) (starting.y - (totalHeight / 2));
            lastRendered = new Rect(new Vec2f(x,y), new Vec2f(x + totalWidth,y + totalHeight));

            RenderSystem.enableBlend();
            RenderSystem.setShaderTexture(0, CAST_BAR);

            float red = ((float) ((viewModel.color >> 16) & 0xFF)) / 255F;
            float green = ((float) ((viewModel.color >> 8) & 0xFF)) / 255F;
            float blue = ((float) (viewModel.color & 0xFF)) / 255F;

            RenderSystem.setShaderColor(red, green, blue, 1F);

            renderBar(matrixStack, barWidth, true, 1, x, y);
            float partialProgress = 0;
            if (viewModel.allowTickDelta && viewModel.castDuration > 0) {
                partialProgress = tickDelta / (viewModel.castDuration * 20F);
            }
            var progress = viewModel.reverse() ? (1F - viewModel.progress - partialProgress) : (viewModel.progress + partialProgress);
            renderBar(matrixStack, barWidth, false, progress, x, y);

            if (hudConfig.icon.visible && viewModel.iconTexture != null) {
                x = (int) (starting.x + hudConfig.icon.offset.x);
                y = (int) (starting.y + hudConfig.icon.offset.y);
                RenderSystem.setShaderTexture(0, viewModel.iconTexture);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                DrawableHelper.drawTexture(matrixStack, x, y, 0, 0, spellIconSize, spellIconSize, spellIconSize, spellIconSize);
            }

            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        private static void renderBar(MatrixStack matrixStack, int barWidth, boolean isBackground, float progress, int x, int y) {
            var totalWidth = barWidth + minWidth;
            var centerWidth = totalWidth - minWidth;
            float leftRenderBegin = 0;
            float centerRenderBegin = tailWidth;
            float rightRenderBegin = totalWidth - tailWidth;

            renderBarPart(matrixStack, isBackground, PART.LEFT, progress, leftRenderBegin, tailWidth, x, y, totalWidth);
            renderBarPart(matrixStack, isBackground, PART.CENTER, progress, centerRenderBegin, centerRenderBegin + centerWidth, x, y, totalWidth);
            renderBarPart(matrixStack, isBackground, PART.RIGHT, progress, rightRenderBegin, totalWidth, x, y, totalWidth);
        }

        enum PART { LEFT, CENTER, RIGHT }
        private static void renderBarPart(MatrixStack matrixStack, boolean isBackground, PART part, float progress, float renderBegin, float renderEnd, int x, int y, float totalWidth) {
            var u = 0;
            var partMaxWidth = renderEnd - renderBegin; //5
            var progressRange = (renderEnd - renderBegin) / totalWidth; //0.05
            var progressFloor = (renderBegin / totalWidth); // 0
            var adjustedProgress = Math.min(Math.max((progress - progressFloor), 0), progressRange) / progressRange;
            var width = Math.round(adjustedProgress * partMaxWidth);
            switch (part) {
                case LEFT -> {
                    u = 0;
                    // System.out.println(" partMaxWidth: " + partMaxWidth + " progressRange: " + progressRange + " progressFloor: " + progressFloor + " adjustedProgress: " + adjustedProgress + " width: " + width);
//                    RenderSystem.setShaderColor(1.F, 0F, 0F, 0.5F);
                }
                case CENTER -> {
                    u = (int) tailWidth;
//                    RenderSystem.setShaderColor(0.F, 1F, 0F, 0.5F);
                }
                case RIGHT -> {
                    u = (int) (textureWidth - tailWidth);
//                    RenderSystem.setShaderColor(0.F, 0F, 1F, 0.5F);
                }
            }
            int v = isBackground ? 0 : barHeight;
            DrawableHelper.drawTexture(matrixStack, (int) (x + renderBegin), y, u, v, width, barHeight, textureWidth, textureHeight);
        }
    }

    public class SpellHotBarWidget {
        public static Rect lastRendered;
        private static final Identifier WIDGETS_TEXTURE = new Identifier("textures/gui/widgets.png");
        private static final int textureWidth = 256;
        private static final int textureHeight = 256;

        public record SpellViewModel(Identifier iconId, float cooldown) { }

        public record ViewModel(List<SpellViewModel> spells, int selected, Color sliderColor) {
            public static ViewModel mock() {
                return new ViewModel(
                        List.of(
                                new SpellViewModel(spellIconTexture(new Identifier(SpellEngineMod.ID, "fireball")), 0),
                                new SpellViewModel(spellIconTexture(new Identifier(SpellEngineMod.ID, "fireball")), 0),
                                new SpellViewModel(spellIconTexture(new Identifier(SpellEngineMod.ID, "fireball")), 0)
                        ),
                        1,
                        Color.from(0xFFFFFF)
                );
            }

            public static final ViewModel empty = new ViewModel(List.of(), 0, Color.from(0xFFFFFF));
        }

        public static void render(MatrixStack matrixStack, int screenWidth, int screenHeight, ViewModel viewModel) {
            var config = SpellEngineClient.hudConfig.value.hotbar;
            if (viewModel.spells.isEmpty()) {
                return;
            }
            int slotHeight = 22;
            int slotWidth = 20;
            float estimatedWidth = slotWidth * viewModel.spells.size();
            float estimatedHeight = slotHeight;
            var origin = config.origin
                    .getPoint(screenWidth, screenHeight)
                    .add(config.offset)
                    .add(new Vec2f(estimatedWidth * (-0.5F), estimatedHeight * (-0.5F))); // Grow from center
            lastRendered = new Rect(origin, origin.add(new Vec2f(estimatedWidth, estimatedHeight)));

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // Background
            RenderSystem.setShaderTexture(0, WIDGETS_TEXTURE);
            DrawableHelper.drawTexture(matrixStack, (int) (origin.x), (int) (origin.y), 0, 0, slotWidth / 2, slotHeight, textureWidth, textureHeight);
            int middleElements = viewModel.spells.size() - 1;
            for (int i = 0; i < middleElements; i++) {
                DrawableHelper.drawTexture(matrixStack, (int) (origin.x) + (slotWidth / 2) + (i * slotWidth), (int) (origin.y), slotWidth / 2, 0, slotWidth, slotHeight, textureWidth, textureHeight);
            }
            DrawableHelper.drawTexture(matrixStack, (int) (origin.x) + (slotWidth / 2) + (middleElements * slotWidth), (int) (origin.y), 170, 0, (slotHeight / 2) + 1, slotHeight, textureWidth, textureHeight);

            // Icons
            var iconsOffset = new Vec2f(3,3);
            int iconSize = 16;
            for (int i = 0; i < viewModel.spells.size(); i++) {
                var spell = viewModel.spells.get(i);
                RenderSystem.setShaderTexture(0, spell.iconId);
                int x = (int) (origin.x + iconsOffset.x) + ((slotWidth) * i);
                int y = (int) (origin.y + iconsOffset.y);
                DrawableHelper.drawTexture(matrixStack, x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);

                if (spell.cooldown > 0) {
                    renderCooldown(spell.cooldown, x, y);
                }
            }

            // Selector
            int selectorSize = 24;
            RenderSystem.setShaderTexture(0, WIDGETS_TEXTURE);
            int x = ((int) origin.x) - 1 + (slotWidth * viewModel.selected);
            int y = ((int) origin.y) - 1;
            DrawableHelper.drawTexture(matrixStack, x, y, 0, 22, selectorSize, selectorSize, textureWidth, textureHeight);


            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        private static void renderCooldown(float progress, int x, int y) {
            RenderSystem.disableDepthTest();
            RenderSystem.disableTexture();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Tessellator tessellator2 = Tessellator.getInstance();
            BufferBuilder bufferBuilder2 = tessellator2.getBuffer();
            renderGuiQuad(bufferBuilder2, x, y + MathHelper.floor(16.0f * (1.0f - progress)), 16, MathHelper.ceil(16.0f * progress), 255, 255, 255, 127);
            RenderSystem.enableTexture();
            RenderSystem.enableDepthTest();
        }

        private static void renderGuiQuad(BufferBuilder buffer, int x, int y, int width, int height, int red, int green, int blue, int alpha) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(x + 0, y + 0, 0.0).color(red, green, blue, alpha).next();
            buffer.vertex(x + 0, y + height, 0.0).color(red, green, blue, alpha).next();
            buffer.vertex(x + width, y + height, 0.0).color(red, green, blue, alpha).next();
            buffer.vertex(x + width, y + 0, 0.0).color(red, green, blue, alpha).next();
            BufferRenderer.drawWithShader(buffer.end());
        }
    }
}

package net.spell_engine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.InputHelper;
import net.spell_engine.client.util.Color;
import net.spell_engine.client.util.Rect;
import net.spell_engine.client.util.SpellRender;
import net.spell_engine.client.util.TextureFile;
import net.spell_engine.config.HudConfig;
import net.spell_engine.internals.SpellCasterClient;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;

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

        var clientConfig = SpellEngineClient.config;

        var targetViewModel = TargetWidget.ViewModel.mock();
        boolean renderHotbar = true;
        var hotbarViewModel = SpellHotBarWidget.ViewModel.mock();
        var errorViewModel = ErrorMessageWidget.ViewModel.mock();
        SpellHotBarWidget.ViewModel hotbarAccessories = null;
        CastBarWidget.ViewModel castBarViewModel = null;
        if (config) {
            castBarViewModel = CastBarWidget.ViewModel.mock();
        } else {
            targetViewModel = TargetWidget.ViewModel.from(player);
        }

        if (player != null) {
            var caster = (SpellCasterClient) player;
            var container = caster.getCurrentContainer();
            var currentSpell = caster.getCurrentSpell();
            var currentSpellId = caster.getCurrentSpellId();

            if (container != null && container.isUsable()) {
                var cooldownManager = caster.getCooldownManager();

                var spells = container.spell_ids.stream()
                        .map(id -> {
                            var spellId = new Identifier(id);
                            var spell = SpellRegistry.getSpell(spellId);
                            return new SpellHotBarWidget.SpellViewModel(
                                SpellRender.iconTexture(spellId),
                                cooldownManager.getCooldownProgress(new Identifier(id), tickDelta),
                                Color.from(spell != null ? spell.school.color() : 0xFFFFFF));
                        })
                        .collect(Collectors.toList());
                int selected = caster.getSelectedSpellIndex(container);

                if (clientConfig.collapseSpellHotbar && !InputHelper.isLocked && selected < spells.size()) {
                    hotbarAccessories = new SpellHotBarWidget.ViewModel(spells, selected, Color.from(0xFFFFFF));
                    spells = List.of(spells.get(selected));
                    selected = 0;
                }

                hotbarViewModel = new SpellHotBarWidget.ViewModel(spells, selected, Color.from(0xFFFFFF));
            } else {
                hotbarViewModel = SpellHotBarWidget.ViewModel.empty;
            }
            renderHotbar = InputHelper.hotbarVisibility().spell();

            if (currentSpell != null) {
                castBarViewModel = new CastBarWidget.ViewModel(
                        currentSpell.school.color(),
                        caster.getCurrentCastProgress(),
                        currentSpell.cast.duration,
                        SpellRender.iconTexture(currentSpellId),
                        true,
                        SpellHelper.isChanneled(currentSpell));
            }

            if (!config) {
                var hudMessages = HudMessages.INSTANCE;
                var error = hudMessages.currentError();
                if (error != null && error.durationLeft > 0) {
                    errorViewModel = ErrorMessageWidget.ViewModel.from(error.message, error.durationLeft, error.fadeOut, tickDelta);
                } else {
                    errorViewModel = null;
                }
            }
        }

        var screenWidth = client.getWindow().getScaledWidth();
        var screenHeight = client.getWindow().getScaledHeight();
        var originPoint = hudConfig.castbar.base.origin.getPoint(screenWidth, screenHeight);
        var baseOffset = originPoint.add(hudConfig.castbar.base.offset);
        if (castBarViewModel != null) {
            CastBarWidget.render(matrixStack, tickDelta, hudConfig, baseOffset, castBarViewModel);
        }

        if (hudConfig.castbar.target.visible) {
            var targetOffset = baseOffset.add(hudConfig.castbar.target.offset);
            TargetWidget.render(matrixStack, tickDelta, targetOffset, targetViewModel);
        }

        if (renderHotbar || config) {
            if (config && (hotbarViewModel == null || hotbarViewModel.isEmpty())) {
                hotbarViewModel = SpellHotBarWidget.ViewModel.mock();
            }
            SpellHotBarWidget.render(matrixStack, screenWidth, screenHeight, hotbarViewModel);
            if(clientConfig.collapsedIndicators && hotbarAccessories != null) {
                SpellHotBarWidget.renderAccessories(matrixStack, screenWidth, screenHeight, hotbarAccessories);
            }
        }

        if (errorViewModel != null) {
            ErrorMessageWidget.render(matrixStack, hudConfig, screenWidth, screenHeight, errorViewModel);
        }
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
                        && (/* SpellEngineClient.config.showTargetNameWhenMultiple || */ caster.getCurrentTargets().size() == 1)) {
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
                return new ViewModel(0xFF3300, 0.5F, 1, SpellRender.iconTexture(new Identifier("spell_engine", "dummy_spell")), false, false);
            }
        }

        public static void render(MatrixStack matrixStack, float tickDelta, HudConfig hudConfig, Vec2f starting, ViewModel viewModel) {
            var barWidth = hudConfig.castbar.width;
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

            if (hudConfig.castbar.icon.visible && viewModel.iconTexture != null) {
                x = (int) (starting.x + hudConfig.castbar.icon.offset.x);
                y = (int) (starting.y + hudConfig.castbar.icon.offset.y);
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
        private static final TextureFile WIDGETS = new TextureFile(new Identifier("textures/gui/widgets.png"), 256, 256);
        private static final TextureFile ACCESSORIES = new TextureFile(new Identifier(SpellEngineMod.ID, "textures/hud/hotbar_accessories.png"), 32, 16);
        private static final int slotHeight = 22;
        private static final int slotWidth = 20;


        public record SpellViewModel(Identifier iconId, float cooldown, Color color) { }

        public record ViewModel(List<SpellViewModel> spells, int selected, Color sliderColor) {
            public static ViewModel mock() {
                return new ViewModel(
                        List.of(
                                new SpellViewModel(SpellRender.iconTexture(new Identifier(SpellEngineMod.ID, "dummy_spell")), 0, Color.RED),
                                new SpellViewModel(SpellRender.iconTexture(new Identifier(SpellEngineMod.ID, "dummy_spell")), 0, Color.RED),
                                new SpellViewModel(SpellRender.iconTexture(new Identifier(SpellEngineMod.ID, "dummy_spell")), 0, Color.RED)
                        ),
                        1,
                        Color.from(0xFFFFFF)
                );
            }

            public static final ViewModel empty = new ViewModel(List.of(), 0, Color.from(0xFFFFFF));

            public boolean isEmpty() {
                return spells.isEmpty();
            }
        }

        public static void render(MatrixStack matrixStack, int screenWidth, int screenHeight, ViewModel viewModel) {
            var config = SpellEngineClient.hudConfig.value.hotbar;
            if (viewModel.spells.isEmpty()) {
                return;
            }
            float estimatedWidth = slotWidth * viewModel.spells.size();
            float estimatedHeight = slotHeight;
            var origin = config.origin
                    .getPoint(screenWidth, screenHeight)
                    .add(config.offset)
                    .add(new Vec2f(estimatedWidth * (-0.5F), estimatedHeight * (-0.5F))); // Grow from center
            lastRendered = new Rect(origin, origin.add(new Vec2f(estimatedWidth, estimatedHeight)));

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            float barOpacity = (SpellEngineClient.config.indicateActiveHotbar && InputHelper.isLocked) ? 1F : 0.5F;

            // Background
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, barOpacity);
            RenderSystem.setShaderTexture(0, WIDGETS.id());
            DrawableHelper.drawTexture(matrixStack, (int) (origin.x), (int) (origin.y), 0, 0, slotWidth / 2, slotHeight, WIDGETS.width(), WIDGETS.height());
            int middleElements = viewModel.spells.size() - 1;
            for (int i = 0; i < middleElements; i++) {
                DrawableHelper.drawTexture(matrixStack, (int) (origin.x) + (slotWidth / 2) + (i * slotWidth), (int) (origin.y), slotWidth / 2, 0, slotWidth, slotHeight, WIDGETS.width(), WIDGETS.height());
            }
            DrawableHelper.drawTexture(matrixStack, (int) (origin.x) + (slotWidth / 2) + (middleElements * slotWidth), (int) (origin.y), 170, 0, (slotHeight / 2) + 1, slotHeight, WIDGETS.width(), WIDGETS.height());

            // Icons
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0F);
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
            if (viewModel.spells.size() > 1) {
                int selectorSize = 24;
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, barOpacity);
                RenderSystem.setShaderTexture(0, WIDGETS.id());
                int x = ((int) origin.x) - 1 + (slotWidth * viewModel.selected);
                int y = ((int) origin.y) - 1;
                DrawableHelper.drawTexture(matrixStack, x, y, 0, 22, selectorSize, selectorSize, WIDGETS.width(), WIDGETS.height());
            }

            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        public static void renderAccessories(MatrixStack matrixStack, int screenWidth, int screenHeight, ViewModel viewModel) {
            if (viewModel.spells.size() < 2) {
                return;
            }

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            var config = SpellEngineClient.hudConfig.value.hotbar;
            var origin = config.origin
                    .getPoint(screenWidth, screenHeight)
                    .add(config.offset);

            float barOpacity = (SpellEngineClient.config.indicateActiveHotbar && InputHelper.isLocked) ? 1F : 0.5F;
            RenderSystem.setShaderTexture(0, ACCESSORIES.id());

            var spacing = 7;
            for (int i = 0; i < viewModel.spells.size(); i++) {
                if (i == viewModel.selected) { continue; }
                var spell = viewModel.spells.get(i);
                var position = i - viewModel.selected;
                int x = (int) (origin.x)
                            + ( ((i < viewModel.selected) ? -1 : 1) * ((slotWidth-4) / 2) )
                            - 7
                            + (position * spacing);
                int y = (int) origin.y - 8;

                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, barOpacity);
                DrawableHelper.drawTexture(matrixStack, x, y, 0, 0, 16, 16, ACCESSORIES.width(), ACCESSORIES.height());
                RenderSystem.setShaderColor(spell.color().red(), spell.color().green(), spell.color().blue(), 1F);
                DrawableHelper.drawTexture(matrixStack, x, y, 16, 0, 16, 16, ACCESSORIES.width(), ACCESSORIES.height());
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1F);
            RenderSystem.disableBlend();
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

    public static class ErrorMessageWidget {
        public static Rect lastRendered;

        public record ViewModel(Text message, float opacity) {
            public static ViewModel mock() {
                return new ViewModel(Text.literal("Error Message!").formatted(Formatting.RED), 1F);
            }

            public static ViewModel from(Text message, int durationLeft, int fadeOut, float tickDelta) {
                float tick = ((float)durationLeft) - tickDelta;
                float opacity = tick > fadeOut ? 1F : (tick / fadeOut);
                return new ViewModel(message, opacity);
            }
        }

        public static void render(MatrixStack matrixStack, HudConfig hudConfig, int screenWidth, int screenHeight, ViewModel viewModel) {
            int alpha = (int) (viewModel.opacity * 255);
            if (alpha < 10) { return; }
            // System.out.println("Rendering opacity: " + viewModel.opacity + " alpha: " + alpha);
            MinecraftClient client = MinecraftClient.getInstance();
            var textRenderer = client.inGameHud.getTextRenderer();
            int textWidth = textRenderer.getWidth(viewModel.message);
            int textHeight = textRenderer.fontHeight;
            var config = hudConfig.error_message;
            var origin = config.origin
                    .getPoint(screenWidth, screenHeight)
                    .add(config.offset);

            int x = (int) (origin.x - (textWidth / 2F));
            int y = (int) origin.y;
            lastRendered = new Rect(new Vec2f(x ,y), new Vec2f(x + textWidth,y + textHeight));
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            InGameHud.fill(matrixStack, x - 2, y - 2, x + textWidth + 2, y + textRenderer.fontHeight + 2, client.options.getTextBackgroundColor(0));
            textRenderer.drawWithShadow(matrixStack, viewModel.message(), x, y, 0xFFFFFF + (alpha << 24)); // color is ARGB
            RenderSystem.disableBlend();
        }
    }
}

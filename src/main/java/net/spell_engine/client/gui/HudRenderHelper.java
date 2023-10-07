package net.spell_engine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.client.util.Rect;
import net.spell_engine.client.util.SpellRender;
import net.spell_engine.client.util.TextureFile;
import net.spell_engine.config.HudConfig;
import net.spell_engine.internals.casting.SpellCasterClient;
import net.spell_engine.internals.SpellHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HudRenderHelper {

    public static void render(DrawContext context, float tickDelta) {
        render(context, tickDelta, false);
    }
    public static void render(DrawContext context, float tickDelta, boolean config) {
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

            if (SpellHotbar.INSTANCE.slots.isEmpty()) {
                hotbarViewModel = SpellHotBarWidget.ViewModel.empty;
            } else {
                var cooldownManager = caster.getCooldownManager();
                var spells = SpellHotbar.INSTANCE.slots.stream().map(slot -> {
                    var info = slot.spell();
                    return new SpellHotBarWidget.SpellViewModel(
                            SpellRender.iconTexture(info.id()),
                            cooldownManager.getCooldownProgress(new Identifier(info.id().toString()), tickDelta),
                            SpellHotBarWidget.KeyBindingViewModel.from(slot.getKeyBinding(client.options)));
                }).collect(Collectors.toList());
                hotbarViewModel = new SpellHotBarWidget.ViewModel(spells);
            }
            renderHotbar = true;

            var spellCast = caster.getSpellCastProgress();
            if (spellCast != null) {
                castBarViewModel = new CastBarWidget.ViewModel(
                        spellCast.process().spell().school.color(),
                        spellCast.ratio(),
                        spellCast.process().length(),
                        SpellRender.iconTexture(spellCast.process().id()),
                        true,
                        SpellHelper.isChanneled(spellCast.process().spell())
                );
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
            CastBarWidget.render(context, tickDelta, hudConfig, baseOffset, castBarViewModel);
        }

        if (hudConfig.castbar.target.visible) {
            var targetOffset = baseOffset.add(hudConfig.castbar.target.offset);
            TargetWidget.render(context, tickDelta, targetOffset, targetViewModel);
        }

        if (renderHotbar || config) {
            if (config && (hotbarViewModel == null || hotbarViewModel.isEmpty())) {
                hotbarViewModel = SpellHotBarWidget.ViewModel.mock();
            }
            SpellHotBarWidget.render(context, screenWidth, screenHeight, hotbarViewModel);
//            if(clientConfig.collapsedIndicators && hotbarAccessories != null) {
//                SpellHotBarWidget.renderAccessories(context, screenWidth, screenHeight, hotbarAccessories);
//            }
        }

        if (errorViewModel != null) {
            ErrorMessageWidget.render(context, hudConfig, screenWidth, screenHeight, errorViewModel);
        }
    }

    public static class TargetWidget {
        public static void render(DrawContext context, float tickDelta, Vec2f starting, ViewModel viewModel) {
            MinecraftClient client = MinecraftClient.getInstance();
            var textRenderer = client.inGameHud.getTextRenderer();

            int textWidth = textRenderer.getWidth(viewModel.text);

            int x = (int) (starting.x - (textWidth / 2F));
            int y = (int) starting.y;
            int opacity = 255;

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.fill(x - 2, y - 2, x + textWidth + 2, y + textRenderer.fontHeight + 2, client.options.getTextBackgroundColor(0));
            context.drawTextWithShadow(textRenderer, viewModel.text, x, y, 0xFFFFFF);
            RenderSystem.disableBlend();
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
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

        public static void render(DrawContext context, float tickDelta, HudConfig hudConfig, Vec2f starting, ViewModel viewModel) {
            var barWidth = hudConfig.castbar.width;
            var totalWidth = barWidth + minWidth;
            var totalHeight = barHeight;
            int x = (int) (starting.x - (totalWidth / 2));
            int y = (int) (starting.y - (totalHeight / 2));
            lastRendered = new Rect(new Vec2f(x,y), new Vec2f(x + totalWidth,y + totalHeight));

            RenderSystem.enableBlend();

            float red = ((float) ((viewModel.color >> 16) & 0xFF)) / 255F;
            float green = ((float) ((viewModel.color >> 8) & 0xFF)) / 255F;
            float blue = ((float) (viewModel.color & 0xFF)) / 255F;

            context.setShaderColor(red, green, blue, 1F);

            renderBar(context, barWidth, true, 1, x, y);
            float partialProgress = 0;
            if (viewModel.allowTickDelta && viewModel.castDuration > 0) {
                partialProgress = tickDelta / viewModel.castDuration;
            }
            var progress = viewModel.reverse() ? (1F - viewModel.progress - partialProgress) : (viewModel.progress + partialProgress);
            renderBar(context, barWidth, false, progress, x, y);

            if (hudConfig.castbar.icon.visible && viewModel.iconTexture != null) {
                x = (int) (starting.x + hudConfig.castbar.icon.offset.x);
                y = (int) (starting.y + hudConfig.castbar.icon.offset.y);
                context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                context.drawTexture(viewModel.iconTexture, x, y, 0, 0, spellIconSize, spellIconSize, spellIconSize, spellIconSize);
            }

            RenderSystem.disableBlend();
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        private static void renderBar(DrawContext context, int barWidth, boolean isBackground, float progress, int x, int y) {
            var totalWidth = barWidth + minWidth;
            var centerWidth = totalWidth - minWidth;
            float leftRenderBegin = 0;
            float centerRenderBegin = tailWidth;
            float rightRenderBegin = totalWidth - tailWidth;

            renderBarPart(context, isBackground, PART.LEFT, progress, leftRenderBegin, tailWidth, x, y, totalWidth);
            renderBarPart(context, isBackground, PART.CENTER, progress, centerRenderBegin, centerRenderBegin + centerWidth, x, y, totalWidth);
            renderBarPart(context, isBackground, PART.RIGHT, progress, rightRenderBegin, totalWidth, x, y, totalWidth);
        }

        enum PART { LEFT, CENTER, RIGHT }
        private static void renderBarPart(DrawContext context, boolean isBackground, PART part, float progress, float renderBegin, float renderEnd, int x, int y, float totalWidth) {
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
//                    context.setShaderColor(1.F, 0F, 0F, 0.5F);
                }
                case CENTER -> {
                    u = (int) tailWidth;
//                    context.setShaderColor(0.F, 1F, 0F, 0.5F);
                }
                case RIGHT -> {
                    u = (int) (textureWidth - tailWidth);
//                    context.setShaderColor(0.F, 0F, 1F, 0.5F);
                }
            }
            int v = isBackground ? 0 : barHeight;
            context.drawTexture(CAST_BAR, (int) (x + renderBegin), y, u, v, width, barHeight, textureWidth, textureHeight);
            // DrawableHelper.drawTexture(matrixStack, (int) (x + renderBegin), y, u, v, width, barHeight, textureWidth, textureHeight);
        }
    }

    public class SpellHotBarWidget {
        public static Rect lastRendered;
        private static final TextureFile WIDGETS = new TextureFile(new Identifier("textures/gui/widgets.png"), 256, 256);
        private static final TextureFile ACCESSORIES = new TextureFile(new Identifier(SpellEngineMod.ID, "textures/hud/hotbar_accessories.png"), 32, 16);
        private static final int slotHeight = 22;
        private static final int slotWidth = 20;

        public record KeyBindingViewModel(String label) {
            public static KeyBindingViewModel from(@Nullable KeyBinding keyBinding) {
                if (keyBinding == null) {
                    return new KeyBindingViewModel("");
                }
                var label = keyBinding.getBoundKeyLocalizedText()
                        .getString()
                        .toUpperCase(Locale.US);
                label = acronym(label, 3);
                return new KeyBindingViewModel(label);
            }
        }

        private static String acronym(String phrase, int maxLength) {
            StringBuilder result = new StringBuilder();
            for (String token : phrase.split("\\s+")) {
                result.append(token.toUpperCase().charAt(0));
            }
            var resultString = result.toString();
            // Make the result at most 3 characters long
            if (resultString.length() > maxLength) {
                resultString = resultString.substring(0, maxLength);
            }
            return result.toString();
        }

        public record SpellViewModel(Identifier iconId, float cooldown, KeyBindingViewModel keybinding) { }

        public record ViewModel(List<SpellViewModel> spells) {
            public static ViewModel mock() {
                return new ViewModel(
                        List.of(
                                new SpellViewModel(SpellRender.iconTexture(new Identifier(SpellEngineMod.ID, "dummy_spell")), 0, new KeyBindingViewModel("1")),
                                new SpellViewModel(SpellRender.iconTexture(new Identifier(SpellEngineMod.ID, "dummy_spell")), 0, new KeyBindingViewModel("2")),
                                new SpellViewModel(SpellRender.iconTexture(new Identifier(SpellEngineMod.ID, "dummy_spell")), 0, new KeyBindingViewModel("3"))
                        )
                );
            }

            public static final ViewModel empty = new ViewModel(List.of());

            public boolean isEmpty() {
                return spells.isEmpty();
            }
        }

        public static void render(DrawContext context, int screenWidth, int screenHeight, ViewModel viewModel) {
            var config = SpellEngineClient.hudConfig.value.hotbar;
            MinecraftClient client = MinecraftClient.getInstance();
            var textRenderer = client.inGameHud.getTextRenderer();
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

            // float barOpacity = (SpellEngineClient.config.indicateActiveHotbar && InputHelper.isLocked) ? 1F : 0.5F;
            float barOpacity = 1F;

            // Background
            context.setShaderColor(1.0f, 1.0f, 1.0f, barOpacity);
            context.drawTexture(WIDGETS.id(), (int) (origin.x), (int) (origin.y), 0, 0, slotWidth / 2, slotHeight, WIDGETS.width(), WIDGETS.height());
            int middleElements = viewModel.spells.size() - 1;
            for (int i = 0; i < middleElements; i++) {
                context.drawTexture(WIDGETS.id(), (int) (origin.x) + (slotWidth / 2) + (i * slotWidth), (int) (origin.y), slotWidth / 2, 0, slotWidth, slotHeight, WIDGETS.width(), WIDGETS.height());
            }
            context.drawTexture(WIDGETS.id(), (int) (origin.x) + (slotWidth / 2) + (middleElements * slotWidth), (int) (origin.y), 170, 0, (slotHeight / 2) + 1, slotHeight, WIDGETS.width(), WIDGETS.height());

            // Icons
            context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0F);
            var iconsOffset = new Vec2f(3,3);
            int iconSize = 16;
            for (int i = 0; i < viewModel.spells.size(); i++) {
                var spell = viewModel.spells.get(i);
                int x = (int) (origin.x + iconsOffset.x) + ((slotWidth) * i);
                int y = (int) (origin.y + iconsOffset.y);

                // Keybinding
                if (spell.keybinding() != null) {
                    context.drawCenteredTextWithShadow(textRenderer, spell.keybinding().label, x + (iconSize / 2), (int)origin.y - 8, 0xFFFFFF);
                }

                RenderSystem.enableBlend();

                try {
                    var slot = SpellHotbar.INSTANCE.slots.get(i);
                    if (slot.spell().spell().mode == Spell.Mode.BYPASS_TO_ITEM_USE) {
                        context.drawItem(SpellHotbar.expectedUseStack(client.player), x, y);
                    } else {
                        context.drawTexture(spell.iconId, x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);
                    }
                } catch (Exception e) { }

                // Icon
                // context.drawTexture(spell.iconId, x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);

                // Cooldown
                if (spell.cooldown > 0) {
                    renderCooldown(context, spell.cooldown, x, y);
                }
            }

//            // Selector
//            if (viewModel.spells.size() > 1) {
//                int selectorSize = 24;
//                context.setShaderColor(1.0f, 1.0f, 1.0f, barOpacity);
//                int x = ((int) origin.x) - 1 + (slotWidth * viewModel.selected);
//                int y = ((int) origin.y) - 1;
//                context.drawTexture(WIDGETS.id(), x, y, 0, 22, selectorSize, selectorSize, WIDGETS.width(), WIDGETS.height());
//            }

            RenderSystem.disableBlend();
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        private static void renderCooldown(DrawContext context, float progress, int x, int y) {
            // Copied from DrawContext.drawItemInSlot
            var k = y + MathHelper.floor(16.0F * (1.0F - progress));
            var l = k + MathHelper.ceil(16.0F * progress);
            context.fill(RenderLayer.getGuiOverlay(), x, k, x + 16, l, Integer.MAX_VALUE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
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

        public static void render(DrawContext context, HudConfig hudConfig, int screenWidth, int screenHeight, ViewModel viewModel) {
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
            context.fill(x - 2, y - 2, x + textWidth + 2, y + textRenderer.fontHeight + 2, client.options.getTextBackgroundColor(0));
            context.drawTextWithShadow(textRenderer, viewModel.message(), x, y, 0xFFFFFF + (alpha << 24)); // color is ARGB
            RenderSystem.disableBlend();
        }
    }
}

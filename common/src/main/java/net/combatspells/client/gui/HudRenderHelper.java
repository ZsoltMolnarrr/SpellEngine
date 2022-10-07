package net.combatspells.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.combatspells.CombatSpells;
import net.combatspells.client.CombatSpellsClient;
import net.combatspells.config.HudConfig;
import net.combatspells.internals.SpellCasterClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;

public class HudRenderHelper {

    public static void render(MatrixStack matrixStack, float tickDelta) {
        render(matrixStack, tickDelta, false);
    }
    public static void render(MatrixStack matrixStack, float tickDelta, boolean config) {
        var hudConfig = CombatSpellsClient.hudConfig.currentConfig;
        var clientConfig = CombatSpellsClient.config;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        if (player == null && !config) {
            return;
        }

        var targetViewModel = TargetWidget.ViewModel.mock();
        CastBarWidget.ViewModel castBarViewModel = null;
        if (config) {
            castBarViewModel = CastBarWidget.ViewModel.mock();
        } else {
            targetViewModel = TargetWidget.ViewModel.from(player);
        }

        if (player != null) {
            var caster = (SpellCasterClient) player;
            var spell = caster.getCurrentSpell();
            if (spell != null) {
                castBarViewModel = new CastBarWidget.ViewModel(spell.school.color(), caster.getCurrentCastProgress(), spell.cast_duration, spell.icon_id, true);
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
                var target = caster.getCurrentTarget();
                var text = "";
                if (target != null) {
                    text = target.getName().getString();
                }
                return new ViewModel(text);
            }
        }
    }

    public static class CastBarWidget {
        private static final float tailWidth = 5;
        public static final float minWidth = 2 * tailWidth;
        private static final int textureWidth = 182;
        private static final int textureHeight = 10;
        private static final int barHeight = textureHeight / 2;
        private static final Identifier CAST_BAR = new Identifier(CombatSpells.MOD_ID, "textures/hud/castbar.png");
        public static final int spellIconSize = 16;

        public record ViewModel(int color, float progress, float castDuration, String iconTexture, boolean allowTickDelta) {
            public static ViewModel mock() {
                return new ViewModel(0xFF3300, 0.5F, 1, "combatspells:textures/spells/fireball.png", false);
            }
        }

        public static void render(MatrixStack matrixStack, float tickDelta, HudConfig hudConfig, Vec2f starting, ViewModel viewModel) {
            var barWidth = hudConfig.bar_width;
            var totalWidth = barWidth + minWidth;
            int x = (int) (starting.x - (totalWidth / 2));
            int y = (int) starting.y;

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
            renderBar(matrixStack, barWidth, false, viewModel.progress + partialProgress, x, y);

            if (hudConfig.icon.visible && viewModel.iconTexture != null) {
                x = (int) (starting.x + hudConfig.icon.offset.x);
                y = (int) (starting.y + hudConfig.icon.offset.y);
                var id = new Identifier(viewModel.iconTexture);
                RenderSystem.setShaderTexture(0, id);
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
}

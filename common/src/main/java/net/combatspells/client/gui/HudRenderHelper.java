package net.combatspells.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.combatspells.client.CombatSpellsClient;
import net.combatspells.internals.SpellCasterClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec2f;

public class HudRenderHelper {
    public static void render(MatrixStack matrixStack, float tickDelta) {
        var hudConfig = CombatSpellsClient.hudConfig.currentConfig;
        var clientConfig = CombatSpellsClient.config;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        var targetViewModel = TargetWidget.ViewModel.mock();
        if (player == null) {
            return;
        } else {
            targetViewModel = TargetWidget.ViewModel.from(player);
        }

        var screenWidth = client.getWindow().getScaledWidth();
        var screenHeight = client.getWindow().getScaledHeight();
        var originPoint = hudConfig.castWidget.origin.getPoint(screenWidth, screenHeight);
        var drawOffset = hudConfig.castWidget.offset;
        var startingPoint = originPoint.add(drawOffset);
        TargetWidget.render(matrixStack, tickDelta, startingPoint, targetViewModel);
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
                return new ViewModel("Ghast");
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
}

package net.combatspells.fabric.client;

import net.combatspells.CombatSpells;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.combatspells.client.CombatSpellsClient;
import net.combatspells.client.gui.HudRenderHelper;

public class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CombatSpellsClient.initialize();
        HudRenderCallback.EVENT.register((MatrixStack matrixStack, float tickDelta) -> {
            HudRenderHelper.render(matrixStack, tickDelta);
        });

        EntityRendererRegistry.register(CombatSpells.SPELL_PROJECTILE, (context) ->
                new FlyingItemEntityRenderer(context));
    }
}

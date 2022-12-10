package net.spell_engine.fabric.client;

import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellTooltip;
import net.spell_engine.client.projectile.SpellProjectileRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.util.math.MatrixStack;
import net.spell_engine.client.CombatSpellsClient;
import net.spell_engine.client.gui.HudRenderHelper;

public class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CombatSpellsClient.initialize();
        HudRenderCallback.EVENT.register((MatrixStack matrixStack, float tickDelta) -> {
            HudRenderHelper.render(matrixStack, tickDelta);
        });

        EntityRendererRegistry.register(SpellEngineMod.SPELL_PROJECTILE, (context) ->
                new SpellProjectileRenderer(context));

        ItemTooltipCallback.EVENT.register((itemStack, context, lines) -> {
            SpellTooltip.addSpellInfo(itemStack, lines);
        });
    }
}

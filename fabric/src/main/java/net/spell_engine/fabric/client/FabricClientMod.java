package net.spell_engine.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.HudRenderHelper;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.particle.GenericSpellParticle;
import net.spell_engine.client.particle.SpellFlameParticle;
import net.spell_engine.client.particle.SpellHitParticle;
import net.spell_engine.client.particle.SpellSnowflakeParticle;
import net.spell_engine.client.render.CustomModelRegistry;
import net.spell_engine.client.render.SpellProjectileRenderer;
import net.spell_engine.particle.Particles;

public class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpellEngineClient.initialize();
        registerKeyBindings();

        HudRenderCallback.EVENT.register((MatrixStack matrixStack, float tickDelta) -> {
            HudRenderHelper.render(matrixStack, tickDelta);
        });
        ItemTooltipCallback.EVENT.register((itemStack, context, lines) -> {
            SpellTooltip.addSpellInfo(itemStack, lines);
        });
        EntityRendererRegistry.register(SpellEngineMod.SPELL_PROJECTILE, (context) ->
                new SpellProjectileRenderer(context));

        registerParticleAppearances();
    }

    private void registerParticleAppearances() {
        /* Adds our particle textures to vanilla's Texture Atlas so it can be shown properly.
         * Modify the namespace and particle id accordingly.
         *
         * This is only used if you plan to add your own textures for the particle. Otherwise, remove  this.*/
        ClientSpriteRegistryCallback.event(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).register(((atlasTexture, registry) -> {
            for(var entry: Particles.all()) {
                if (entry.usesCustomTexture) {
                    registry.register(entry.id);
                }
            }
        }));

        /* Registers our particle client-side.
         * First argument is our particle's instance, created previously on ExampleMod.
         * Second argument is the particle's factory. The factory controls how the particle behaves.
         * In this example, we'll use FlameParticle's Factory.*/
        ParticleFactoryRegistry.getInstance().register(Particles.arcane_hit.particleType, SpellHitParticle.ArcaneFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.arcane_spell.particleType, GenericSpellParticle.ArcaneSpellFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.healing_ascend.particleType, SpellFlameParticle.HealingFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.holy_hit.particleType, SpellHitParticle.HolyFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.holy_ascend.particleType, SpellFlameParticle.HolyFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.flame.particleType, SpellFlameParticle.FlameFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.snowflake.particleType, SpellSnowflakeParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.frost_hit.particleType, SpellHitParticle.FrostFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.frost_shard.particleType, SpellFlameParticle.FrostShard::new);

        ModelLoadingRegistry.INSTANCE.registerModelProvider((manager, out) -> {
            for(var entry: CustomModelRegistry.modelIds) {
                out.accept(entry);
            }
        });
    }

    private void registerKeyBindings() {
        for(var keybinding: Keybindings.all) {
            KeyBindingHelper.registerKeyBinding(keybinding);
        }
    }
}

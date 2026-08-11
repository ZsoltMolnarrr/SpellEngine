package net.spell_engine.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.List;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.effect.SpellEngineEffects;
import net.spell_engine.api.item.set.EquipmentSetTooltip;
import net.spell_engine.api.render.BuffParticleSpawner;
import net.spell_engine.api.render.StunParticleSpawner;
import net.spell_engine.api.spell.fx.ParticleGroupBuilder;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.client.compatibility.CompatFeatures;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.particle.*;
import net.spell_engine.client.render.*;
import net.spell_engine.client.util.Color;
import net.spell_engine.config.ClientConfig;
import net.spell_engine.config.ClientConfigWrapper;
import net.spell_engine.config.HudConfig;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.rpg_series.client.RPGSeriesCoreClient;
import net.spell_engine.spellbinding.SpellBindingBlockEntity;
import net.tiny_config.ConfigManager;

public class SpellEngineClient {
    public static ClientConfig config;

    public static ConfigManager<HudConfig> hudConfig = new ConfigManager<>
            ("hud_config", HudConfig.createDefault())
            .builder()
            .setDirectory(SpellEngineMod.ID)
            .sanitize(true)
            .validate(HudConfig::isValid)
            .build();

    /// Loader-neutral client init. Loader-specific registration (particle appearances, tooltips,
    /// client-started, world render) is invoked from each loader's client entrypoint via the hooks
    /// below, so this stays free of any loader client API.
    public static void init() {
        AutoConfig.register(ClientConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ClientConfigWrapper.class).getConfig().client;
        hudConfig.refresh();

        BlockEntityRendererFactories.register(SpellBindingBlockEntity.ENTITY_TYPE, SpellBindingBlockEntityRenderer::new);
        CompatFeatures.initialize();
        registerEffectParticles();
        ModelEffectOperations.registerDefaults();

        RPGSeriesCoreClient.init();
    }

    // MARK: - Loader-invoked registration hooks

    @FunctionalInterface
    public interface ParticleAppearanceRegistrar {
        <T extends ParticleEffect> void register(ParticleType<T> type, SpriteFactory<T> factory);
    }

    /// Loader-neutral equivalent of the vanilla (private) `ParticleManager.SpriteAwareFactory` and
    /// Fabric's `PendingParticleFactory`: builds a particle factory from a sprite provider.
    @FunctionalInterface
    public interface SpriteFactory<T extends ParticleEffect> {
        ParticleFactory<T> create(SpriteProvider spriteProvider);
    }

    /// Ran once the client has started (registries frozen). Fabric: `ClientLifecycleEvents.CLIENT_STARTED`;
    /// NeoForge: `FMLClientSetupEvent`.
    public static void onClientStarted() {
        injectRangedWeaponModelPredicates();
    }

    /// Append Spell Engine tooltip lines. Fabric: `ItemTooltipCallback`; NeoForge: `ItemTooltipEvent`.
    public static void addTooltipLines(ItemStack itemStack, TooltipType tooltipType, List<Text> lines) {
        SpellTooltip.addSpellLines(itemStack, tooltipType, lines);
        EquipmentSetTooltip.appendLines(itemStack, lines);
    }

    private static void injectRangedWeaponModelPredicates() {
        for(var itemId: Registries.ITEM.getIds()) {
            var item = Registries.ITEM.get(itemId);
            if (item instanceof BowItem) {
                ModelPredicateHelper.injectBowSkillUsePredicate(item);
            } else if (item instanceof CrossbowItem) {
                ModelPredicateHelper.injectCrossBowSkillUsePredicate(item);
            }
        }
    }

    private static void registerEffectParticles() {
        CustomParticleStatusEffect.register(
                SpellEngineEffects.STUN.effect,
                new StunParticleSpawner()
        );
        final var magicSnareParticles = ParticleGroupBuilder
                .magic(SpellEngineParticles.magic_spark, ParticleGroup.Motion.DECELERATE, Color.PHYSICAL_BLUE)
                .batch(ParticleGroupBuilder.Batches.shockwave(2F, 0.15F, 5)
                        .andThen(b -> b.invert(true)));
        CustomParticleStatusEffect.register(
                SpellEngineEffects.IMMOBILIZE.effect,
                new BuffParticleSpawner(magicSnareParticles)
        );

        // Blood dripping off a bleeding entity; count scales with stacks, dripping a few times a second.
        final var bleedParticles = ParticleGroupBuilder
                .of(SpellEngineParticles.dripping_blood)
                .batch(ParticleGroupBuilder.Batches.impact(1F, 0.3F).andThen(b -> b.speed(0.1F, 0.3F)));
        CustomParticleStatusEffect.register(
                SpellEngineEffects.BLEED.effect,
                new BuffParticleSpawner(bleedParticles)
                        .withFrequency(5)
        );
    }

    /// Register the particle appearance factories. Fabric: `ParticleFactoryRegistry`; NeoForge:
    /// `RegisterParticleProvidersEvent`.
    public static void registerParticleAppearances(ParticleAppearanceRegistrar registrar) {
        // One generic factory serves every entry: appearance and behaviour are
        // resolved from the entry's defaults + the spawning effect's payload.
        for (var entry: SpellEngineParticles.entries()) {
            registrar.register(entry.type(),
                    (SpriteProvider provider) -> new SpellParticle.Factory(provider, entry));
        }
    }
}
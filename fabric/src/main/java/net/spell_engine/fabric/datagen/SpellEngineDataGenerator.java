package net.spell_engine.fabric.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.datagen.SimpleParticleGenerator;
import net.spell_engine.api.datagen.SimpleSoundGeneratorV2;
import net.spell_engine.api.tags.SpellEngineDamageTypeTags;
import net.spell_engine.api.tags.SpellEngineEntityTags;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.rpg_series.datagen.RPGSeriesAdvancements;
import net.spell_engine.rpg_series.datagen.RPGSeriesContent;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class SpellEngineDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider(ParticlesGen::new);
        pack.addProvider(SoundGen::new);
        pack.addProvider(DamageTypeTagGen::new);
        pack.addProvider(EntityTypeTagGen::new);
        pack.addProvider(RPGSeriesContent.EquipmentTagGen::new);
        pack.addProvider(RPGSeriesContent.WeaponSkillGen::new);
        pack.addProvider(RPGSeriesContent.LangGenerator::new);
        pack.addProvider(RPGSeriesAdvancements::new);
        // TestDataGen.addTo(pack);
    }

    public static class ParticlesGen extends SimpleParticleGenerator {
        public ParticlesGen(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
            super(dataOutput, registryLookup);
        }

        @Override
        public void generateSimpleParticles(Builder builder) {
            for (var entry: SpellEngineParticles.entries()) {
                ArrayList<String> textures = new ArrayList<>();
                var frameCount = entry.texture().frames();
                if (frameCount > 1) {
                    for (int i = 0; i < entry.texture().frames(); i++) {
                        var index = entry.texture().reverseOrder() ? (frameCount - 1 - i) : i;
                        textures.add(entry.texture().id().toString() + "_" + index);
                    }
                } else {
                    textures.add(entry.texture().id().toString());
                }
                builder.add(entry.id(), new SimpleParticleGenerator.ParticleData(textures));
            }
        }
    }

    public static class SoundGen extends SimpleSoundGeneratorV2 {
        public SoundGen(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
            super(dataOutput, registryLookup);
        }

        @Override
        public void generateSounds(Builder builder) {
            builder.entries.add(new Entry(SpellEngineMod.ID,
                    SpellEngineSounds.entries.stream()
                                    .map(entry -> SoundEntry.withVariants(entry.id().getPath(), entry.variants()))
                                    .toList()
                    )
            );
        }
    }

    public static class DamageTypeTagGen extends FabricTagProvider<DamageType> {
        public DamageTypeTagGen(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, Registries.DAMAGE_TYPE, registriesFuture);
        }

        @Override
        protected void addTags(HolderLookup.Provider wrapperLookup) {
            builder(SpellEngineDamageTypeTags.EVADABLE)
                    // .addTag(DamageTypeTags.IS_PROJECTILE)
                    .addOptionalTag(DamageTypeTags.IS_PROJECTILE)
                    .add(DamageTypes.PLAYER_ATTACK)
                    .add(DamageTypes.GENERIC)
                    .add(DamageTypes.MOB_ATTACK)
                    .add(DamageTypes.MOB_ATTACK_NO_AGGRO);
        }
    }

    public static class EntityTypeTagGen extends FabricTagProvider.EntityTypeTagProvider {
        public EntityTypeTagGen(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, registriesFuture);
        }

        @Override
        protected void addTags(HolderLookup.Provider wrapperLookup) {
            SpellEngineEntityTags.Vulnerability.ALL.forEach(entry -> {
                var builder = valueLookupBuilder(entry.tag());
                entry.included().forEach(tag -> {
                    builder.addOptionalTag(tag);
                });
            });
        }
    }
}

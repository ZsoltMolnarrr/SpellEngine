package net.spell_engine.data_gen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.registry.RegistryWrapper;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.datagen.SimpleParticleGenerator;
import net.spell_engine.api.datagen.SimpleSoundGenerator;
import net.spell_engine.api.datagen.SimpleSoundGeneratorV2;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.fx.SpellEngineSounds;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class SpellEngineDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider(ParticlesGen::new);
        pack.addProvider(SoundGen::new);
    }

    public static class ParticlesGen extends SimpleParticleGenerator {
        public ParticlesGen(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
            super(dataOutput, registryLookup);
        }

        @Override
        public void generateSimpleParticles(Builder builder) {
            for (var variant: SpellEngineParticles.MAGIC_FAMILY_VARIANTS.get()) {
                var textures = new ArrayList<String>();
                int frameCount = variant.frameCount();
                String texture = "";
                switch (variant.shape()) {
                    case SPELL -> {
                        texture = "minecraft:spell";
                    }
                    case IMPACT -> {
                        texture = "spell_engine:magic/impact_" + variant.familyName();
                    }
                    case SPARK -> {
                        texture = "minecraft:generic_0";
                    }
                    case STRIPE -> {
                        texture = "spell_engine:magic/vertical_stripe";
                    }
                }

                if (frameCount > 1) {
                    for (int i = 0; i < frameCount; i++) {
                        var reversedIndex = frameCount - 1 - i;
                        textures.add(texture + "_" + reversedIndex);
                    }
                } else {
                    textures.add(texture);
                }

                if (textures.isEmpty())  {
                    assert true;
                }
                builder.add(variant.id(), new SimpleParticleGenerator.ParticleData(textures));
            }
            for (var entry: SpellEngineParticles.all()) {
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
            for (var entry: SpellEngineParticles.proxies()) {
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
        public SoundGen(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
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
}

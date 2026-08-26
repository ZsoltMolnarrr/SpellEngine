package net.spell_engine.fx;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.spell_engine.api.spell.fx.ParticleGroup;
import org.jetbrains.annotations.Nullable;

/// The registered particle type behind every Spell Engine particle.
///
/// One instance per [SpellEngineParticles.Entry] is registered into
/// `Registries.PARTICLE_TYPE`; that instance is the *root*. To spawn with a
/// customized appearance, [#spawnable] derives a throwaway copy carrying the
/// [ParticleGroup.Appearance] payload (and the source entity, for
/// attachment) — the copy reports the root as its [#getType], so vanilla's
/// particle manager still resolves the factory registered for the root.
///
/// The codecs are placeholders (payload-less), matching the V1 behaviour:
/// customized spawns only travel through Spell Engine's own packet, not
/// through vanilla particle serialization.
public class ParticleGroupType extends ParticleType<ParticleGroupType> implements ParticleOptions {
    private final MapCodec<ParticleGroupType> codec = MapCodec.unit(this::getType);
    private final StreamCodec<RegistryFriendlyByteBuf, ParticleGroupType> packetCodec = StreamCodec.unit(this);

    private ParticleGroupType root = this;
    private SpellEngineParticles.Entry entry;
    @Nullable private ParticleGroup.Appearance payload;
    @Nullable private Entity sourceEntity;

    public ParticleGroupType() {
        super(true);
    }

    /// The entry this type was registered for — texture, lifetime, pivot, defaults.
    public SpellEngineParticles.Entry entry() {
        return entry;
    }

    /// Per-spawn payload; `null` = spawn with the entry's defaults.
    @Nullable public ParticleGroup.Appearance payload() {
        return payload;
    }

    /// The entity this spawn originates from, for [ParticleGroup.Attachment].
    @Nullable public Entity sourceEntity() {
        return sourceEntity;
    }

    /// Derives a spawnable instance carrying a payload and source entity.
    /// Pass the result to `World.addParticle`.
    public ParticleGroupType spawnable(@Nullable ParticleGroup.Appearance payload, @Nullable Entity sourceEntity) {
        var copy = new ParticleGroupType();
        copy.root = this.root;
        copy.entry = this.entry;
        copy.payload = payload;
        copy.sourceEntity = sourceEntity;
        return copy;
    }

    void bind(SpellEngineParticles.Entry entry) {
        this.entry = entry;
    }

    // MARK: ParticleType + ParticleEffect conformance

    @Override
    public ParticleGroupType getType() {
        return root;
    }

    @Override
    public MapCodec<ParticleGroupType> codec() {
        return codec;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, ParticleGroupType> streamCodec() {
        return packetCodec;
    }
}

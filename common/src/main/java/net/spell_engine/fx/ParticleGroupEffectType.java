package net.spell_engine.fx;

import com.mojang.serialization.MapCodec;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.spell_engine.api.spell.fx.ParticleGroupEffect;
import org.jetbrains.annotations.Nullable;

/// The registered particle type behind every Spell Engine particle.
///
/// One instance per [SpellEngineParticles.Entry] is registered into
/// `Registries.PARTICLE_TYPE`; that instance is the *root*. To spawn with a
/// customized appearance, [#spawnable] derives a throwaway copy carrying the
/// [ParticleGroupEffect.Particle] payload (and the source entity, for
/// attachment) — the copy reports the root as its [#getType], so vanilla's
/// particle manager still resolves the factory registered for the root.
///
/// The codecs are placeholders (payload-less), matching the V1 behaviour:
/// customized spawns only travel through Spell Engine's own packet, not
/// through vanilla particle serialization.
public class ParticleGroupEffectType extends ParticleType<ParticleGroupEffectType> implements ParticleEffect {
    private final MapCodec<ParticleGroupEffectType> codec = MapCodec.unit(this::getType);
    private final PacketCodec<RegistryByteBuf, ParticleGroupEffectType> packetCodec = PacketCodec.unit(this);

    private ParticleGroupEffectType root = this;
    private SpellEngineParticles.Entry entry;
    @Nullable private ParticleGroupEffect.Particle payload;
    @Nullable private Entity sourceEntity;

    public ParticleGroupEffectType() {
        super(true);
    }

    /// The entry this type was registered for — texture, lifetime, pivot, defaults.
    public SpellEngineParticles.Entry entry() {
        return entry;
    }

    /// Per-spawn payload; `null` = spawn with the entry's defaults.
    @Nullable public ParticleGroupEffect.Particle payload() {
        return payload;
    }

    /// The entity this spawn originates from, for [ParticleGroupEffect.Attachment].
    @Nullable public Entity sourceEntity() {
        return sourceEntity;
    }

    /// Derives a spawnable instance carrying a payload and source entity.
    /// Pass the result to `World.addParticle`.
    public ParticleGroupEffectType spawnable(@Nullable ParticleGroupEffect.Particle payload, @Nullable Entity sourceEntity) {
        var copy = new ParticleGroupEffectType();
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
    public ParticleGroupEffectType getType() {
        return root;
    }

    @Override
    public MapCodec<ParticleGroupEffectType> getCodec() {
        return codec;
    }

    @Override
    public PacketCodec<? super RegistryByteBuf, ParticleGroupEffectType> getPacketCodec() {
        return packetCodec;
    }
}

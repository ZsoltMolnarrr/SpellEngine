package net.spell_engine.client.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

public class CustomParticleEffect extends ParticleType<CustomParticleEffect> implements ParticleEffect, CustomizableParticleEffect {
    private final MapCodec<CustomParticleEffect> codec = MapCodec.unit(this::getType);
    private final PacketCodec<RegistryByteBuf, CustomParticleEffect> packetCodec = PacketCodec.unit(this);


    private CustomParticleEffect type;
    public CustomParticleEffect() {
        this(true);
    }

    public CustomParticleEffect(boolean alwaysShow) {
        super(alwaysShow);
        this.type = this;
    }

    public CustomParticleEffect getType() {
        return this.type;
    }

    @Override
    public MapCodec<CustomParticleEffect> getCodec() {
        return this.codec;
    }

    @Override
    public PacketCodec<? super RegistryByteBuf, CustomParticleEffect> getPacketCodec() {
        return packetCodec;
    }


    private CustomizableParticleEffect.Appearance appearance = new CustomizableParticleEffect.Appearance();

    @Override
    public void setAppearance(Appearance appearance) {
        this.appearance = appearance;
    }
    @Override
    public Appearance getAppearance() {
        return appearance;
    }
    @Override
    public CustomizableParticleEffect copy() {
        var copy = new CustomParticleEffect(this.shouldAlwaysSpawn());
        copy.type = this.type;
        return copy;
    }
}


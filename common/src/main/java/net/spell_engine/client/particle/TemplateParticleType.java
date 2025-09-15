package net.spell_engine.client.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.particle.Particle;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

public class TemplateParticleType extends ParticleType<TemplateParticleType> implements ParticleEffect, TemplateParticleEffect {
    private final MapCodec<TemplateParticleType> codec = MapCodec.unit(this::getType);
    private final PacketCodec<RegistryByteBuf, TemplateParticleType> packetCodec = PacketCodec.unit(this);

    private TemplateParticleType type;
    public TemplateParticleType() {
        this(true);
    }

    public TemplateParticleType(boolean alwaysShow) {
        super(alwaysShow);
        this.type = this;
    }

    public TemplateParticleType getType() {
        return this.type;
    }

    @Override
    public MapCodec<TemplateParticleType> getCodec() {
        return this.codec;
    }

    @Override
    public PacketCodec<? super RegistryByteBuf, TemplateParticleType> getPacketCodec() {
        return packetCodec;
    }


    private TemplateParticleEffect.Appearance appearance = new TemplateParticleEffect.Appearance();

    @Override
    public void setAppearance(Appearance appearance) {
        this.appearance = appearance;
    }
    @Override
    public Appearance getAppearance() {
        return appearance;
    }
    @Override
    public TemplateParticleEffect copy() {
        var copy = new TemplateParticleType(this.shouldAlwaysSpawn());
        copy.type = this.type;
        return copy;
    }

    public static void apply(TemplateParticleType templateParticleType, Particle particle) {
        var appearance = templateParticleType.getAppearance();
        if (appearance != null) {
            var color = appearance.color;
            if (color != null) {
                particle.setColor(color.red(), color.green(), color.blue());
            }
        }
    }
}


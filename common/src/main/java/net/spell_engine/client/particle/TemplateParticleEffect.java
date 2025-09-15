package net.spell_engine.client.particle;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.spell_engine.client.util.Color;
import org.jetbrains.annotations.Nullable;

public interface TemplateParticleEffect extends ParticleEffect {
    class Appearance {
        public @Nullable Color color;
        public float scale = 1;
        public Entity entityFollowed;
        public boolean grounded = false;
        public float max_age = 1;
    }

    void setAppearance(Appearance appearance);
    Appearance getAppearance();

    default Appearance createOrDefaultAppearance() {
        var appearance = getAppearance() == null ? new Appearance() : getAppearance();
        setAppearance(appearance);
        return appearance;
    }

    TemplateParticleEffect copy();
}

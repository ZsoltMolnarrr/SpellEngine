package net.spell_engine.client.particle;

import net.minecraft.entity.Entity;
import net.spell_engine.client.util.Color;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by particles that can be customized with a {@link TemplateParticleEffect.Appearance}.
 * Implementations expose mutations of protected {@code Particle} fields, which are only
 * accessible from within the particle subclass itself.
 */
public interface AppearanceAware {
    void applyColor(Color color);
    void multiplyAlpha(float factor);
    void multiplyScale(float factor);
    void multiplyMaxAge(float factor);
    void follow(@Nullable Entity entity);

    default void applyAppearance(@Nullable TemplateParticleEffect.Appearance appearance) {
        if (appearance == null) {
            return;
        }
        var color = appearance.color;
        if (color != null) {
            applyColor(color);
            multiplyAlpha(color.alpha());
        }
        multiplyScale(appearance.scale);
        multiplyMaxAge(appearance.max_age);
        follow(appearance.entityFollowed);
    }
}

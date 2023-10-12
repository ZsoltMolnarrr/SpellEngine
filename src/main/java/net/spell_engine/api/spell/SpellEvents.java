package net.spell_engine.api.spell;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.spell_engine.api.event.Event;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.SpellHelper;
import org.jetbrains.annotations.Nullable;

public class SpellEvents {
    // Projectile Launch event
    public static final Event<ProjectileLaunch> PROJECTILE_SHOOT = new Event<ProjectileLaunch>();
    public static final Event<ProjectileLaunch> PROJECTILE_FALL = new Event<ProjectileLaunch>();
    public record ProjectileLaunchEvent(SpellProjectile projectile,
                                        Spell.LaunchProperties mutableLaunchProperties,
                                        LivingEntity caster,
                                        @Nullable Entity target,
                                        SpellInfo spellInfo,
                                        SpellHelper.ImpactContext context,
                                        boolean initial) { }
    public interface ProjectileLaunch {
        void onProjectileLaunch(ProjectileLaunchEvent event);
    }
}

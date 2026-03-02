package net.spell_engine.api.spell.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.event.Event;
import net.spell_engine.api.event.StagedEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.casting.SpellCast;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpellEvents {
    public static final StagedEvent<CastingAttemptEvent> CASTING_ATTEMPT = new StagedEvent<>();
    public interface CastingAttemptEvent {
        record Args(PlayerEntity caster, RegistryEntry<Spell> spell, ItemStack itemStack) {}
        @Nullable SpellCast.Attempt onCastingAttempt(Args args);
    }

    public static final Event<SpellCostConsumeEvent> COST_CONSUME = new Event<>();
    public interface SpellCostConsumeEvent {
        record Args(PlayerEntity caster, RegistryEntry<Spell> spell, ItemStack itemStack) {}
        void onSpellCostConsume(Args args);
    }

    public static final Event<SpellCastEvent> SPELL_CAST = new Event<SpellCastEvent>();
    public interface SpellCastEvent {
        record Args(PlayerEntity caster, RegistryEntry<Spell> spell, List<Entity> targets, SpellCast.Action action, float progress) {}
        void onSpellCast(Args args);
    }

    public static final Event<HealEvent> HEAL = new Event<HealEvent>();
    public interface HealEvent {
        record Args(LivingEntity caster, RegistryEntry<Spell> spell, LivingEntity target, float amount) {}
        void onHeal(Args args);
    }

    // Projectile Launch event
    public static final Event<ProjectileLaunch> PROJECTILE_SHOOT = new Event<ProjectileLaunch>();
    public static final Event<ProjectileLaunch> PROJECTILE_FALL = new Event<ProjectileLaunch>();
    public record ProjectileLaunchEvent(SpellProjectile projectile,
                                        Spell.LaunchProperties mutableLaunchProperties,
                                        LivingEntity caster,
                                        @Nullable Entity target,
                                        RegistryEntry<Spell> spellEntry,
                                        SpellHelper.ImpactContext context,
                                        int sequenceIndex) { }
    public interface ProjectileLaunch {
        void onProjectileLaunch(ProjectileLaunchEvent event);
    }
}
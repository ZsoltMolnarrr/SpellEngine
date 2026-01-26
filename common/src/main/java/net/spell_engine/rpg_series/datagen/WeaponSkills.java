package net.spell_engine.rpg_series.datagen;

import net.minecraft.util.Identifier;
import net.spell_engine.api.datagen.SpellBuilder;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.Animation;
import net.spell_engine.api.spell.fx.ParticleBatch;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.rpg_series.RPGSeriesCore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WeaponSkills {
    public static String NAMESPACE = RPGSeriesCore.NAMESPACE;
    public record Entry(Identifier id, Spell spell, String title, String description,
                        @Nullable SpellTooltip.DescriptionMutator mutator) { }
    public static final List<Entry> entries = new ArrayList<>();
    private static Entry add(Entry entry) {
        entries.add(entry);
        return entry;
    }

    public static final Entry WHIRLWIND = add(whirlwind());
    private static Entry whirlwind() {
        var id = Identifier.of(NAMESPACE, "whirlwind");
        var title = "Whirlwind";
        var description = "Hold to spin around, dealing {damage} damage per second, to nearby enemies.";
        var spell = SpellBuilder.createWeaponSpell();
        spell.school = ExternalSpellSchools.PHYSICAL_MELEE;
        spell.range = 0;
        spell.range_mechanic = Spell.RangeMechanic.MELEE;

        spell.active.cast.duration = 8F;
        spell.active.cast.movement_speed = 1.1F;
        spell.active.cast.animation = Animation.of("spell_engine:two_handed_spin_static");
        spell.active.cast.animation_pitch = false;
        spell.active.cast.animation_spin = -18F;
        spell.active.cast.channel_ticks = 8;
        spell.active.cast.sound = Sound.withRandomness(SpellEngineSounds.WHIRLWIND.id(), 0F);
        spell.active.cast.particles = new ParticleBatch[]{
                new ParticleBatch(SpellEngineParticles.smoke_medium.id().toString(),
                        ParticleBatch.Shape.WIDE_PIPE, ParticleBatch.Origin.FEET,
                        1, 0.1F, 0.2F),
                new ParticleBatch("campfire_cosy_smoke",
                        ParticleBatch.Shape.WIDE_PIPE, ParticleBatch.Origin.FEET,
                        0.1F, 0.01F, 0.1F)
        };

        spell.release.sound = new Sound(SpellEngineSounds.THROW_WEAPON.id());
        spell.release.particles = new ParticleBatch[]{
                new ParticleBatch(SpellEngineParticles.smoke_medium.id().toString(),
                        ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET,
                        25, 0.15F, 0.15F)
                        .preSpawnTravel(1)
        };

        spell.target.type = Spell.Target.Type.AREA;
        spell.target.area = new Spell.Target.Area();
        spell.target.area.vertical_range_multiplier = 0.25F;

        var damage = new Spell.Impact();
        damage.action = new Spell.Impact.Action();
        damage.action.type = Spell.Impact.Action.Type.DAMAGE;
        damage.action.damage = new Spell.Impact.Action.Damage();
        damage.action.damage.spell_power_coefficient = 1.2F;
        damage.action.damage.knockback = 0.8F;
        damage.particles = new ParticleBatch[]{
                new ParticleBatch("crit",
                        ParticleBatch.Shape.SPHERE, ParticleBatch.Origin.CENTER,
                        30, 0.2F, 0.7F)
        };
        spell.impacts = List.of(damage);

        SpellBuilder.Cost.cooldown(spell, 30);
        spell.cost.cooldown.proportional = true;
        spell.cost.exhaust = 0.5F;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry BLADE_SPIN = add(BLADE_SPIN());
    private static Entry BLADE_SPIN() {
        var id = Identifier.of(NAMESPACE, "spin_cleave");
        var title = "Spin Cleave";
        var description = "Performs a spin attack, dealing {damage} damage to nearby enemies.";
        var spell = SpellBuilder.createWeaponSpell();
        spell.school = ExternalSpellSchools.PHYSICAL_MELEE;
        spell.range = 0;
        spell.range_mechanic = Spell.RangeMechanic.MELEE;

        SpellBuilder.Casting.instant(spell);

        spell.release.sound = new Sound(SpellEngineSounds.CLEAVE.id());
        spell.release.animation = Animation.of("spell_engine:cleave");
        spell.active.cast.animation_pitch = false;

        spell.target.type = Spell.Target.Type.AREA;
        spell.target.area = new Spell.Target.Area();
        spell.target.area.distance_dropoff = Spell.Target.Area.DropoffCurve.NONE;
        spell.target.area.vertical_range_multiplier = 0.5F;

        spell.release.particles_scaled_with_ranged = new ParticleBatch[]{
                new ParticleBatch(SpellEngineParticles.area_swirl.id().toString(),
                        ParticleBatch.Shape.SPHERE, ParticleBatch.Origin.CENTER,
                        1, 0.0F, 0.F)
                        .scale(0.8F)
                        .followEntity(true)
        };

        spell.deliver.delay = 2;

        var damage = SpellBuilder.Impacts.damage(1F);
        spell.impacts = List.of(damage);

        SpellBuilder.Cost.cooldown(spell, 6);

        return new Entry(id, spell, title, description, null);
    }
}

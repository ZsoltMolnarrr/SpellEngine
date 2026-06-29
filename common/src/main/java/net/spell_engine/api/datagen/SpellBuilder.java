package net.spell_engine.api.datagen;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.spell_engine.api.effect.SpellEngineEffects;
import net.spell_engine.api.entity.SpellEntityPredicates;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.api.spell.fx.ParticleBatch;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.spell.summon.SummonBehaviour;
import net.spell_engine.client.util.Color;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_power.api.SpellSchool;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class SpellBuilder {
    public static final String GROUP_PRIMARY = "primary";

    public static Spell createSpellActive() {
        var spell = new Spell();
        spell.type = Spell.Type.ACTIVE;
        spell.active = new Spell.Active();
        spell.active.cast = new Spell.Active.Cast();
        spell.learn = new Spell.Learn();
        return spell;
    }

    public static Spell createWeaponSpell() {
        var spell = createSpellActive();
        spell.tier = 1;
        Cost.cooldownGroupWeapon(spell);
        return spell;
    }

    public static Spell createMeleeSpell() {
        var spell = createWeaponSpell();
        spell.school = ExternalSpellSchools.PHYSICAL_MELEE;
        spell.range = 0;
        spell.range_mechanic = Spell.RangeMechanic.MELEE;
        spell.cost.exhaust = 0.1F;
        return spell;
    }

    public static Spell createSpellPassive() {
        var spell = new Spell();
        spell.type = Spell.Type.PASSIVE;
        spell.passive = new Spell.Passive();
        spell.cost.durability = 0;
        return spell;
    }

    public static Spell createSpellModifier() {
        var spell = new Spell();
        spell.type = Spell.Type.MODIFIER;
        spell.range = 0;
        spell.modifiers = new ArrayList<>();
        return spell;
    }

    public static void configureImpactEnableCondition(Spell.Impact impact, Spell.TargetCondition targetCondition) {
        var targetModifier = new Spell.Impact.TargetModifier();
        targetModifier.conditions = List.of(targetCondition);
        impact.target_modifiers = List.of(targetModifier);
    }

    public static class Casting {
        public static void instant(Spell spell) {
            spell.active.cast = new Spell.Active.Cast();
            spell.active.cast.duration = 0;
        }

        public static void cast(Spell spell, float duration) {
            cast(spell, duration, null);
        }

        public static void cast(Spell spell, float duration, @Nullable String animation) {
            spell.active.cast = new Spell.Active.Cast();
            spell.active.cast.duration = duration;
            if (animation != null) {
                spell.active.cast.animation = PlayerAnimation.of(animation);
            }
        }

        public static void channel(Spell spell, float duration, int ticks) {
            spell.active.cast = new Spell.Active.Cast();
            spell.active.cast.duration = duration;
            spell.active.cast.type = Spell.Active.Cast.Type.CHANNEL;
            spell.active.cast.channel = new Spell.Active.Cast.Channel();
            spell.active.cast.channel.ticks = ticks;
        }

        /// Configures the spell as CHARGE: the player holds to charge and may release early, and the
        /// returned `Charge` carries the bonus applied at full charge (scaled toward zero by the
        /// curved release ratio). Configure `bonus`, `curve` and `min_release_ratio` on the result.
        public static Spell.Active.Cast.Charge charge(Spell spell, float duration) {
            spell.active.cast = new Spell.Active.Cast();
            spell.active.cast.duration = duration;
            spell.active.cast.type = Spell.Active.Cast.Type.CHARGE;
            spell.active.cast.charge = new Spell.Active.Cast.Charge();
            return spell.active.cast.charge;
        }

        public static Spell.Active.Cast.Charge charge(Spell spell, float duration, Spell.Active.Cast.Charge.Curve curve) {
            var charge = charge(spell, duration);
            charge.curve = curve;
            return charge;
        }

        public static void visuals(Spell spell,
                                       @Nullable String playerAnimation, @Nullable ParticleBatch[] particles, @Nullable Sound sound) {
            if (spell.active.cast == null) {
                spell.active.cast = new Spell.Active.Cast();
            }
            if (playerAnimation != null) {
                spell.active.cast.animation = PlayerAnimation.of(playerAnimation);
            }
            if (particles != null) {
                spell.active.cast.particles = particles;
            }
        }
    }

    public static class Release {
        public static void visuals(Spell spell,
                                   @Nullable String playerAnimation, @Nullable ParticleBatch[] particles, @Nullable Sound sound) {
            spell.release = new Spell.Release();
            if (playerAnimation != null) {
                spell.release.animation = PlayerAnimation.of(playerAnimation);
            }
            if (particles != null) {
                spell.release.particles = particles;
            }
            if (sound != null) {
                spell.release.sound = sound;
            }
        }
    }

    public static class TargetConditions {
        public static Spell.TargetCondition dead() {
            var deadCondition = new Spell.TargetCondition();
            deadCondition.health_percent_below = 0F;
            deadCondition.health_percent_above = 0F;
            return deadCondition;
        }

        public static Spell.TargetCondition lowHP() {
            return lowHP(0.5F);
        }

        public static Spell.TargetCondition lowHP(float maxHealthPercent) {
            var deadCondition = new Spell.TargetCondition();
            deadCondition.health_percent_below = maxHealthPercent;
            deadCondition.health_percent_above = 0.01F;
            return deadCondition;
        }

        public static Spell.TargetCondition hasEffect(Identifier effectId) {
            var effectCondition = new Spell.TargetCondition();
            effectCondition.entity_predicate_id = SpellEntityPredicates.HAS_EFFECT.id().toString();
            effectCondition.entity_predicate_param = effectId.toString();
            return effectCondition;
        }

        public static Spell.TargetCondition ofPredicate(SpellEntityPredicates.Entry entityPredicate) {
            var targetCondition = new Spell.TargetCondition();
            targetCondition.entity_predicate_id = entityPredicate.id().toString();
            return targetCondition;
        }
    }

    public static class Target {
        public static void none(Spell spell) {
            spell.target.type = Spell.Target.Type.NONE;
        }
        public static void aim(Spell spell) {
            spell.target.type = Spell.Target.Type.AIM;
            spell.target.aim = new Spell.Target.Aim();
        }
    }

    public static class Deliver {
        public static void stash(Spell spell, String stashEffectId, float duration, Spell.Trigger trigger) {
            stash(spell, stashEffectId, duration, List.of(trigger));
        }
        public static void stash(Spell spell, String stashEffectId, float duration, List<Spell.Trigger> triggers) {
            spell.deliver.type = Spell.Delivery.Type.STASH_EFFECT;
            spell.deliver.stash_effect = new Spell.Delivery.StashEffect();
            spell.deliver.stash_effect.id = stashEffectId;
            spell.deliver.stash_effect.duration = duration;
            spell.deliver.stash_effect.triggers = triggers;
        }

        public static Spell.EntityPlacement placementByLook(float distanceOffset, float angleOffset, int delay) {
            var placement = new Spell.EntityPlacement();
            placement.location_offset_by_look = distanceOffset;
            placement.location_yaw_offset = angleOffset;
            placement.apply_yaw = true;

            placement.delay_ticks = delay;
            return placement;
        }

        public static Spell.Delivery.Cloud cloud(float timeToLive, float radius, Identifier spawnSound, int light_level, ParticleBatch[] presenceParticles) {
            var cloud = new Spell.Delivery.Cloud();
            cloud.volume = new Spell.AreaImpact();
            cloud.volume.area.vertical_range_multiplier = 0.3F;
            cloud.volume.radius = radius;

            cloud.impact_tick_interval = 4;
            cloud.time_to_live_seconds = timeToLive;
            cloud.spawn.sound = new Sound(spawnSound.toString());
            cloud.client_data = new Spell.Delivery.Cloud.ClientData();
            cloud.client_data.light_level = light_level;
            cloud.client_data.particles = presenceParticles;

            return cloud;
        }

        public static void melee(Spell spell, List<Spell.Delivery.Melee.Attack> attacks) {
            spell.deliver.type = Spell.Delivery.Type.MELEE;
            spell.deliver.melee = new Spell.Delivery.Melee();
            spell.deliver.melee.attacks = attacks;
        }
    }

    /// Factory functions for {@link Spell.EntityPlacement}s and common multi-placement arrangements,
    /// to keep generator code declarative. Methods are tagged by the axis they affect: SPATIAL ones
    /// (`point`, `ring`, `cross`, `diamond`, `row`, `ray`) build a layout and return a new list;
    /// TIMING ones (`delayCascade`, `delayUniform`) adjust `delay_ticks` on an existing list in place.
    ///
    /// Geometry is relative to the caster's facing: a placement is positioned `distance` blocks away
    /// in a direction `yawOffset` degrees clockwise from where the caster looks (0 = front, 90 =
    /// right, 180 = back, 270 = left). The arrangements clone a `template` placement so shared flags
    /// (delay, ground-snap, line-of-sight, orientation, fixed offsets) carry over to every slot,
    /// while the layout fields (`location_offset_by_look` / `location_yaw_offset`) are set per slot.
    public static class Placements {
        /// Default template for the no-template overloads: oriented to the caster's yaw and
        /// ground-snapped.
        public static Spell.EntityPlacement template() {
            var placement = new Spell.EntityPlacement();
            placement.apply_yaw = true;
            placement.force_onto_ground = true;
            return placement;
        }

        /// SPATIAL: a single placement `distance` blocks from the caster, `yawOffset` degrees clockwise
        /// from its facing (0 = front, 90 = right, 180 = back, 270 = left).
        public static Spell.EntityPlacement point(float distance, float yawOffset) {
            var placement = template();
            placement.location_offset_by_look = distance;
            placement.location_yaw_offset = yawOffset;
            return placement;
        }
        /// SPATIAL (with an initial spawn delay): a single placement, seeded with `delay` ticks.
        public static Spell.EntityPlacement point(float distance, float yawOffset, int delay) {
            var placement = point(distance, yawOffset);
            placement.delay_ticks = delay;
            return placement;
        }

        /// SPATIAL: `count` placements evenly spaced around a ring of `radius` about the caster, the
        /// first at `startAngle` (0 = front) and proceeding clockwise. Each slot is a clone of `template`.
        public static List<Spell.EntityPlacement> ring(int count, float radius, float startAngle, Spell.EntityPlacement template) {
            var list = new ArrayList<Spell.EntityPlacement>();
            if (count <= 0) return list;
            float step = 360F / count;
            for (int i = 0; i < count; i++) {
                var placement = template.copy();
                placement.location_offset_by_look = radius;
                placement.location_yaw_offset = startAngle + step * i;
                list.add(placement);
            }
            return list;
        }
        public static List<Spell.EntityPlacement> ring(int count, float radius) {
            return ring(count, radius, 0, template());
        }

        /// SPATIAL: 4 placements in the cardinal directions relative to the caster's facing — front,
        /// right, back, left — each `distance` blocks out (a compass rose).
        public static List<Spell.EntityPlacement> cross(float distance, Spell.EntityPlacement template) {
            return ring(4, distance, 0, template);
        }
        public static List<Spell.EntityPlacement> cross(float distance) {
            return cross(distance, template());
        }

        /// SPATIAL: 4 placements at the corners of a square around the caster (the diagonals:
        /// front-right, back-right, back-left, front-left), each `distance` blocks from the centre.
        public static List<Spell.EntityPlacement> diamond(float distance, Spell.EntityPlacement template) {
            return ring(4, distance, 45, template);
        }
        public static List<Spell.EntityPlacement> diamond(float distance) {
            return diamond(distance, template());
        }

        /// The order in which a {@link #row} fills its slots. The geometry is always a centered row;
        /// this only controls the sequence the placements are returned in — which matters because a
        /// partial fill (`spawn_count` < slots) keeps the leading slots, and {@link #delayCascade}
        /// delays scale with list index.
        public enum RowOrder {
            /// Centre first, then outward in alternating sides: centre, right 1, left 1, right 2, ...
            CENTER_OUT,
            /// Outermost slots first, working inward toward the centre.
            EDGES_IN,
            /// Leftmost slot first, proceeding to the right.
            LEFT_TO_RIGHT,
            /// Rightmost slot first, proceeding to the left.
            RIGHT_TO_LEFT
        }

        /// SPATIAL: `count` placements in a straight row centered on the caster and perpendicular to
        /// its facing (a left-right line), `spacing` blocks apart, the whole row offset `forward`
        /// blocks ahead of the caster (negative = behind). Geometry is symmetric about the centre (an
        /// exact centre slot for odd counts); `order` chooses the fill sequence. Each slot clones `template`.
        public static List<Spell.EntityPlacement> row(int count, float spacing, float forward, RowOrder order, Spell.EntityPlacement template) {
            var list = new ArrayList<Spell.EntityPlacement>();
            if (count <= 0) return list;
            // Signed position of each slot along the perpendicular axis, centered on the caster.
            // Positive = right, negative = left.
            float[] offsets = new float[count];
            for (int i = 0; i < count; i++) {
                offsets[i] = (i - (count - 1) / 2F) * spacing;
            }
            // Order the slot indices per the requested fill order. Side ties (a symmetric pair at the
            // same distance) resolve right-before-left.
            Integer[] indices = new Integer[count];
            for (int i = 0; i < count; i++) indices[i] = i;
            Comparator<Integer> comparator = switch (order) {
                case LEFT_TO_RIGHT -> Comparator.comparingDouble(i -> offsets[i]);
                case RIGHT_TO_LEFT -> Comparator.comparingDouble(i -> -offsets[i]);
                case CENTER_OUT -> Comparator.<Integer>comparingDouble(i -> Math.abs(offsets[i]))
                        .thenComparingDouble(i -> -offsets[i]);
                case EDGES_IN -> Comparator.<Integer>comparingDouble(i -> -Math.abs(offsets[i]))
                        .thenComparingDouble(i -> -offsets[i]);
            };
            Arrays.sort(indices, comparator);
            for (int index : indices) {
                float signed = offsets[index];
                var placement = template.copy();
                // Resolve the (forward, side) vector into a single look-offset: distance from the
                // caster and yaw rotated from its facing. forward = 0 gives a row through the caster.
                placement.location_offset_by_look = (float) Math.hypot(forward, signed);
                placement.location_yaw_offset = (float) Math.toDegrees(Math.atan2(signed, forward));
                list.add(placement);
            }
            return list;
        }
        public static List<Spell.EntityPlacement> row(int count, float spacing, RowOrder order, Spell.EntityPlacement template) {
            return row(count, spacing, 0F, order, template);
        }
        public static List<Spell.EntityPlacement> row(int count, float spacing, RowOrder order) {
            return row(count, spacing, 0F, order, template());
        }
        public static List<Spell.EntityPlacement> row(int count, float spacing, Spell.EntityPlacement template) {
            return row(count, spacing, 0F, RowOrder.CENTER_OUT, template);
        }
        public static List<Spell.EntityPlacement> row(int count, float spacing) {
            return row(count, spacing, 0F, RowOrder.CENTER_OUT, template());
        }

        /// SPATIAL: `count` placements in a straight line projecting away from the caster: the first
        /// `startDistance` blocks out and each subsequent one `spacing` blocks further, all along a ray
        /// `angle` degrees clockwise from the caster's facing (0 = straight ahead, 90 = right,
        /// 180 = back, 270 = left). The slot nearest the caster is first in the list, the farthest is
        /// last — so pairing with {@link #delayCascade} marches the spawns outward over time. Each slot
        /// clones `template`.
        public static List<Spell.EntityPlacement> ray(int count, float spacing, float startDistance, float angle, Spell.EntityPlacement template) {
            var list = new ArrayList<Spell.EntityPlacement>();
            if (count <= 0) return list;
            for (int i = 0; i < count; i++) {
                var placement = template.copy();
                placement.location_offset_by_look = startDistance + i * spacing;
                placement.location_yaw_offset = angle;
                list.add(placement);
            }
            return list;
        }
        public static List<Spell.EntityPlacement> ray(int count, float spacing, float startDistance, float angle) {
            return ray(count, spacing, startDistance, angle, template());
        }
        public static List<Spell.EntityPlacement> ray(int count, float spacing, float startDistance) {
            return ray(count, spacing, startDistance, 0F, template());
        }
        public static List<Spell.EntityPlacement> ray(int count, float spacing) {
            return ray(count, spacing, spacing, 0F, template());
        }

        /// SPATIAL: a `rows` x `columns` grid of placements centered on the caster — `rows` rows
        /// stacked along the forward axis `rowSpacing` blocks apart, each holding `columns` slots
        /// along the left-right axis `columnSpacing` blocks apart. The whole block is symmetric about
        /// the caster (an exact centre slot when both counts are odd), so the front half sits ahead of
        /// the caster and the back half behind. Filled front row first (nearest the caster's facing),
        /// left to right within each row — so pairing with {@link #delayCascade} sweeps the grid from
        /// front to back. Total slots = `rows * columns`. Each slot clones `template`.
        public static List<Spell.EntityPlacement> squareGrid(int rows, float rowSpacing, int columns, float columnSpacing, Spell.EntityPlacement template) {
            var list = new ArrayList<Spell.EntityPlacement>();
            if (rows <= 0 || columns <= 0) return list;
            for (int r = 0; r < rows; r++) {
                // Forward position of this row: r = 0 is the frontmost row, increasing r steps backward.
                float forward = ((rows - 1) / 2F - r) * rowSpacing;
                for (int c = 0; c < columns; c++) {
                    // Side position of this column: negative = left, positive = right, centered.
                    float side = (c - (columns - 1) / 2F) * columnSpacing;
                    var placement = template.copy();
                    // Resolve the (forward, side) vector into a single look-offset: radial distance
                    // from the caster and yaw rotated from its facing.
                    placement.location_offset_by_look = (float) Math.hypot(forward, side);
                    placement.location_yaw_offset = (float) Math.toDegrees(Math.atan2(side, forward));
                    list.add(placement);
                }
            }
            return list;
        }
        public static List<Spell.EntityPlacement> squareGrid(int rows, float rowSpacing, int columns, float columnSpacing) {
            return squareGrid(rows, rowSpacing, columns, columnSpacing, template());
        }
        /// A uniform `size` x `size` grid, `spacing` blocks apart on both axes. See
        /// {@link #squareGrid(int, float, int, float, Spell.EntityPlacement)}.
        public static List<Spell.EntityPlacement> squareGrid(int size, float spacing) {
            return squareGrid(size, spacing, size, spacing, template());
        }

        /// TIMING: cascades spawn delays in place so the slots spawn in sequence — the placement at
        /// index `i` gets `i * stepTicks` added to its `delay_ticks`, producing a marching-wave effect.
        /// Returns the same list for chaining.
        public static List<Spell.EntityPlacement> delayCascade(List<Spell.EntityPlacement> placements, int stepTicks) {
            for (int i = 0; i < placements.size(); i++) {
                placements.get(i).delay_ticks += i * stepTicks;
            }
            return placements;
        }

        /// TIMING: adds the same spawn delay to every placement in place (all spawn together, later).
        /// Returns the same list for chaining.
        public static List<Spell.EntityPlacement> delayUniform(List<Spell.EntityPlacement> placements, int ticks) {
            for (var placement : placements) {
                placement.delay_ticks += ticks;
            }
            return placements;
        }
    }

    public static class Triggers {
        public static Spell.Trigger withConditionMustWield(Spell.Trigger trigger) {
            trigger.equipment_condition = EquipmentSlot.MAINHAND;
            return trigger;
        }

        public static List<Spell.Trigger> withConditionMustWield(List<Spell.Trigger> triggers) {
            for (var trigger : triggers) {
                trigger.equipment_condition = EquipmentSlot.MAINHAND;
            }
            return triggers;
        }

        public static Spell.Trigger activeSpellHit(float chance, @Nullable String schoolRegex) {
            var trigger = spellHit(chance, schoolRegex);
            trigger.spell.type = Spell.Type.ACTIVE;
            return trigger;
        }

        public static Spell.Trigger spellHit(float chance, @Nullable String schoolRegex) {
            var trigger = new Spell.Trigger();
            trigger.impact = new Spell.Trigger.ImpactCondition();
            trigger.impact.impact_type = Spell.Impact.Action.Type.DAMAGE.toString();
            trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
            trigger.spell = new Spell.Trigger.SpellCondition();
            trigger.spell.school = schoolRegex;
            trigger.chance = chance;
            return trigger;
        }

        public static Spell.Trigger specificSpellHit(String spellId) {
            var trigger = new Spell.Trigger();
            trigger.impact = new Spell.Trigger.ImpactCondition();
            trigger.impact.impact_type = Spell.Impact.Action.Type.DAMAGE.toString();
            trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
            trigger.spell = new Spell.Trigger.SpellCondition();
            trigger.spell.id = spellId;
            return trigger;
        }

        public static Spell.Trigger activeSpellCrit() {
            var trigger = new Spell.Trigger();
            trigger.impact = new Spell.Trigger.ImpactCondition();
            trigger.impact.critical = true;
            trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
            trigger.spell = new Spell.Trigger.SpellCondition();
            trigger.spell.type = Spell.Type.ACTIVE;
            return trigger;
        }

        public static Spell.Trigger specificSpellAreaImpact(String spellId) {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.SPELL_AREA_IMPACT;
            trigger.spell = new Spell.Trigger.SpellCondition();
            trigger.spell.id = spellId;
            return trigger;
        }

        public static Spell.Trigger specificSpellCast(String spellId) {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.SPELL_CAST;
            trigger.spell = new Spell.Trigger.SpellCondition();
            trigger.spell.id = spellId;
            return trigger;
        }

        public static Spell.Trigger spellCast(SpellSchool school) {
            return spellCast(school.id.toString());
        }

        public static Spell.Trigger spellCast(String school) {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.SPELL_CAST;
            trigger.spell = new Spell.Trigger.SpellCondition();
            trigger.spell.school = school;
            return trigger;
        }

        public static Spell.Trigger activeSpellCast() {
            return activeSpellCast((String)null);
        }

        public static Spell.Trigger activeSpellCast(SpellSchool school) {
            return activeSpellCast(school.id.toString());
        }

        public static Spell.Trigger activeSpellCast(String school) {
            var trigger = spellCast(school);
            trigger.spell.type = Spell.Type.ACTIVE;
            return trigger;
        }

        public static Spell.Trigger activeSpellHeal(float chance) {
            var trigger = new Spell.Trigger();
            trigger.impact = new Spell.Trigger.ImpactCondition();
            trigger.impact.impact_type = Spell.Impact.Action.Type.HEAL.toString();
            trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
            trigger.spell = new Spell.Trigger.SpellCondition();
            trigger.spell.type = Spell.Type.ACTIVE;
            trigger.chance = chance;
            return trigger;
        }

        @Deprecated(forRemoval = true)
        public static Spell.Trigger meleeAttack(boolean mustWield) {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.MELEE_IMPACT;
            if (mustWield) {
                trigger.equipment_condition = EquipmentSlot.MAINHAND;
            }
            return trigger;
        }

        @Deprecated(forRemoval = true)
        public static Spell.Trigger meleeAttack() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.MELEE_IMPACT;
            return trigger;
        }

        public static Spell.Trigger meleeAttackImpact() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.MELEE_IMPACT;
            return trigger;
        }

        public static Spell.Trigger meleeSkillImpact() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
            trigger.spell = new Spell.Trigger.SpellCondition();
            trigger.spell.archetype = SpellSchool.Archetype.MELEE;
            trigger.spell.type = Spell.Type.ACTIVE;
            return trigger;
        }

        public static List<Spell.Trigger> meleeImpact() {
            return List.of(meleeAttackImpact(), meleeSkillImpact());
        }

        @Deprecated(forRemoval = true)
        public static Spell.Trigger meleeKill(boolean mustWield) {
            var deadCondition = TargetConditions.dead();

            var trigger = meleeAttack(mustWield);
            trigger.target_conditions = List.of(deadCondition);

            return trigger;
        }

        @Deprecated(forRemoval = true)
        public static List<Spell.Trigger> meleeKills(boolean mustWield) {
            var deadCondition = TargetConditions.dead();

            var attackTrigger = meleeAttack(mustWield);
            attackTrigger.target_conditions = List.of(deadCondition);

            var skillTrigger = new Spell.Trigger();
            skillTrigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
            skillTrigger.spell = new Spell.Trigger.SpellCondition();
            skillTrigger.spell.school = ExternalSpellSchools.PHYSICAL_MELEE.id.toString();
            skillTrigger.target_conditions = List.of(deadCondition);

            return List.of(attackTrigger, skillTrigger);
        }

        public static List<Spell.Trigger> meleeKills() {
            var triggers = meleeImpact();
            for (var trigger : triggers) {
                var deadCondition = TargetConditions.dead();
                trigger.target_conditions = List.of(deadCondition);
            }
            return triggers;
        }

        public static Spell.Trigger spellKill() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
            trigger.impact = new Spell.Trigger.ImpactCondition();
            trigger.impact.impact_type = Spell.Impact.Action.Type.DAMAGE.toString();
            var deadCondition = TargetConditions.dead();
            trigger.target_conditions = List.of(deadCondition);
            return trigger;
        }

        @Deprecated(forRemoval = true)
        public static Spell.Trigger rangedAttack() {
            return rangedAttack(false);
        }

        @Deprecated(forRemoval = true)
        public static Spell.Trigger rangedAttack(boolean mustWield) {
            var arrowTrigger = new Spell.Trigger();
            arrowTrigger.type = Spell.Trigger.Type.ARROW_IMPACT;
            if (mustWield) {
                arrowTrigger.equipment_condition = EquipmentSlot.MAINHAND;
            }
            return arrowTrigger;
        }

        public static Spell.Trigger rangedAttackImpact() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.ARROW_IMPACT;
            return trigger;
        }

        public static Spell.Trigger rangedSkillImpact() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
            trigger.spell = new Spell.Trigger.SpellCondition();
            trigger.spell.archetype = SpellSchool.Archetype.ARCHERY;
            trigger.spell.type = Spell.Type.ACTIVE;
            return trigger;
        }

        public static List<Spell.Trigger> rangedImpact() {
            return List.of(rangedAttackImpact(), rangedSkillImpact());
        }

        public static List<Spell.Trigger> rangedKill() {
            var triggers = rangedImpact();
            for (var trigger : triggers) {
                var deadCondition = TargetConditions.dead();
                trigger.target_conditions = List.of(deadCondition);
            }
            return triggers;
        }

        @Deprecated(forRemoval = true)
        public static List<Spell.Trigger> rangedKill(boolean mustWield) {
            var deadCondition = TargetConditions.dead();
            var arrowTrigger = rangedAttack(mustWield);
            arrowTrigger.target_conditions = List.of(deadCondition);

            var skillTrigger = new Spell.Trigger();
            skillTrigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
            skillTrigger.spell = new Spell.Trigger.SpellCondition();
            skillTrigger.spell.archetype = SpellSchool.Archetype.ARCHERY;
            skillTrigger.target_conditions = List.of(deadCondition);

            return List.of(arrowTrigger, skillTrigger);
        }

        public static Spell.Trigger shieldBlock() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.SHIELD_BLOCK;
            return trigger;
        }

        public static Spell.Trigger arrowHit() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.ARROW_IMPACT;
            return trigger;
        }

        public static Spell.Trigger arrowShot() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.ARROW_SHOT;
            return trigger;
        }

        public static Spell.Trigger arrowShot(boolean firedBySpell) {
            var trigger = arrowShot();
            trigger.arrow_shot = new Spell.Trigger.ArrowShotCondition();
            trigger.arrow_shot.from_spell = firedBySpell;
            return trigger;
        }

        public static Spell.Trigger roll() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.ROLL;
            return trigger;
        }

        public static Spell.Trigger evade() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.EVASION;
            return trigger;
        }

        public static Spell.Trigger damageTaken() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.DAMAGE_TAKEN;
            return trigger;
        }

        public static Spell.Trigger damageIncoming() {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.DAMAGE_TAKEN;
            trigger.stage = Spell.Trigger.Stage.PRE;
            return trigger;
        }

        public static Spell.Trigger damageIncomingFatal() {
            var trigger = damageIncoming();
            trigger.damage = new Spell.Trigger.DamageCondition();
            trigger.damage.fatal = true;
            return trigger;
        }

        public static Spell.Trigger effectTick(String effectId) {
            var trigger = new Spell.Trigger();
            trigger.type = Spell.Trigger.Type.EFFECT_TICK;
            trigger.effect = new Spell.Trigger.EffectCondition();
            trigger.effect.id = effectId;
            return trigger;
        }

        public static Spell.Trigger becomingLowHP(float healthThreshold) {
            var trigger = SpellBuilder.Triggers.damageTaken();
            trigger.stage = Spell.Trigger.Stage.POST;
            trigger.caster_conditions = List.of(SpellBuilder.TargetConditions.lowHP(healthThreshold));
            return trigger;
        }
    }

    public static class Impacts {
        public static Spell.Impact damage(float coefficient) {
            var impact = new Spell.Impact();
            impact.action = new Spell.Impact.Action();
            impact.action.type = Spell.Impact.Action.Type.DAMAGE;
            impact.action.damage = new Spell.Impact.Action.Damage();
            impact.action.damage.spell_power_coefficient = coefficient;
            return impact;
        }

        public static Spell.Impact damage(float coefficient, float knockback) {
            var impact = new Spell.Impact();
            impact.action = new Spell.Impact.Action();
            impact.action.type = Spell.Impact.Action.Type.DAMAGE;
            impact.action.damage = new Spell.Impact.Action.Damage();
            impact.action.damage.spell_power_coefficient = coefficient;
            impact.action.damage.knockback = knockback;
            return impact;
        }

        public static Spell.Impact heal(float spell_power_coefficient) {
            var impact = new Spell.Impact();
            impact.action = new Spell.Impact.Action();
            impact.action.type = Spell.Impact.Action.Type.HEAL;
            impact.action.heal = new Spell.Impact.Action.Heal();
            impact.action.heal.spell_power_coefficient = spell_power_coefficient;
            return impact;
        }

        public static Spell.Impact effectSet(String effectIdString, float duration, int amplifier) {
            var impact = new Spell.Impact();
            impact.action = new Spell.Impact.Action();
            impact.action.type = Spell.Impact.Action.Type.STATUS_EFFECT;
            impact.action.status_effect = new Spell.Impact.Action.StatusEffect();
            impact.action.status_effect.apply_mode = Spell.Impact.Action.StatusEffect.ApplyMode.SET;
            impact.action.status_effect.effect_id = effectIdString;
            impact.action.status_effect.duration = duration;
            impact.action.status_effect.amplifier = amplifier;
            return impact;
        }

        public static Spell.Impact effectSet_ScaledAmplifier(String effectIdString, float duration, int amplifier, float coefficient) {
            var impact = effectSet(effectIdString, duration, amplifier);
            impact.action.status_effect.amplifier_power_multiplier = coefficient;
            return impact;
        }

        public static Spell.Impact effectSet_ScaledAmplifier_Cap(String effectIdString, float duration, int amplifier, float coefficient, int amplifierCap) {
            var impact = effectSet_ScaledAmplifier(effectIdString, duration, amplifier, coefficient);
            impact.action.status_effect.amplifier_cap = amplifierCap;
            return impact;
        }

        public static Spell.Impact effectAdd(String effectIdString, float duration, int amplifier, int amplifierCap) {
            var impact = new Spell.Impact();
            impact.action = new Spell.Impact.Action();
            impact.action.type = Spell.Impact.Action.Type.STATUS_EFFECT;
            impact.action.status_effect = new Spell.Impact.Action.StatusEffect();
            impact.action.status_effect.apply_mode = Spell.Impact.Action.StatusEffect.ApplyMode.ADD;
            impact.action.status_effect.effect_id = effectIdString;
            impact.action.status_effect.duration = duration;
            impact.action.status_effect.amplifier = amplifier;
            impact.action.status_effect.amplifier_cap = amplifierCap;
            return impact;
        }

        public static Spell.Impact effectAdd_ScaledCap(String effectIdString, float duration, float coefficient) {
            var impact = effectAdd(effectIdString, duration, 1, 1);
            impact.action.status_effect.amplifier_cap_power_multiplier = coefficient;
            return impact;
        }

        public static Spell.Impact effectCleanse() {
            var cleanse = new Spell.Impact();
            cleanse.action = new Spell.Impact.Action();
            cleanse.action.type = Spell.Impact.Action.Type.STATUS_EFFECT;
            cleanse.action.status_effect = new Spell.Impact.Action.StatusEffect();
            cleanse.action.status_effect.apply_mode = Spell.Impact.Action.StatusEffect.ApplyMode.REMOVE;
            cleanse.action.status_effect.remove = new Spell.Impact.Action.StatusEffect.Remove();
            cleanse.action.status_effect.remove.id = "!" + StatusEffects.TRIAL_OMEN.getIdAsString();
            cleanse.action.status_effect.remove.selector = Spell.Impact.Action.StatusEffect.Remove.Selector.RANDOM;
            cleanse.action.status_effect.remove.select_beneficial = false;
            cleanse.sound = new Sound(SpellEngineSounds.GENERIC_DISPEL_1.id());
            return cleanse;
        }

        public static Spell.Impact effectRemove(String effectIdString) {
            var impact = new Spell.Impact();
            impact.action = new Spell.Impact.Action();
            impact.action.type = Spell.Impact.Action.Type.STATUS_EFFECT;
            impact.action.status_effect = new Spell.Impact.Action.StatusEffect();
            impact.action.status_effect.apply_mode = Spell.Impact.Action.StatusEffect.ApplyMode.REMOVE;
            impact.action.status_effect.remove = new Spell.Impact.Action.StatusEffect.Remove();
            impact.action.status_effect.remove.id = effectIdString;
            return impact;
        }

        public static Spell.Impact stun(float duration) {
            var impact = effectSet(SpellEngineEffects.STUN.id.toString(), duration, 0);
            impact.sound = new Sound(SpellEngineSounds.STUN_GENERIC.id().toString());
            return impact;
        }

        public static Spell.Impact taunt() {
            var taunt = new Spell.Impact();
            taunt.action = new Spell.Impact.Action();
            taunt.action.type = Spell.Impact.Action.Type.AGGRO;
            taunt.action.aggro = new Spell.Impact.Action.Aggro();
            taunt.action.aggro.mode = Spell.Impact.Action.Aggro.Mode.SET;
            return taunt;
        }

        public static Spell.Impact disrupt(boolean shieldBlocking, float itemUsageTime) {
            var impact = new Spell.Impact();
            impact.action = new Spell.Impact.Action();
            impact.action.type = Spell.Impact.Action.Type.DISRUPT;
            impact.action.disrupt = new Spell.Impact.Action.Disrupt();
            impact.action.disrupt.shield_blocking = shieldBlocking;
            impact.action.disrupt.item_usage_seconds = itemUsageTime;
            return impact;
        }

        public static Spell.Impact disengage(boolean onlyIfTargeted) {
            var taunt = new Spell.Impact();
            taunt.action = new Spell.Impact.Action();
            taunt.action.type = Spell.Impact.Action.Type.AGGRO;
            taunt.action.aggro = new Spell.Impact.Action.Aggro();
            taunt.action.aggro.mode = Spell.Impact.Action.Aggro.Mode.CLEAR;
            taunt.action.aggro.only_if_targeted = onlyIfTargeted;
            return taunt;
        }

        public static Spell.Impact fire(float duration) {
            var fire = new Spell.Impact();
            fire.action = new Spell.Impact.Action();
            fire.action.type = Spell.Impact.Action.Type.FIRE;
            fire.action.fire = new Spell.Impact.Action.Fire();
            fire.action.fire.duration = duration;
            return fire;
        }

        public static Spell.Impact resetCooldownActive(String spellPattern) {
            var impact = new Spell.Impact();
            impact.action = new Spell.Impact.Action();
            impact.action.type = Spell.Impact.Action.Type.COOLDOWN;
            impact.action.cooldown = new Spell.Impact.Action.Cooldown();

            impact.action.cooldown.actives = new Spell.Impact.Action.Cooldown.Modify();
            impact.action.cooldown.actives.id = spellPattern;
            impact.action.cooldown.actives.duration_multiplier = 0F; // reset cooldown

            return impact;
        }

        public static Spell.Impact resetCooldownActiveAll() {
            var impact = new Spell.Impact();
            impact.action = new Spell.Impact.Action();
            impact.action.type = Spell.Impact.Action.Type.COOLDOWN;
            impact.action.cooldown = new Spell.Impact.Action.Cooldown();

            impact.action.cooldown.actives = new Spell.Impact.Action.Cooldown.Modify();
            impact.action.cooldown.actives.id = "*"; // reset cooldown for all active spells
            impact.action.cooldown.actives.duration_multiplier = 0F; // reset cooldown

            return impact;
        }

        // MARK: Summon impacts

        /// Wraps a fully-built {@link Spell.Impact.Action.Summon} into a SUMMON impact, ready to drop
        /// straight into `spell.impacts`. This is the boilerplate every summon spell otherwise repeats
        /// by hand (new impact + action + `Type.SUMMON` + assign). Build the {@code Summon} however you
        /// like first — configuring its behaviour, attribute scaling, placements and group composition —
        /// then hand it here. See `WizardSummons` for example {@code Summon} factories.
        public static Spell.Impact summon(Spell.Impact.Action.Summon summon) {
            var impact = new Spell.Impact();
            impact.action = new Spell.Impact.Action();
            impact.action.type = Spell.Impact.Action.Type.SUMMON;
            impact.action.summon = summon;
            return impact;
        }

        /// A single-group SUMMON impact built from its parts: spawns `spawnCount` entities of
        /// `entityTypeId`, cycling through `placements` (slot `i % size`), all driven by `behaviour`.
        /// Convenience for summons that don't need group composition; for attribute scaling or group
        /// fields, build the {@link Spell.Impact.Action.Summon} yourself and use {@link #summon(Spell.Impact.Action.Summon)}.
        public static Spell.Impact summon(String entityTypeId, SummonBehaviour behaviour,
                                          List<Spell.EntityPlacement> placements, int spawnCount) {
            return summon(new Spell.Impact.Action.Summon(entityTypeId, behaviour, placements, spawnCount));
        }

        /// A grouped SUMMON impact: `groupCount` groups, each replaying the per-entity formation
        /// (`placements` cycled `spawnCount` times) translated by the next `groupPlacements` slot
        /// (slot `g % size`).
        public static Spell.Impact summon(String entityTypeId, SummonBehaviour behaviour,
                                          List<Spell.EntityPlacement> placements, int spawnCount,
                                          List<Spell.EntityPlacement> groupPlacements, int groupCount) {
            return summon(new Spell.Impact.Action.Summon(entityTypeId, behaviour, placements, spawnCount,
                    groupPlacements, groupCount));
        }

        /// A single summoned entity of `entityTypeId` at `placement`, driven by `behaviour` — the
        /// simplest case (spawn_count = 1, one placement slot).
        public static Spell.Impact summon(String entityTypeId, SummonBehaviour behaviour,
                                          Spell.EntityPlacement placement) {
            return summon(entityTypeId, behaviour, List.of(placement), 1);
        }
    }

    public static class ImpactModifiers {
        public static Spell.Impact.TargetModifier create(String entityType) {
            var condition = new Spell.TargetCondition();
            condition.entity_type = entityType;
            var modifier = new Spell.Impact.TargetModifier();
            modifier.conditions = List.of(condition);
            return modifier;
        }

        public static Spell.Impact.TargetModifier extraDamageAgainstUndead() {
            var modifier = create("#minecraft:undead");
            var powerModifier = new Spell.Impact.Modifier();
            powerModifier.power_multiplier = 0.5F;
            modifier.modifier = powerModifier;
            return modifier;
        }

        public static Spell.Impact.TargetModifier alwaysCritAgainstUndead() {
            var modifier = create("#minecraft:undead");
            var powerModifier = new Spell.Impact.Modifier();
            powerModifier.critical_chance_bonus = 1F;
            modifier.modifier = powerModifier;
            return modifier;
        }
    }

    public static class Cost {
        public static void exhaust(Spell spell, float exhaust) {
            if (spell.cost == null) {
                spell.cost = new Spell.Cost();
            }
            spell.cost.exhaust = exhaust;
        }

        public static void cooldown(Spell spell, float duration) {
            if (spell.cost == null) {
                spell.cost = new Spell.Cost();
            }
            if (spell.cost.cooldown == null) {
                spell.cost.cooldown = new Spell.Cost.Cooldown();
            }
            spell.cost.cooldown.duration = duration;
        }

        public static void cooldownGroup(Spell spell, String group) {
            if (spell.cost == null) {
                spell.cost = new Spell.Cost();
            }
            if (spell.cost.cooldown == null) {
                spell.cost.cooldown = new Spell.Cost.Cooldown();
            }
            spell.cost.cooldown.group = group;
        }

        public static void cooldownGroupWeapon(Spell spell) {
            cooldownGroup(spell, "weapon");
        }

        public static void item(Spell spell, String itemId) {
            item(spell, itemId, 1);
        }

        public static void item(Spell spell, String itemId, int amount) {
            if (spell.cost == null) {
                spell.cost = new Spell.Cost();
            }
            if (spell.cost.item == null) {
                spell.cost.item = new Spell.Cost.Item();
            }
            spell.cost.item.id = itemId;
            spell.cost.item.amount = amount;
        }
    }

    public static class Particles {
        public static ParticleBatch popUpSign(Identifier signId, Color color) {
            return new ParticleBatch(signId.toString(),
                    ParticleBatch.Shape.LINE_VERTICAL, ParticleBatch.Origin.CENTER,
                    1, 0.8F, 0.8F)
                    .scale(0.8F)
                    .color(color.toRGBA())
                    .followEntity(true);
        }

        public static ParticleBatch area(Identifier id) {
            return new ParticleBatch(id.toString(),
                    ParticleBatch.Shape.SPHERE, ParticleBatch.Origin.GROUND,
                    1, 0, 0);
        }

        public static ParticleBatch aura(Identifier id) {
            return new ParticleBatch(
                    id.toString(),
                    ParticleBatch.Shape.LINE, ParticleBatch.Origin.CENTER,
                    1, 0, 0)
                    .followEntity(true);
        }

        public static ParticleBatch[] zoneMagic(long color, Identifier contour, List<Identifier> fillers, float multiplier) {
            var particles = new ArrayList<ParticleBatch>();
            particles.add(
                    new ParticleBatch(
                            contour.toString(),
                            ParticleBatch.Shape.PIPE, ParticleBatch.Origin.GROUND,
                            3 * multiplier, 0.05F, 0.15F)
                            .color(color)
            );
            for (var filler : fillers) {
                particles.add(
                        new ParticleBatch(
                                filler.toString(),
                                ParticleBatch.Shape.PILLAR, ParticleBatch.Origin.GROUND,
                                3 * multiplier, 0.05F, 0.1F)
                                .color(color)
                );
            }
            return particles.toArray(new ParticleBatch[0]);
        }
    }

    public static class Complex {
        /**
         * Sets delivery and impact
         */
        public static void flameCloud(Spell spell, float radius, float coefficient, float timeToLive, @Nullable String attribute) {
            spell.deliver.type = Spell.Delivery.Type.CLOUD;
            spell.deliver.delay = 10;
            var cloud = new Spell.Delivery.Cloud();
            cloud.volume.radius = radius;
            cloud.volume.area.vertical_range_multiplier = 0.3F;
            cloud.volume.sound = new Sound(SpellEngineSounds.GENERIC_FIRE_IMPACT_2.id().toString());
            cloud.impact_tick_interval = 8;
            cloud.time_to_live_seconds = timeToLive;
            cloud.spawn.sound = new Sound(SpellEngineSounds.GENERIC_FIRE_IGNITE.id().toString());
            cloud.client_data = new Spell.Delivery.Cloud.ClientData();
            cloud.client_data.light_level = 15;
            cloud.client_data.particles = new ParticleBatch[] {
                    new ParticleBatch(SpellEngineParticles.flame_ground.id().toString(),
                            ParticleBatch.Shape.PILLAR, ParticleBatch.Origin.FEET,
                            3, 0, 0),
                    new ParticleBatch(SpellEngineParticles.flame_medium_a.id().toString(),
                            ParticleBatch.Shape.PILLAR, ParticleBatch.Origin.FEET,
                            2, 0.02F, 0.1F),
                    new ParticleBatch(SpellEngineParticles.flame_medium_b.id().toString(),
                            ParticleBatch.Shape.PILLAR, ParticleBatch.Origin.FEET,
                            1, 0.02F, 0.1F),
                    new ParticleBatch(SpellEngineParticles.flame_spark.id().toString(),
                            ParticleBatch.Shape.PILLAR, ParticleBatch.Origin.FEET,
                            3, 0.03F, 0.2F),
            };
            spell.deliver.clouds = List.of(cloud);

            var damage = new Spell.Impact();
            if (attribute != null) {
                damage.attribute = attribute;
            }
            damage.action = new Spell.Impact.Action();
            damage.action.type = Spell.Impact.Action.Type.DAMAGE;
            damage.action.damage = new Spell.Impact.Action.Damage();
            damage.action.damage.knockback = 0.2F;
            damage.action.damage.spell_power_coefficient = coefficient;
            damage.sound = new Sound(SpellEngineSounds.GENERIC_FIRE_IMPACT_1.id().toString());
            damage.particles = new ParticleBatch[]{
                    new ParticleBatch(SpellEngineParticles.flame.id().toString(),
                            ParticleBatch.Shape.PILLAR, ParticleBatch.Origin.FEET,
                            20, 0.05F, 0.15F),
                    new ParticleBatch(SpellEngineParticles.flame_medium_a.id().toString(),
                            ParticleBatch.Shape.PILLAR, ParticleBatch.Origin.FEET,
                            20, 0.05F, 0.15F),
            };
            spell.impacts = List.of(damage);
        }


        public static Spell.AreaImpact fireExplosion(float radius) {
            return fireExplosion(radius, radius / 2.5F);
        }

        public static Spell.AreaImpact fireExplosion(float radius, float scale) {
            var area_impact = new Spell.AreaImpact();
            area_impact.radius = radius;
            area_impact.area.distance_dropoff = Spell.Target.Area.DropoffCurve.SQUARED;
            area_impact.particles = new ParticleBatch[]{
                    new ParticleBatch(
                            SpellEngineParticles.fire_explosion.id().toString(),
                            ParticleBatch.Shape.SPHERE, ParticleBatch.Origin.CENTER,
                            1, 0, 0)
                            .scale(scale)
            };
            area_impact.sound = new Sound(SpellEngineSounds.GENERIC_FIRE_IMPACT_1.id());
            return area_impact;
        }

        public static void poisonCloud(Spell spell, float radius, float cloudDuration, float effectDuration, int effectAmplifierCap, float coefficient) {
            spell.deliver.type = Spell.Delivery.Type.CLOUD;
            spell.deliver.delay = 8;
            var cloud = new Spell.Delivery.Cloud();
            cloud.volume.radius = radius;
            cloud.volume.area.vertical_range_multiplier = 0.3F;
            cloud.volume.sound = new Sound(SpellEngineSounds.POISON_CLOUD_TICK.id().toString());
            cloud.impact_tick_interval = 8;
            cloud.time_to_live_seconds = cloudDuration;
            cloud.spawn.sound = new Sound(SpellEngineSounds.POISON_CLOUD_SPAWN.id().toString());
            cloud.client_data = new Spell.Delivery.Cloud.ClientData();
            cloud.client_data.light_level = 0;
            cloud.client_data.particles = new ParticleBatch[] {
                    new ParticleBatch(SpellEngineParticles.smoke_large.id().toString(),
                            ParticleBatch.Shape.PILLAR, ParticleBatch.Origin.FEET,
                            1, 0.01F, 0.02F)
                            .color(0x99FF66AAL),
                    new ParticleBatch(SpellEngineParticles.smoke_large.id().toString(),
                            ParticleBatch.Shape.PILLAR, ParticleBatch.Origin.FEET,
                            1, 0.01F, 0.02F)
                            .color(0x33DD33EE),
            };
            spell.deliver.clouds = List.of(cloud);

            var impact = SpellBuilder.Impacts.effectAdd("poison", effectDuration, 1, effectAmplifierCap);
            impact.action.status_effect.amplifier_cap_power_multiplier = coefficient;
            impact.action.status_effect.show_particles = true;

            impact.particles = new ParticleBatch[]{
                    new ParticleBatch(SpellEngineParticles.smoke_large.id().toString(),
                            ParticleBatch.Shape.SPHERE, ParticleBatch.Origin.CENTER,
                            0.5F, 0.01F, 0.02F)
                            .color(0x33DD33AA),
                    new ParticleBatch(
                            SpellEngineParticles.MagicParticles.get(
                                    SpellEngineParticles.MagicParticles.Shape.SKULL,
                                    SpellEngineParticles.MagicParticles.Motion.DECELERATE).id().toString(),
                            ParticleBatch.Shape.SPHERE, ParticleBatch.Origin.CENTER,
                            3, 0.1F, 0.2F)
                            .color(0x33DD33AA)
            };
            spell.impacts = List.of(impact);
        }
    }
}

package net.spell_engine.api.spell.summon;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.fx.ModelEffectHelper;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.utils.WorldScheduler;

/// Server-side spawner for {@link Summon} definitions. Content mods build a {@link Summon} (entity id,
/// behaviour, placements, group layout, spawn FX) and call {@link #spawn} from a custom impact handler;
/// this resolves the placement geometry, group composition, anti-clip line-of-sight, spawn delays and
/// spawn FX/sound generically — no content-specific code.
public class SummonSpawner {

    /// Spawns the summon(s) from a definition. `group_count` groups are spawned; each group replays
    /// the per-entity formation (`spawn_count` entities cycling through `placements`), translated by
    /// the next group placement (cycling through `group_placements`). Every entity is created by id,
    /// handed the behaviour, positioned via {@link Spell.EntityPlacement} (group offset first, then
    /// the per-entity placement on top), and pulled back to the nearest visible point when a placement
    /// opts into line-of-sight. Group and per-entity `delay_ticks` are summed and defer the actual
    /// world spawn (entities are positioned at cast time, anchored to the caster's cast-time state,
    /// matching SpellEngine's built-in SPAWN action).
    public static void spawn(Summon def, RegistryEntry<Spell> spellEntry, LivingEntity caster, SpellHelper.ImpactContext context) {
        var world = caster.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) return;

        var type = Registries.ENTITY_TYPE.get(Identifier.of(def.entity_type_id));
        for (int g = 0; g < def.group_count; g++) {
            // Next group slot, wrapping around the list (null when no group offset is configured).
            var groupPlacement = def.group_placements.isEmpty() ? null : def.group_placements.get(g % def.group_placements.size());
            int groupDelay = groupPlacement != null ? groupPlacement.delay_ticks : 0;
            Vec3d groupAnchor = null; // caster position + group offset; captured from the first entity

            for (int i = 0; i < def.spawn_count; i++) {
                var created = (Entity) type.create(world);
                if (!(created instanceof SpellSummoned summoned)) return;

                // Next per-entity slot, wrapping around the list (null when no slots are configured).
                var placement = def.placements.isEmpty() ? null : def.placements.get(i % def.placements.size());

                summoned.onSummonedBySpell(new SpellSummoned.Args(caster, spellEntry, def.behaviour, context));

                // Compose placements: the group offset's resulting position seeds the per-entity
                // placement (both rotate the look-offset by the caster's yaw, so the formation keeps
                // a consistent caster-relative orientation across groups).
                var origin = caster.getPos();
                if (groupPlacement != null) {
                    SpellHelper.applyEntityPlacement(created, caster, origin, groupPlacement);
                    origin = created.getPos();
                }
                if (i == 0) groupAnchor = origin; // the group's anchor (pre per-entity offset)
                SpellHelper.applyEntityPlacement(created, caster, origin, placement);

                // applyEntityPlacement only sets entity yaw; sync head/body yaw so the initial pose matches.
                boolean appliedYaw = (groupPlacement != null && groupPlacement.apply_yaw)
                        || (placement != null && placement.apply_yaw);
                if (appliedYaw && created instanceof LivingEntity living) {
                    living.setHeadYaw(living.getYaw());
                    living.setBodyYaw(living.getYaw());
                }
                // Anti-clip: when a contributing placement opts in via `line_of_sight`, and the placed
                // position is out of the caster's line of sight (e.g. behind a wall), pull it back
                // along the sightline to the closest point that is still visible.
                boolean checkLineOfSight = (placement != null && placement.line_of_sight)
                        || (groupPlacement != null && groupPlacement.line_of_sight);
                if (checkLineOfSight) {
                    var visible = nearestVisiblePosition(caster, created.getPos(), serverWorld);
                    created.setPosition(visible.x, visible.y, visible.z);
                }

                // Defer the world spawn by the combined group + per-entity delay (0 = spawn this tick).
                int entityDelay = placement != null ? placement.delay_ticks : 0;
                ((WorldScheduler) serverWorld).schedule(groupDelay + entityDelay, () -> serverWorld.spawnEntity(created));
            }

            // Group spawn FX + sound: one-shot at the group anchor, deferred by the group delay.
            if (groupAnchor != null && (def.group_spawn_fx != null || def.group_spawn_sound != null)) {
                var anchor = groupAnchor;
                var fx = def.group_spawn_fx;
                var sound = def.group_spawn_sound;
                ((WorldScheduler) serverWorld).schedule(groupDelay, () -> {
                    if (fx != null) emitGroupSpawnFx(serverWorld, caster, anchor, fx);
                    if (sound != null) playSoundAt(serverWorld, anchor, sound);
                });
            }
        }
    }

    /// Emits a one-shot visual FX bundle at a fixed location (the group anchor): particles via a
    /// tracker packet to the caster's viewers, and model effects as self-syncing entities.
    private static void emitGroupSpawnFx(ServerWorld world, LivingEntity caster, Vec3d anchor, SummonFx fx) {
        if (fx.particles != null && fx.particles.length > 0) {
            ParticleHelper.sendBatches(anchor, caster, fx.particles);
        }
        ModelEffectHelper.spawn(world, anchor, caster.getYaw(), fx.model_fx);
    }

    /// Plays a sound at a fixed world position.
    private static void playSoundAt(ServerWorld world, Vec3d pos, Sound sound) {
        var soundEvent = Registries.SOUND_EVENT.get(Identifier.of(sound.id()));
        if (soundEvent != null) {
            world.playSound(null, pos.x, pos.y, pos.z, soundEvent,
                    SoundCategory.PLAYERS, sound.volume(), sound.randomizedPitch());
        }
    }

    /// Distance the result is pulled back from a blocking surface along the sightline, so the entity
    /// sits just shy of the geometry rather than embedded in its face.
    private static final double LOS_SURFACE_BACKOFF = 0.5;

    /// The point along the segment from the caster's eyes to `desired` that is still in line of sight:
    /// `desired` itself when the path is unobstructed, otherwise the closest clear point just before
    /// the blocking surface (pulled back `LOS_SURFACE_BACKOFF` blocks off the face). Never returns a
    /// point behind the caster's eyes.
    private static Vec3d nearestVisiblePosition(LivingEntity caster, Vec3d desired, ServerWorld world) {
        var from = caster.getEyePos();
        var hit = world.raycast(new RaycastContext(
                from, desired,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                caster));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return desired; // unobstructed line of sight
        }
        var ray = desired.subtract(from);
        var length = ray.length();
        if (length < 1.0e-4) {
            return from;
        }
        var candidate = hit.getPos().subtract(ray.multiply(LOS_SURFACE_BACKOFF / length));
        // Guard against a surface right at the caster's face pushing the point behind the eyes.
        if (candidate.subtract(from).dotProduct(ray) < 0) {
            return from;
        }
        return candidate;
    }
}

package net.spell_engine.internals.delivery;

import net.minecraft.entity.SpawnReason;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.entity.SpellCloud;
import net.spell_engine.fx.ModelEffectHelper;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.internals.SpellExecution.ImpactContext;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.WorldScheduler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Places the CLOUD delivery: one lingering {@link SpellCloud} entity per authored cloud and
/// placement, with spell modifiers folded into a snapshotted cloud bonus and the actual world spawn
/// deferred by the placement's delay.
public class CloudPlacer {

    public static void placeCloud(World world, LivingEntity caster,
                                  @Nullable Entity target, @Nullable Vec3d location,
                                  RegistryEntry<Spell> spellEntry, ImpactContext context) {
        var spell = spellEntry.value();
        var clouds = spell.deliver.clouds;
        if (clouds == null || clouds.isEmpty()) {
            return;
        }
        if (target == null && location == null) {
            target = caster;
        }

        List<Spell.Modifier> spellModifiers = SpellModifiers.of(caster, spellEntry, context.chargeModifier());
        float extraTimeToLive = 0;
        var extraPlacements = new ArrayList<Spell.EntityPlacement>();
        // Summed cloud bonus, snapshotted onto each cloud below. Magnitudes accumulate; growth timing is
        // taken from the first modifier that actually contributes growth (see radiusForAge merge rules).
        var cloudModifier = new Spell.Modifier.Cloud();
        boolean cloudTimingSet = false;
        for (var spellModifier: spellModifiers) {
            extraTimeToLive += spellModifier.spawn_duration_add;
            extraPlacements.addAll(spellModifier.additional_placements);
            var cm = spellModifier.cloud;
            if (cm != null) {
                cloudModifier.radius_add += cm.radius_add;
                cloudModifier.growth.radius_step += cm.growth.radius_step;
                if (cm.growth.duration_ticks < 0 || cloudModifier.growth.duration_ticks < 0) {
                    cloudModifier.growth.duration_ticks = -1; // "whole life" sentinel wins over any span
                } else {
                    cloudModifier.growth.duration_ticks += cm.growth.duration_ticks;
                }
                if (!cloudTimingSet && cm.growth.radius_step != 0F) {
                    cloudModifier.growth.step_interval = cm.growth.step_interval;
                    cloudModifier.growth.start_tick = cm.growth.start_tick;
                    cloudTimingSet = true;
                }
            }
        }

        var index = 0;
        for (var cloud: clouds) {
            var placements = new ArrayList<Spell.EntityPlacement>();
            placements.add(cloud.placement);
            placements.addAll(cloud.additional_placements);
            if (index == 0) {
                placements.addAll(extraPlacements);
            }
            var base_delay = cloud.delay_ticks;

            for (var placement: placements) {
                var delay = base_delay + placement.delay_ticks;

                SpellCloud entity;
                if (cloud.entity_type_id != null) {
                    var id = Identifier.of(cloud.entity_type_id);
                    var type = Registries.ENTITY_TYPE.get(id);
                    entity = (SpellCloud) type.create(world, SpawnReason.MOB_SUMMONED);
                } else {
                    entity = new SpellCloud(world);
                }
                entity.setOwner(caster);
                entity.onCreatedFromSpell(spellEntry.getKey().get().getValue(), cloud, context, cloud.time_to_live_seconds + extraTimeToLive, cloudModifier);

                if (target != null) {
                    EntityPlacements.applyEntityPlacement(entity, target, target.getEntityPos(), placement);
                } else if (location != null) {
                    EntityPlacements.applyEntityPlacement(caster.getEntityWorld(), entity,
                            caster.getYaw(), caster.getPitch(), null,
                            location, placement);
                } else {
                    continue;
                }


                ((WorldScheduler)world).schedule(delay, () -> {
                    world.spawnEntity(entity);
                    var sound = cloud.spawn.sound;
                    if (sound != null) {
                        SoundHelper.playSound(world, entity, sound);
                    }
                    var spawnVisuals = cloud.spawn.visuals.resolved(Fx.Context.NONE);
                    ParticleHelper.sendBatches(entity, spawnVisuals.particles);
                    ModelEffectHelper.spawn(world, entity.getEntityPos(), entity.getYaw(), spawnVisuals.models, null);
                });

                if (cloud.placement_delay_stacks) {
                    base_delay = delay;
                }
            }
            index += 1;
        }
    }
}

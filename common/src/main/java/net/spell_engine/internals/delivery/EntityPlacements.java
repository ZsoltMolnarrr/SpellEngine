package net.spell_engine.internals.delivery;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.utils.TargetHelper;
import org.jetbrains.annotations.Nullable;

/// Positions an entity a spell puts into the world — a summon, a spawn, a cloud — according to a
/// {@link Spell.EntityPlacement}, with a safeguard against placements that resolve inside terrain.
public class EntityPlacements {

    /// Maximum upward nudge, in blocks, applied to a placed entity that would otherwise land embedded
    /// in terrain. Deliberately small: this is a safeguard against a bad placement, not a general
    /// free-space search (which would also have to look sideways).
    private static final int PLACEMENT_LIFT_LIMIT = 2;

    public static void applyEntityPlacement(Entity entity, Entity target, Vec3d initialPosition, Spell.EntityPlacement placement) {
        applyEntityPlacement(target.getWorld(), entity, target.getYaw(), target.getPitch(), target, initialPosition, placement);
    }

    public static void applyEntityPlacement(World world, Entity placedEntity,
                                            float targetedYaw, float targetedPitch, @Nullable Entity rayCastEntity,
                                            Vec3d initialPosition, Spell.EntityPlacement placement) {
        var position = initialPosition;
        if (placement != null) {
            if (placement.location_offset_by_look > 0) {
                float yaw = targetedYaw + placement.location_yaw_offset;
                position = position.add(Vec3d.fromPolar(0, yaw).multiply(placement.location_offset_by_look));
            }
            position = position.add(new Vec3d(placement.location_offset_x, placement.location_offset_y, placement.location_offset_z));
            if (placement.force_onto_ground) {
                var searchPosition = position;
                var blockPos = BlockPos.ofFloored(searchPosition.getX(), searchPosition.getY(), searchPosition.getZ());
                if (world.getBlockState(blockPos).isSolid()) {
                    searchPosition = searchPosition.add(0, 2, 0);
                }
                var groundPosBelow = TargetHelper.findSolidBlockBelow(rayCastEntity, searchPosition, world, -20);
                position = groundPosBelow != null ? groundPosBelow : position;
            }
            if (placement.apply_yaw) {
                placedEntity.setYaw(targetedYaw);
            }
            if (placement.apply_pitch) {
                placedEntity.setPitch(targetedPitch);
            }
        }
        // Safeguard against a placement that resolved inside terrain — a look-offset pushed into a
        // wall, a formation slot fanned into a hillside, `force_onto_ground` finding a floor that is
        // itself buried. No-op whenever the placement already fits.
        position = liftedOutOfBlocks(world, placedEntity, position);
        placedEntity.setPosition(position.getX(), position.getY(), position.getZ());
    }

    /// Whether `box` is clear of **block** collisions for `entity`.
    ///
    /// Intentionally not `World.isSpaceEmpty`, which also counts *entity* collisions: placements are
    /// routinely anchored on top of the caster (a zero look-offset puts the entity at their feet), and
    /// an entity-aware check would read that overlap as "blocked" and nudge every such placement
    /// skyward. Only terrain should move a placement.
    private static boolean fitsBetweenBlocks(World world, Entity entity, Box box) {
        for (var shape : world.getBlockCollisions(entity, box)) {
            if (!shape.isEmpty()) return false;
        }
        return true;
    }

    /// Lifts `position` just enough to free `entity`'s bounding box from terrain, by at most
    /// {@link #PLACEMENT_LIFT_LIMIT} blocks. Returns `position` unchanged when it already fits, when no
    /// lift within the limit frees it, or when the entity ignores block collision entirely — `noClip`
    /// entities (spell clouds, model effects) are placed inside geometry on purpose.
    ///
    /// Conservative by design: a placement that already works is never moved, so authored formations
    /// keep their tuned geometry. Only vertical embedding is recovered — a look-offset pushed sideways
    /// into a wall face is not resolved here (`EntityPlacement.line_of_sight` is the existing opt-in
    /// for that case).
    private static Vec3d liftedOutOfBlocks(World world, Entity entity, Vec3d position) {
        if (entity.noClip) return position;
        var dimensions = entity.getDimensions(entity.getPose());
        if (fitsBetweenBlocks(world, entity, dimensions.getBoxAt(position))) return position;
        for (int lift = 1; lift <= PLACEMENT_LIFT_LIMIT; lift++) {
            var candidate = position.add(0, lift, 0);
            if (fitsBetweenBlocks(world, entity, dimensions.getBoxAt(candidate))) return candidate;
        }
        return position;
    }
}

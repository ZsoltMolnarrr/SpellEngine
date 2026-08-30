package net.spell_engine.entity;

import com.google.gson.Gson;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.client.render.FlyingSpellEntity;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.utils.PatternMatching;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.VectorHelper;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import net.spell_engine.internals.delivery.melee.OrientedBoundingBox;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
import net.spell_engine.internals.target.SpellIntents;

public class SpellProjectile extends Projectile implements FlyingSpellEntity {
    public static EntityType<SpellProjectile> ENTITY_TYPE;
    private static Random random = new Random();

    public float range = 128;
    private Spell.ProjectileData.Perks perks;
    private SpellExecution.ImpactContext context;
    public Vec3 previousVelocity;

    public SpellProjectile(EntityType<? extends Projectile> entityType, Level world) {
        super(entityType, world);
    }

    protected SpellProjectile(Level world, LivingEntity owner) {
        super(ENTITY_TYPE, world);
        this.setOwner(owner);
    }

    public enum Behaviour {
        FLY, FALL
    }

    public SpellProjectile(Level world, LivingEntity caster, double x, double y, double z,
                           Behaviour behaviour, Holder<Spell> spellEntry, SpellExecution.ImpactContext context, Spell.ProjectileData.Perks mutablePerks) {
        this(world, caster);
        this.setPos(x, y, z);

        this.setBehaviour(behaviour);
        this.setSpell(spellEntry);
        this.perks = mutablePerks;
        this.context = context;

        var projectileData = projectileData();
        // Capture the held item once if any model wants to render as it.
        if (projectileData.client_data != null && projectileData.client_data.composite_model != null
                && projectileData.client_data.composite_model.models.stream().anyMatch(m -> m.use_held_item)) {
            setItemStackModel(caster.getMainHandItem());
        }
    }

    /**
     * A copy of the spell projectile perks, can be safely modified
      */
    public Spell.ProjectileData.Perks mutablePerks() {
        return perks;
    }

    public Spell.ProjectileData projectileData() {
        var spellEntry = getSpellEntry();
        if (spellEntry == null) {
            return null;
        }
        var spell = getSpellEntry().value();
        var release = spell.deliver;
        switch (release.type) {
            case PROJECTILE -> {
                return release.projectile.projectile;
            }
            case METEOR -> {
                return release.meteor.projectile;
            }
        }
        assert true;
        return null;
    }

    public void setVelocity(double x, double y, double z, float speed, float spread, float divergence) {
        var rotX = Math.toRadians(divergence * random.nextFloat(spread, 1F));
        var rotY = Math.toRadians(360 * random.nextFloat());
        Vec3 vec3d = (new Vec3(x, y, z))
                .xRot((float) rotX)
                .yRot((float) rotY)
                .scale(speed);
        this.setDeltaMovement(vec3d);
        double d = vec3d.horizontalDistance();
        this.setYRot((float)(Mth.atan2(vec3d.x, vec3d.z) * 57.2957763671875));
        this.setXRot((float)(Mth.atan2(vec3d.y, d) * 57.2957763671875));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    private boolean hasCustomDimensions = false;
    public EntityDimensions getDimensions(Pose pose) {
        var data = projectileData();
        if (data != null && data.hitbox != null) {
            this.hasCustomDimensions = true;
            var scale = getScaleMultiplier();
            var width = data.hitbox.width * scale;
            var height = data.hitbox.height * scale;
            return EntityDimensions.scalable(width, height);
        } else {
            return super.getDimensions(pose);
        }
    }

    public Entity getFollowedTarget() {
        Entity entityReference = null;
        if (level().isClientSide()) {
            var id = this.getEntityData().get(TRACKER_TARGET_ID);
            if (id != null && id > 0) {
                entityReference = level().getEntity(id);
            }
        } else {
            entityReference = followedTarget;
        }
        if (entityReference != null && entityReference.isAttackable() && entityReference.isAlive()) {
            return entityReference;
        }
        return entityReference;
    }

//    @Override
//    public void setVelocityClient(double x, double y, double z) {
//        super.setVelocityClient(x, y, z);
//    }

    public boolean shouldRenderAtSqrDistance(double distance) {
        double d0 = this.getBoundingBox().getSize() * 4.0;
        if (Double.isNaN(d0)) {
            d0 = 4.0;
        }

        d0 *= 128.0;
        var result =  distance < d0 * d0;
        return result;
    }

    private boolean skipTravel = false;

    public void tick() {
        skipTravel = false;
        Entity entity = this.getOwner();
        var behaviour = getBehaviour();
        var spellEntry = getSpellEntry();
        if (!this.level().isClientSide()) {
            // Server side
            if (spellEntry == null) {
                System.err.println("Spell Projectile safeguard termination, failed to resolve spell: " + spellId());
                this.discard();
                return;
            }
            switch (behaviour) {
                case FLY -> {
                    if (moveDist >= range || tickCount > 1200) { // 1200 ticks = 1 minute
                        this.discard();
                        return;
                    }
                }
                case FALL -> {
                    var fallDistance = range * 0.98F;
                    if (moveDist >= fallDistance) {
                        // Travel advances in whole velocity steps, so this threshold is overshot
                        // by up to a full step (e.g. velocity 1.5 against a 9.8 threshold ends
                        // 0.7 too low — below the aimed surface). Snap back along the travel
                        // direction so the impact and its area FX land where the threshold was
                        // actually crossed.
                        var overshoot = moveDist - fallDistance;
                        var velocity = this.getDeltaMovement();
                        if (overshoot > 0 && velocity.lengthSqr() > 1e-6) {
                            this.setPos(this.position().subtract(velocity.normalize().scale(overshoot)));
                        }
                        finishFalling();
                        this.discard();
                        return;
                    }
                    if (tickCount > 1200) { // 1200 ticks = 1 minute
                        this.discard();
                        return;
                    }
                }
            }
            if (moveDist >= range || tickCount > 1200) { // 1200 ticks = 1 minute
                this.discard();
                return;
            }
        }
        this.previousVelocity = new Vec3(getDeltaMovement().x, getDeltaMovement().y, getDeltaMovement().z);
        if (this.level().isClientSide() || (entity == null || !entity.isRemoved()) && this.level().hasChunkAt(this.blockPosition())) {
            super.tick();

            if (!level().isClientSide()) {
                HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
                var data = projectileData();
                if (data != null && data.hitbox != null) {
                    if (hitResult.getType() == HitResult.Type.BLOCK) {
                        // Block hit: handle normally (preserves bounce/block-impact)
                        handleHitResult(hitResult, behaviour, spellEntry);
                    } else {
                        // Entity/miss from raycast: discard, use swept OBB detection instead
                        performVolumetricEntityCollision(behaviour, spellEntry, data);
                    }
                } else {
                    // RAYCAST mode (default) — original path with intersects bug fixed
                    handleHitResult(hitResult, behaviour, spellEntry);
                    if (hitResult.getType() == HitResult.Type.MISS && hasCustomDimensions) {
                        var boundingBox = this.getBoundingBox();
                        for (Entity areaTarget : this.level().getEntities(entity, this.getBoundingBox().inflate(1), this::canHitEntity)) {
                            if (areaTarget.getBoundingBox().intersects(boundingBox)) {
                                var areaHitResult = new EntityHitResult(areaTarget);
                                handleHitResult(areaHitResult, behaviour, spellEntry);
                            }
                        }
                    }
                }
            }

            this.applyEffectsFromBlocks();

            // Travel
            if (!skipTravel) {
                this.followTarget();
                // Flight physics (gravity/drag) run after homing and before the move, so homing
                // aims first and drag then damps the whole velocity (vanilla order). May expire
                // the projectile via `min_speed`.
                applyMotion();
                if (this.isRemoved()) {
                    return;
                }
                Vec3 velocity = this.getDeltaMovement();
                double d = this.getX() + velocity.x;
                double e = this.getY() + velocity.y;
                double f = this.getZ() + velocity.z;
                ProjectileUtil.rotateTowardsMovement(this, 0.2F);

                if (this.isInWater()) {
                    for(int i = 0; i < 4; ++i) {
                        this.level().addParticle(ParticleTypes.BUBBLE, d - velocity.x * 0.25, e - velocity.y * 0.25, f - velocity.z * 0.25, velocity.x, velocity.y, velocity.z);
                    }
                }

                var data = projectileData();
                if (data != null) {
                    if (level().isClientSide()) {
                        for (var travel_particles : data.client_data.travel_particles) {
                            ParticleHelper.play(level(), this, getYRot(), getXRot(), travel_particles);
                        }
                    } else {
                        if (data.travel_sound != null && tickCount % data.travel_sound_interval == 0) {
                            SoundHelper.playSound(level(), this, data.travel_sound);
                        }
                    }
                }

                this.setPos(d, e, f);
                this.moveDist += velocity.length();
            }
        } else {
            this.discard();
        }
    }

    private void handleHitResult(HitResult hitResult, Behaviour behaviour, Holder<Spell> spellEntry) {
        if (hitResult.getType() != HitResult.Type.MISS) {
            switch (behaviour) {
                case FLY -> {
                    boolean shouldCollideWithEntity = true;
                    if (hitResult.getType() == HitResult.Type.ENTITY) {
                        var target = ((EntityHitResult) hitResult).getEntity();
                        var spell = spellEntry.value();
                        if (SpellEngineMod.config.projectiles_pass_thru_irrelevant_targets
                                && spell != null
                                && !spell.impacts.isEmpty()
                                && !impactHistory.contains(target.getId())
                                && getOwner() instanceof LivingEntity owner) {
                            var intents = SpellIntents.impactIntents(spell);

                            boolean intentAllows = false;
                            for (var intent: intents) {
                                intentAllows = intentAllows || EntityRelations.actionAllowed(SpellTarget.FocusMode.DIRECT, intent, owner, target);
                            }
                            shouldCollideWithEntity = intentAllows;
                        }
                    }
                    if (shouldCollideWithEntity) {
                        this.onHit(hitResult);
                    } else {
                        this.setFollowedTarget(null);
                    }
                }
                case FALL -> {
                    if (hitResult.getType() == HitResult.Type.ENTITY) {
                        var target = ((EntityHitResult) hitResult).getEntity();
                        // Falling projectiles land on entities that are solid to them
                        // (spell barriers, boats, shulkers, ...), and keep falling through the rest.
                        if (target.canBeCollidedWith(this)) {
                            this.finishFalling();
                        }
                    }
                }
            }
        }
    }

    private void performVolumetricEntityCollision(
            Behaviour behaviour,
            Holder<Spell> spellEntry,
            Spell.ProjectileData data) {

        // 1. Determine OBB dimensions from hitbox (caller guarantees hitbox is non-null)
        var hitbox = data.hitbox;
        var scale = getScaleMultiplier();
        float obbWidth  = hitbox.width * scale;
        float obbHeight = hitbox.height * scale;
        float obbLength = ((hitbox.length > 0) ? hitbox.length : hitbox.width) * scale;
        Vec3 obbCenter = this.position().add(this.getDeltaMovement().normalize().scale(obbLength));

        // point backward and miss targets that are clearly in the travel path.
        float obbYaw = this.getYRot();
        float obbPitch = this.getXRot();

        // NOTE: OBB constructor param order is (pitch_value, yaw_value) — matches fromPolar(pitch, yaw)
        var obb = new OrientedBoundingBox(
                obbCenter, obbWidth, obbHeight, obbLength,
                obbPitch, obbYaw);
        obb.updateVertex();

        var effectiveLength = Math.max(obbWidth, obbLength);

        // 3. Broad-phase: query candidate entities in a conservative search box
        double searchRadius = effectiveLength / 2.0 + Math.max(obbWidth, obbHeight) / 2.0 + 1.0;
        var broadPhaseBox = this.getBoundingBox().inflate(searchRadius);
        List<Entity> candidates = this.level().getEntities(
                this, broadPhaseBox, this::canHitEntity);

//        // === DEBUG LOG ===
//        if (age <= 60) {
//            System.out.println("[VOBB] t=" + age
//                    + " p=" + fmt(getPos()) + " pp=" + fmtRaw(prevX, prevY, prevZ)
//                    + " y=" + String.format("%.2f", getYaw()) + "(vy=" + String.format("%.2f", obbYaw) + ")"
//                    + " pt=" + String.format("%.2f", getPitch()) + "(vpt=" + String.format("%.2f", obbPitch) + ")");
//            System.out.println("[VOBB]  c=" + fmt(obbCenter)
//                    + " e(w=" + String.format("%.2f", obbWidth) + ",h=" + String.format("%.2f", obbHeight) + ",l=" + String.format("%.2f", effectiveLength) + ")"
//                    + " td=" + String.format("%.2f", 0F));
//            var bb = broadPhaseBox;
//            System.out.println("[VOBB]  bb=[" + String.format("%.2f,%.2f,%.2f->%.2f,%.2f,%.2f", bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ) + "] n=" + candidates.size());
//            for (Entity c : candidates) {
//                var cb = c.getBoundingBox().expand(c.getTargetingMargin());
//                var cc = c.getPos().add(0, c.getHeight() / 2.0, 0);
//                boolean ix = obb.intersects(cb);
//                boolean cn = obb.contains(cc);
//                System.out.println("[VOBB]   e=" + c.getName().getString()
//                        + " ep=" + fmt(c.getPos())
//                        + " d=" + String.format("%.2f", c.distanceTo(this))
//                        + " h=" + impactHistory.contains(c.getId())
//                        + " ix=" + ix + " cn=" + cn);
//            }
//        }
//        // === END DEBUG LOG ===

        if (candidates.isEmpty()) return;

        // 4. Narrow-phase: SAT test (OBB vs entity AABB, plus center-point containment check)
        List<Entity> hits = new ArrayList<>();
        for (Entity candidate : candidates) {
            if (obb.intersects(candidate.getBoundingBox().inflate(candidate.getPickRadius()))
                    || obb.contains(candidate.position().add(0, candidate.getBbHeight() / 2.0, 0))) {
                hits.add(candidate);
            }
        }
        if (hits.isEmpty()) return;

        // 5. Sort by distance to mid-center: nearest processed first (pierce/ricochet consistency)
        hits.sort(Comparator.comparingDouble(e -> e.distanceToSqr(obbCenter)));

        // 6. Process hits sequentially; stop if projectile is killed mid-loop
        for (Entity hitEntity : hits) {
            if (this.isRemoved()) break;
            if (impactHistory.contains(hitEntity.getId())) continue;
            handleHitResult(new EntityHitResult(hitEntity), behaviour, spellEntry);
        }
    }

//    private static String fmt(Vec3d v) {
//        return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
//    }
//    private static String fmtRaw(double x, double y, double z) {
//        return String.format("(%.2f, %.2f, %.2f)", x, y, z);
//    }

    private void finishFalling() {
        Entity owner = this.getOwner();
        if (owner == null || owner.isRemoved()) {
            return;
        }
        if (owner instanceof LivingEntity livingEntity) {
            SpellImpacts.fallImpact(livingEntity, this, this.getSpellEntry(), context.position(this.position()));
        }
    }

    private int followTicks = 0;
    private void followTarget() {
        var target = getFollowedTarget();
        var data = projectileData();
        if (data == null) {
            return;
        }
        var homing_angle = projectileData().homing_angle;
        if (projectileData().homing_angles != null && followTicks < projectileData().homing_angles.length) {
            homing_angle = projectileData().homing_angles[followTicks];
        }
        if (target != null && homing_angle > 0) {
            if (data.homing_after_relative_distance > 0 || data.homing_after_absolute_distance > 0) {
                var shouldFollow = moveDist >= (distanceToFollow * data.homing_after_relative_distance)
                        || moveDist >= data.homing_after_absolute_distance;
                if (!shouldFollow) {
                    return;
                }
            }
//            System.out.println((this.getWorld().isClient ? "Client: " : "Server: ") + "Following target: " + target + " with angle: " + homing_angle);
            var distanceVector = (target.position().add(0, target.getBbHeight() / 2F, 0))
                    .subtract(this.position().add(0, this.getBbHeight() / 2F, 0));
//            System.out.println((world.isClient ? "Client: " : "Server: ") + "Distance: " + distanceVector);
//            System.out.println((world.isClient ? "Client: " : "Server: ") + "Velocity: " + getVelocity());
            var newVelocity = VectorHelper.rotateTowards(getDeltaMovement(), distanceVector, homing_angle);
            if (newVelocity.lengthSqr() > 0) {
//                System.out.println((world.isClient ? "Client: " : "Server: ") + "Rotated to: " + newVelocity);
                this.setDeltaMovement(newVelocity);
                // this.velocityDirty = true;
                followTicks += 1;
            }
        }
    }

    /// Applies optional flight physics (`Spell.ProjectileData.Motion`) to the velocity for this
    /// tick: gravity, then medium-dependent drag. FLY behaviour only — FALL (meteor) keeps its
    /// straight-line descent, and its `range * 0.98` fall-distance math must not be perturbed.
    /// May `kill()` the projectile when its speed decays below `min_speed`.
    private void applyMotion() {
        if (getBehaviour() != Behaviour.FLY) {
            return;
        }
        var data = projectileData();
        if (data == null || data.motion == null) {
            return;
        }
        var motion = data.motion;

        // Resolve the current medium by sampling the FluidState at the projectile's block.
        // Empty = air; any non-empty state (water, lava, modded honey, ...) counts as a fluid.
        // `drag` here is the fraction of speed lost per tick (0 = constant speed).
        float drag = motion.drag;
        float gravityMultiply = 1F;
        var fluidState = level().getFluidState(blockPosition());
        if (!fluidState.isEmpty()) {
            boolean matched = false;
            var fluidEntry = fluidState.getType().builtInRegistryHolder();
            for (var override : motion.fluid_overrides) {
                if (PatternMatching.matches(fluidEntry, Registries.FLUID, override.fluid)) {
                    drag = override.drag;
                    gravityMultiply = override.gravity_multiply;
                    matched = true;
                    break;
                }
            }
            if (!matched && motion.drag_fluid > 0F) {
                drag = motion.drag_fluid;
            }
        }

        var velocity = this.getDeltaMovement();
        if (motion.gravity != 0F) {
            velocity = velocity.subtract(0, motion.gravity * gravityMultiply, 0);
        }
        if (drag != 0F) {
            // Retained fraction; clamp at 0 so drag > 1 fully stops rather than reversing.
            velocity = velocity.scale(Math.max(0F, 1F - drag));
        }
        this.setDeltaMovement(velocity);

        if (motion.min_speed > 0F && velocity.length() < motion.min_speed) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        if (!level().isClientSide()) {
            var target = entityHitResult.getEntity();
            if (target != null
                    && !impactHistory.contains(target.getId())
                    && this.getOwner() != null
                    && this.getOwner() instanceof LivingEntity caster) {
                setFollowedTarget(null);
                var context = this.context;
                if (context == null) {
                    context = new SpellExecution.ImpactContext();
                    var spell = this.getSpellEntry().value();
                    if (getOwner() instanceof Player player && spell != null)  {
                        context = context.power(SpellPower.getSpellPower(spell.school, player));
                    }
                }
                if (context.power() == null) {
                    this.discard();
                    return;
                }

                var prevProjectilePos = new Vec3(this.xo, this.yo, this.zo);
                var hitVector = entityHitResult.getLocation().subtract(prevProjectilePos).normalize().scale(this.getBbWidth() * 0.5F);
                var hitPosition = entityHitResult.getLocation().subtract(hitVector);

                var performed = SpellImpacts.projectileImpact(caster, this, target, this.getSpellEntry(), context.position(hitPosition));
                if (performed) {
                    chainReactionFrom(target);
                    if (ricochetFrom(target, caster)) {
                        return;
                    }
                    if (pierced(target)) {
                        return;
                    }
                    this.discard();
                }
            }
        }
    }

    // MARK: Perks
    protected Set<Integer> impactHistory = new HashSet<>();

    /**
     * Returns `true` if a new target is found to ricochet to
     */
    protected boolean ricochetFrom(Entity target, LivingEntity caster) {
        if (this.perks == null
                || this.perks.ricochet <= 0) {
            return false;
        }
        impactHistory.add(target.getId());

        // Find next target
        var box = this.getBoundingBox().inflate(
                this.perks.ricochet_range,
                this.perks.ricochet_range,
                this.perks.ricochet_range);
        var spell = this.getSpellEntry().value();
        var intents = SpellIntents.impactIntents(spell);
        Predicate<Entity> intentMatches = (entity) -> {
            boolean intentAllows = false;
            for (var intent: intents) {
                intentAllows = intentAllows || EntityRelations.actionAllowed(SpellTarget.FocusMode.AREA, intent, caster, entity);
            }
            return intentAllows;
        };
        var otherTargets = this.level().getEntities(this, box, (entity) -> {
            return entity.isAttackable()
                    && entity instanceof LivingEntity // Avoid targeting unliving entities like other projectiles
                    && !impactHistory.contains(entity.getId())
                    && intentMatches.test(entity)
                    && !entity.position().equals(target.position());
        });
        if (otherTargets.isEmpty()) {
            this.setFollowedTarget(null);
            return false;
        }

        otherTargets.sort(Comparator.comparingDouble(o -> o.distanceToSqr(target)));

        // Set trajectory
        var newTarget = otherTargets.get(0);
        this.setPos(target.position().add(0, target.getBbHeight() * 0.5F, 0));
        this.setFollowedTarget(newTarget);

        var distanceVector = (newTarget.position().add(0, newTarget.getBbHeight() / 2F, 0))
                .subtract(this.position().add(0, this.getBbHeight() / 2F, 0));
        var newVelocity = distanceVector.normalize().scale(this.getDeltaMovement().length());
        this.setDeltaMovement(newVelocity);
        this.needsSync = true;

        this.perks.ricochet -= 1;
        if (this.perks.bounce_ricochet_sync) {
            this.perks.bounce -= 1;
        }
        return true;
    }

    /**
     * Returns `true` if projectile can continue to travel
     */
    private boolean pierced(Entity target) {
        if (this.perks == null
                || this.perks.pierce <= 0) {
            return false;
        }
        // Save
        impactHistory.add(target.getId());
        setFollowedTarget(null);
        this.perks.pierce -= 1;

        // Modify velocity by a tiny, non zero amount
        // to enforce velocity update on the client.
        // (Otherwise the projectile is going crazy on the client)
        var tiny = 0.01 * ((-1) * (this.perks.pierce % 2));
        this.setDeltaMovement(this.getDeltaMovement().scale(1 + tiny));
        this.needsSync = true;

        return true;
    }

    private boolean bounceFrom(BlockHitResult blockHitResult) {
        if (this.perks == null
                || this.perks.bounce <= 0) {
            return false;
        }

        var previousPosition = position();
        var previousDirection = getDeltaMovement();
        var impactPosition = blockHitResult.getLocation();
        var impactSide = blockHitResult.getDirection();
        var speed = getDeltaMovement().length();

        Vec3 surfaceNormal = getSurfaceNormal(impactSide);
        Vec3 newDirection = calculateBounceVector(previousDirection, surfaceNormal);

        // Calculate the remaining distance the projectile should travel after bouncing
        double remainingDistance = previousDirection.length() - (impactPosition.subtract(previousPosition)).length();

        // Calculate the final position after the remaining distance
        Vec3 finalPosition = impactPosition.add(newDirection.normalize().scale(remainingDistance));

        // Set the new position and velocity
        this.setPosRaw(finalPosition.x(), finalPosition.y(), finalPosition.z());
        // Reflection preserves magnitude, so `newDirection` already has length == `speed`.
        // Re-normalize before applying the speed to avoid squaring it on every bounce.
        this.setDeltaMovement(newDirection.normalize().scale(speed));
        ProjectileUtil.rotateTowardsMovement(this, 0.2F);

        this.perks.bounce -= 1;
        if (this.perks.bounce_ricochet_sync) {
            this.perks.ricochet -= 1;
        }
        this.needsSync = true;
        this.skipTravel = true;
        return true;
    }

    public Vec3 calculateBounceVector(Vec3 previousDirection, Vec3 normal) {
        // Calculate the reflection of the incident vector with respect to the surface normal
        return previousDirection.subtract(normal.scale(2.0 * previousDirection.dot(normal)));
    }

    public Vec3 getSurfaceNormal(Direction blockSide) {
        return switch (blockSide) {
            case DOWN -> new Vec3(0, -1, 0);
            case UP -> new Vec3(0, 1, 0);
            case NORTH -> new Vec3(0, 0, -1);
            case SOUTH -> new Vec3(0, 0, 1);
            case WEST -> new Vec3(-1, 0, 0);
            case EAST -> new Vec3(1, 0, 0);
        };
    }
    
    private void chainReactionFrom(Entity target) {
        if (this.perks == null
                || this.perks.chain_reaction_size <= 0
                || this.perks.chain_reaction_triggers <= 0
                || impactHistory.contains(target)) {
            return;
        }
        if (level().isClientSide()) {
            return;
        }
        var spellEntry = this.getSpellEntry();
        if (spellEntry == null) {
            return;
        }
        var position = this.position();
        var spawnCount = this.perks.chain_reaction_size;
        var launchVector = new Vec3(1, 0, 0).scale(this.getDeltaMovement().length());
        var launchAngle = 360 / spawnCount;
        var launchAngleOffset = random.nextFloat() * launchAngle;

        this.impactHistory.add(target.getId());
        this.perks.chain_reaction_triggers -= 1;
        this.perks.chain_reaction_size += this.perks.chain_reaction_increment;

        for (int i = 0; i < spawnCount; i++) {
            var projectile = new SpellProjectile(level(), (LivingEntity)this.getOwner(),
                    position.x(), position.y(), position.z(),
                    this.getBehaviour(), spellEntry, context, this.perks.copy());

            var angle = launchAngle * i + launchAngleOffset;
            projectile.setDeltaMovement(launchVector.yRot((float) Math.toRadians(angle)));
            projectile.range = this.range;
            ProjectileUtil.rotateTowardsMovement(projectile, 0.2F);
            projectile.impactHistory = new HashSet<>(this.impactHistory);
            level().addFreshEntity(projectile);
        }
    }

    // MARK: Helper

    public SpellExecution.ImpactContext getImpactContext() {
        return context;
    }

    public ItemStack getItemStackModel() {
        return itemStackModel;
    }

    // MARK: FlyingSpellEntity

    /// The models this projectile renders as; null when it defines none.
    public Spell.ProjectileModelComposite renderModels() {
        var data = projectileData();
        if (data != null && data.client_data != null) {
            return data.client_data.composite_model;
        }
        return null;
    }

    /// Synced registry id of the caster's captured held item (empty when none). Used by the
    /// composite renderer for models with `use_held_item`.
    public String heldItemModelId() {
        return this.getEntityData().get(TRACKER_ITEM_MODEL_ID);
    }

    /// Required by `FlyingItemEntity`. Spell projectiles draw themselves through
    /// `composite_model` rather than as an item stack, so there is nothing to hand back —
    /// models that render as the caster's held item go through `heldItemModelId()` instead.
    @Override
    public ItemStack getItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (bounceFrom(blockHitResult)) {
            return;
        }

        if (this.getOwner() != null
                && this.getOwner() instanceof LivingEntity caster) {
            var hitPosition = blockHitResult.getLocation();
            var performed = SpellImpacts.projectileImpact(caster, this, null, this.getSpellEntry(), context.position(hitPosition));
        }
        this.discard();
    }

    private Gson gson = new Gson();


    // MARK: Stored data

    public void setBehaviour(Behaviour behaviour) {
        this.getEntityData().set(TRACKER_BEHAVIOUR, behaviour.toString());
    }

    /// Per-instance multiplier applied to the projectile's render scale and hitbox (1 = unchanged).
    /// Accumulated from spell modifiers (e.g. charge `bonus.projectile_scale_multiply`) at launch.
    public float getScaleMultiplier() {
        return this.getEntityData().get(TRACKER_SCALE);
    }
    public void setScaleMultiplier(float scale) {
        this.getEntityData().set(TRACKER_SCALE, scale);
        this.refreshDimensions();
    }
    public Behaviour getBehaviour() {
        var string = this.getEntityData().get(TRACKER_BEHAVIOUR);
        if (string == null || string.isEmpty()) {
            return Behaviour.FLY;
        }
        return Behaviour.valueOf(string);
    }

    private Holder<Spell> spellEntry;
    public void setSpell(Holder<Spell> entry) {
        this.spellEntry = entry;
        if (!level().isClientSide()) {
            this.getEntityData().set(TRACKER_SPELL_ID, spellId().toString());
        }
        this.refreshDimensions();
    }
    @Nullable public Holder<Spell> getSpellEntry() {
        return spellEntry;
    }
    private Identifier spellId() {
        if (spellEntry != null) {
            return spellEntry.unwrapKey().get().identifier();
        }
        return null;
    }


    private Entity followedTarget;
    private double distanceToFollow = 0;
    public void setFollowedTarget(Entity target) {
        followedTarget = target;
        if (target != null) {
            distanceToFollow = target.distanceTo(this);
        } else {
            distanceToFollow = 0;
        }
        var id = -1;
        if (!level().isClientSide()) {
            if (target != null) {
                id = target.getId();
            }
            this.getEntityData().set(TRACKER_TARGET_ID, id);
        }
    }

    private ItemStack itemStackModel;
    public void setItemStackModel(ItemStack itemStack) {
        var modelId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        this.getEntityData().set(TRACKER_ITEM_MODEL_ID, modelId.toString());
    }
    private void updateItemModel(String idString) {
        if (idString != null && !idString.isEmpty()) {
            var id = Identifier.parse(this.getEntityData().get(TRACKER_ITEM_MODEL_ID));
            itemStackModel = BuiltInRegistries.ITEM.getValue(id).getDefaultInstance();
        }
    }

    // MARK: NBT (Persistence)

    private static String NBT_BEHAVIOUR = "Behaviour";
    private static String NBT_SPELL_ID = "Spell.ID";
    private static String NBT_PERKS = "Perks";
    private static String NBT_IMPACT_CONTEXT = "Impact.Context";
    private static String NBT_ITEM_MODEL_ID = "Item.Model.ID";
    private static String NBT_SCALE = "Scale";

    @Override
    public void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putString(NBT_BEHAVIOUR, this.getBehaviour().toString());

        if (this.spellId() != null) {
            view.putString(NBT_SPELL_ID, this.spellId().toString());
        }
        view.putString(NBT_IMPACT_CONTEXT, gson.toJson(this.context));
        view.putString(NBT_PERKS, gson.toJson(this.perks));
        view.putFloat(NBT_SCALE, getScaleMultiplier());

        var itemModelId = getEntityData().get(TRACKER_ITEM_MODEL_ID);
        if (!itemModelId.isEmpty()) {
            view.putString(NBT_ITEM_MODEL_ID, itemModelId);
        }
    }

    @Override
    public void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        var spellIdString = view.getString(NBT_SPELL_ID);
        if (spellIdString.isPresent()) {
            try {
                var behaviour = Behaviour.valueOf(view.getStringOr(NBT_BEHAVIOUR, Behaviour.FLY.toString()));
                this.setBehaviour(behaviour);

                var spellId = Identifier.parse(spellIdString.get());
                this.setSpell(SpellRegistry.from(this.level()).get(spellId).orElse(null));

                this.context = gson.fromJson(view.getStringOr(NBT_IMPACT_CONTEXT, "{}"), SpellExecution.ImpactContext.class);
                this.perks = gson.fromJson(view.getStringOr(NBT_PERKS, "{}"), Spell.ProjectileData.Perks.class);
                this.setScaleMultiplier(view.getFloatOr(NBT_SCALE, getScaleMultiplier()));

                view.getString(NBT_ITEM_MODEL_ID).ifPresent(this::updateItemModel);
            } catch (Exception e) {
                System.err.println("SpellProjectile - Failed to read spell data from NBT " + e.getMessage());
            }
        }
    }

    // MARK: DataTracker (client-server sync)

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TRACKER_SPELL_ID, "");
        builder.define(TRACKER_BEHAVIOUR, Behaviour.FLY.toString());
        builder.define(TRACKER_TARGET_ID, 0);
        builder.define(TRACKER_ITEM_MODEL_ID, "");
        builder.define(TRACKER_SCALE, 1F);
    }

    private static final EntityDataAccessor<String> TRACKER_SPELL_ID;
    private static final EntityDataAccessor<String> TRACKER_BEHAVIOUR;
    private static final EntityDataAccessor<Integer> TRACKER_TARGET_ID;
    private static final EntityDataAccessor<String> TRACKER_ITEM_MODEL_ID;
    private static final EntityDataAccessor<Float> TRACKER_SCALE;

    static {
        TRACKER_SPELL_ID = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
        TRACKER_BEHAVIOUR = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
        TRACKER_TARGET_ID = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.INT);
        TRACKER_ITEM_MODEL_ID = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
        TRACKER_SCALE = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.FLOAT);
    }

    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (this.level().isClientSide()) {
            if (data.equals(TRACKER_SPELL_ID)) {
                var spellId = this.getEntityData().get(TRACKER_SPELL_ID);
                var spellEntry = SpellRegistry.from(this.level()).get(Identifier.parse(spellId)).orElse(null);
                this.setSpell(spellEntry);
            }
            if (data.equals(TRACKER_ITEM_MODEL_ID)) {
                updateItemModel(this.getEntityData().get(TRACKER_ITEM_MODEL_ID));
            }
            if (data.equals(TRACKER_TARGET_ID)) {
                var id = this.getEntityData().get(TRACKER_TARGET_ID);
                var target = id > 0 ? this.level().getEntity(id) : null;
                this.setFollowedTarget(target);
            }
            if (data.equals(TRACKER_SCALE)) {
                this.refreshDimensions();
            }
        }
    }
}

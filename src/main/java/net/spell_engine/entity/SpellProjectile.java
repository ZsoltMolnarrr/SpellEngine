package net.spell_engine.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.render.FlyingSpellEntity;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.particle.ParticleHelper;
import net.spell_engine.utils.RecordsWithGson;
import net.spell_engine.utils.TargetHelper;
import net.spell_engine.utils.VectorHelper;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;


public class SpellProjectile extends ProjectileEntity implements FlyingSpellEntity {
    public static EntityType<SpellProjectile> ENTITY_TYPE;
    private static Random random = new Random();

    public float range = 128;
    private Spell.ProjectileData.Perks perks;
    private SpellHelper.ImpactContext context;
    private Entity followedTarget;
    private Identifier spellId;
    public Vec3d previousVelocity;

    public SpellProjectile(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    protected SpellProjectile(World world, LivingEntity owner) {
        super(ENTITY_TYPE, world);
        this.setOwner(owner);
    }

    public enum Behaviour {
        FLY, FALL
    }

    public SpellProjectile(World world, LivingEntity caster, double x, double y, double z,
                           Behaviour behaviour, Identifier spellId, Entity target, SpellHelper.ImpactContext context, Spell.ProjectileData.Perks mutablePerks) {
        this(world, caster);
        this.setPosition(x, y, z);
        this.spellId = spellId;
        this.perks = mutablePerks;
        var projectileData = projectileData();
        var gson = new Gson();
        this.context = context;
        this.getDataTracker().set(CLIENT_DATA, gson.toJson(projectileData));
        this.getDataTracker().set(BEHAVIOUR, behaviour.toString());
        setFollowedTarget(target);
    }

    /**
     * A copy of the spell projectile perks, can be safely modified
      */
    public Spell.ProjectileData.Perks mutablePerks() {
        return perks;
    }

    private Spell.ProjectileData projectileData() {
        if (getWorld().isClient) {
            return clientSyncedData;
        } else {
            var spell = getSpell();
            var release = spell.release.target;
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
    }
    private Spell.ProjectileData clientSyncedData;

    private void updateClientSideData() {
        if (clientSyncedData != null) {
            return;
        }
        try {
            var gson = new Gson();
            var json = this.getDataTracker().get(CLIENT_DATA);
            var data = gson.fromJson(json, Spell.ProjectileData.class);
            clientSyncedData = data;
        } catch (Exception e) {
            System.err.println("Spell Projectile - Failed to read clientSyncedData");
        }
    }

    public void setVelocity(double x, double y, double z, float speed, float spread, float divergence) {
        var rotX = Math.toRadians(divergence * random.nextFloat(spread, 1F));
        var rotY = Math.toRadians(360 * random.nextFloat());
        Vec3d vec3d = (new Vec3d(x, y, z))
                .rotateX((float) rotX)
                .rotateY((float) rotY)
                .multiply(speed);
        this.setVelocity(vec3d);
        double d = vec3d.horizontalLength();
        this.setYaw((float)(MathHelper.atan2(vec3d.x, vec3d.z) * 57.2957763671875));
        this.setPitch((float)(MathHelper.atan2(vec3d.y, d) * 57.2957763671875));
        this.prevYaw = this.getYaw();
        this.prevPitch = this.getPitch();
    }

    public void setFollowedTarget(Entity target) {
        followedTarget = target;
        var id = 0;
        if (!getWorld().isClient) {
            if (target != null) {
                id = target.getId();
            }
            this.getDataTracker().set(TARGET_ID, id);
        }
    }

    public Entity getFollowedTarget() {
        Entity entityReference = null;
        if (getWorld().isClient) {
            var id = this.getDataTracker().get(TARGET_ID);
            if (id != null && id != 0) {
                entityReference = getWorld().getEntityById(id);
            }
        } else {
            entityReference = followedTarget;
        }
        if (entityReference != null && entityReference.isAttackable() && entityReference.isAlive()) {
            return entityReference;
        }
        return entityReference;
    }

    public boolean shouldRender(double distance) {
        double d0 = this.getBoundingBox().getAverageSideLength() * 4.0;
        if (Double.isNaN(d0)) {
            d0 = 4.0;
        }

        d0 *= 128.0;
        var result =  distance < d0 * d0;
        return result;
    }

    public Behaviour behaviour() {
        var string = this.getDataTracker().get(BEHAVIOUR);
        if (string == null || string.isEmpty()) {
            return Behaviour.FLY;
        }
        return Behaviour.valueOf(string);
    }

    private boolean skipTravel = false;

    public void tick() {
        skipTravel = false;
        Entity entity = this.getOwner();
        var behaviour = behaviour();
        if (getWorld().isClient) {
            updateClientSideData();
        }
        if (!this.getWorld().isClient) {
            // Server side
            if (getSpell() == null) {
                System.err.println("Spell Projectile safeguard termination, failed to resolve spell: " + spellId);
                this.kill();
                return;
            }
            switch (behaviour) {
                case FLY -> {
                    if (distanceTraveled >= range || age > 1200) { // 1200 ticks = 1 minute
                        this.kill();
                        return;
                    }
                }
                case FALL -> {
                    if (distanceTraveled >= (range * 0.98)) {
                        finishFalling();
                        this.kill();
                        return;
                    }
                    if (age > 1200) { // 1200 ticks = 1 minute
                        this.kill();
                        return;
                    }
                }
            }
            if (distanceTraveled >= range || age > 1200) { // 1200 ticks = 1 minute
                this.kill();
                return;
            }
        }
        this.previousVelocity = new Vec3d(getVelocity().x, getVelocity().y, getVelocity().z);
        if (this.getWorld().isClient || (entity == null || !entity.isRemoved()) && this.getWorld().isChunkLoaded(this.getBlockPos())) {
            super.tick();

            if (!getWorld().isClient) {
                HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
                if (hitResult.getType() != HitResult.Type.MISS) {
                    switch (behaviour) {
                        case FLY -> {
                            boolean shouldCollideWithEntity = true;
                            if (hitResult.getType() == HitResult.Type.ENTITY) {
                                var target = ((EntityHitResult) hitResult).getEntity();
                                var spell = getSpell();
                                if (SpellEngineMod.config.projectiles_pass_thru_irrelevant_targets
                                        && spell != null
                                        && spell.impact.length > 0
                                        && getOwner() instanceof LivingEntity owner) {
                                    var intents = SpellHelper.intents(spell);

                                    boolean intentAllows = false;
                                    for (var intent: intents) {
                                        intentAllows = intentAllows || TargetHelper.actionAllowed(TargetHelper.TargetingMode.DIRECT, intent, owner, target);
                                    }
                                    shouldCollideWithEntity = intentAllows;
                                }
                            }
                            if (shouldCollideWithEntity) {
                                this.onCollision(hitResult);
                            } else {
                                this.setFollowedTarget(null);
                            }
                        }
                        case FALL -> {
                        }
                    }
                }
            }

            this.checkBlockCollision();

            // Travel
            if (!skipTravel) {
                this.followTarget();
                Vec3d velocity = this.getVelocity();
                double d = this.getX() + velocity.x;
                double e = this.getY() + velocity.y;
                double f = this.getZ() + velocity.z;
                ProjectileUtil.setRotationFromVelocity(this, 0.2F);

                float g = this.getDrag();
                if (this.isTouchingWater()) {
                    for(int i = 0; i < 4; ++i) {
                        float h = 0.25F;
                        this.getWorld().addParticle(ParticleTypes.BUBBLE, d - velocity.x * 0.25, e - velocity.y * 0.25, f - velocity.z * 0.25, velocity.x, velocity.y, velocity.z);
                    }
                    g = 0.8F;
                }

                if (getWorld().isClient) {
                    var data = projectileData();
                    if (data != null) {
                        for (var travel_particles : data.client_data.travel_particles) {
                            ParticleHelper.play(getWorld(), this, getYaw(), getPitch(), travel_particles);
                        }
                    }
                }

                this.setPosition(d, e, f);
                this.distanceTraveled += velocity.length();
            }
        } else {
            this.discard();
        }
    }

    private void finishFalling() {
        Entity owner = this.getOwner();
        if (owner == null || owner.isRemoved()) {
            return;
        }
        if (owner instanceof LivingEntity livingEntity) {
            SpellHelper.fallImpact(livingEntity, this, this.getSpell(), context.position(this.getPos()));
        }
    }

    private void followTarget() {
        var target = getFollowedTarget();
        if (target != null && projectileData().homing_angle > 0) {
            var distanceVector = (target.getPos().add(0, target.getHeight() / 2F, 0))
                    .subtract(this.getPos().add(0, this.getHeight() / 2F, 0));
//            System.out.println((world.isClient ? "Client: " : "Server: ") + "Distance: " + distanceVector);
//            System.out.println((world.isClient ? "Client: " : "Server: ") + "Velocity: " + getVelocity());
            var newVelocity = VectorHelper.rotateTowards(getVelocity(), distanceVector, projectileData().homing_angle);
            if (newVelocity.lengthSquared() > 0) {
//                System.out.println((world.isClient ? "Client: " : "Server: ") + "Rotated to: " + newVelocity);
                this.setVelocity(newVelocity);
                this.velocityDirty = true;
            }
        }
    }

    protected float getDrag() {
        return 0.95F;
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!getWorld().isClient) {
            var target = entityHitResult.getEntity();
            if (target != null
                    && !impactHistory.contains(target.getId())
                    && this.getOwner() != null
                    && this.getOwner() instanceof LivingEntity caster) {
                setFollowedTarget(null);
                var context = this.context;
                if (context == null) {
                    context = new SpellHelper.ImpactContext();
                }
                var performed = SpellHelper.projectileImpact(caster, this, target, this.getSpell(), context.position(entityHitResult.getPos()));
                if (performed) {
                    chainReactionFrom(target);
                    if (ricochetFrom(target, caster)) {
                        return;
                    }
                    if (pierced(target)) {
                        return;
                    }
                    this.kill();
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
        var box = this.getBoundingBox().expand(
                this.perks.ricochet_range,
                this.perks.ricochet_range,
                this.perks.ricochet_range);
        var intents = SpellHelper.intents(this.getSpell());
        Predicate<Entity> intentMatches = (entity) -> {
            boolean intentAllows = false;
            for (var intent: intents) {
                intentAllows = intentAllows || TargetHelper.actionAllowed(TargetHelper.TargetingMode.AREA, intent, caster, entity);
            }
            return intentAllows;
        };
        var otherTargets = this.getWorld().getOtherEntities(this, box, (entity) -> {
            return entity.isAttackable()
                    && entity instanceof LivingEntity // Avoid targeting unliving entities like other projectiles
                    && !impactHistory.contains(entity.getId())
                    && intentMatches.test(entity)
                    && !entity.getPos().equals(target.getPos());
        });
        if (otherTargets.isEmpty()) {
            this.setFollowedTarget(null);
            return false;
        }

        otherTargets.sort(Comparator.comparingDouble(o -> o.squaredDistanceTo(target)));

        // Set trajectory
        var newTarget = otherTargets.get(0);
        this.setFollowedTarget(newTarget);

        var distanceVector = (newTarget.getPos().add(0, newTarget.getHeight() / 2F, 0))
                .subtract(this.getPos().add(0, this.getHeight() / 2F, 0));
        var newVelocity = distanceVector.normalize().multiply(this.getVelocity().length());
        this.setVelocity(newVelocity);
        this.velocityDirty = true;

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
        return true;
    }

    private boolean bounceFrom(BlockHitResult blockHitResult) {
        if (this.perks == null
                || this.perks.bounce <= 0) {
            return false;
        }

        var previousPosition = getPos();
        var previousDirection = getVelocity();
        var impactPosition = blockHitResult.getPos();
        var impactSide = blockHitResult.getSide();
        var speed = getVelocity().length();

        Vec3d surfaceNormal = getSurfaceNormal(impactSide);
        Vec3d newDirection = calculateBounceVector(previousDirection, surfaceNormal);

        // Calculate the remaining distance the projectile should travel after bouncing
        double remainingDistance = previousDirection.length() - (impactPosition.subtract(previousPosition)).length();

        // Calculate the final position after the remaining distance
        Vec3d finalPosition = impactPosition.add(newDirection.normalize().multiply(remainingDistance));

        // Set the new position and velocity
        this.setPos(finalPosition.getX(), finalPosition.getY(), finalPosition.getZ());
        this.setVelocity(newDirection.multiply(speed));
        ProjectileUtil.setRotationFromVelocity(this, 0.2F);

        this.perks.bounce -= 1;
        if (this.perks.bounce_ricochet_sync) {
            this.perks.ricochet -= 1;
        }
        this.velocityDirty = true;
        this.skipTravel = true;
        return true;
    }

    public Vec3d calculateBounceVector(Vec3d previousDirection, Vec3d normal) {
        // Calculate the reflection of the incident vector with respect to the surface normal
        return previousDirection.subtract(normal.multiply(2.0 * previousDirection.dotProduct(normal)));
    }

    public Vec3d getSurfaceNormal(Direction blockSide) {
        return switch (blockSide) {
            case DOWN -> new Vec3d(0, -1, 0);
            case UP -> new Vec3d(0, 1, 0);
            case NORTH -> new Vec3d(0, 0, -1);
            case SOUTH -> new Vec3d(0, 0, 1);
            case WEST -> new Vec3d(-1, 0, 0);
            case EAST -> new Vec3d(1, 0, 0);
        };
    }
    
    private void chainReactionFrom(Entity target) {
        if (this.perks == null
                || this.perks.chain_reaction_size <= 0
                || this.perks.chain_reaction_triggers <= 0
                || impactHistory.contains(target)) {
            return;
        }
        if (getWorld().isClient) {
            return;
        }
        var spell = getSpell();
        var position = this.getPos();
        var spawnCount = this.perks.chain_reaction_size;
        var launchVector = new Vec3d(1, 0, 0).multiply(this.getVelocity().length());
        var launchAngle = 360 / spawnCount;
        var launchAngleOffset = random.nextFloat() * launchAngle;

        this.impactHistory.add(target.getId());
        this.perks.chain_reaction_triggers -= 1;
        this.perks.chain_reaction_size += this.perks.chain_reaction_increment;

        for (int i = 0; i < spawnCount; i++) {
            var projectile = new SpellProjectile(getWorld(), (LivingEntity)this.getOwner(),
                    position.getX(), position.getY(), position.getZ(),
                    this.behaviour(), spellId, null, context, this.perks.copy());

            var angle = launchAngle * i + launchAngleOffset;
            projectile.setVelocity(launchVector.rotateY((float) Math.toRadians(angle)));
            projectile.range = spell.range;
            ProjectileUtil.setRotationFromVelocity(projectile, 0.2F);
            projectile.impactHistory = new HashSet<>(this.impactHistory);
            getWorld().spawnEntity(projectile);
        }
    }

    // MARK: Helper

    public Spell getSpell() {
        return SpellRegistry.getSpell(spellId);
    }

    public SpellHelper.ImpactContext getImpactContext() {
        return context;
    }

    // MARK: FlyingSpellEntity

    public Spell.ProjectileModel renderData() {
        var data = projectileData();
        if (data != null && data.client_data != null) {
            return data.client_data.model;
        }
        return null;
    }

    @Override
    public ItemStack getStack() {
        var data = projectileData();
        if (data != null && data.client_data != null && data.client_data.model != null) {
            return Registries.ITEM.get(new Identifier(data.client_data.model.model_id)).getDefaultStack();
        }
        return ItemStack.EMPTY;
    }

    // MARK: NBT (Persistence)

    private static String NBT_SPELL_ID = "Spell.ID";
    private static String NBT_PERKS = "Perks";
    private static String NBT_IMPACT_CONTEXT = "Impact.Context";

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (bounceFrom(blockHitResult)) {
            return;
        }
        this.kill();
    }

    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        var gson = new Gson();
        nbt.putString(NBT_SPELL_ID, gson.toJson(spellId));
        nbt.putString(NBT_IMPACT_CONTEXT, gson.toJson(context));
        nbt.putString(NBT_PERKS, gson.toJson(this.perks));
    }

    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains(NBT_SPELL_ID, NbtElement.STRING_TYPE)) {
            try {
                var gson = new Gson();
                this.spellId = new Identifier(nbt.getString(NBT_SPELL_ID));
                var recordReader = new GsonBuilder()
                        .registerTypeAdapterFactory(new RecordsWithGson.RecordTypeAdapterFactory())
                        .create();
                this.context = recordReader.fromJson(nbt.getString(NBT_IMPACT_CONTEXT), SpellHelper.ImpactContext.class);
                this.perks = gson.fromJson(nbt.getString(NBT_PERKS), Spell.ProjectileData.Perks.class);
            } catch (Exception e) {
                System.err.println("SpellProjectile - Failed to read spell data from NBT");
            }
        }
    }

    // MARK: DataTracker (client-server sync)

    @Override
    protected void initDataTracker() {
        this.getDataTracker().startTracking(CLIENT_DATA, "");
        this.getDataTracker().startTracking(TARGET_ID, 0);
        this.getDataTracker().startTracking(BEHAVIOUR, Behaviour.FLY.toString());
    }

    private static final TrackedData<String> BEHAVIOUR;
    private static final TrackedData<String> CLIENT_DATA;
    private static final TrackedData<Integer> TARGET_ID;

    static {
        CLIENT_DATA = DataTracker.registerData(SpellProjectile.class, TrackedDataHandlerRegistry.STRING);
        TARGET_ID = DataTracker.registerData(SpellProjectile.class, TrackedDataHandlerRegistry.INTEGER);
        BEHAVIOUR = DataTracker.registerData(SpellProjectile.class, TrackedDataHandlerRegistry.STRING);
    }
}

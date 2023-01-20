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
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.render.FlyingSpellEntity;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.particle.ParticleHelper;
import net.spell_engine.utils.RecordsWithGson;
import net.spell_engine.utils.TargetHelper;
import net.spell_engine.utils.VectorHelper;


public class SpellProjectile extends ProjectileEntity implements FlyingSpellEntity {
    public float range = 128;
    private Spell spell;
    private SpellHelper.ImpactContext context;
    private Entity followedTarget;

    public Vec3d previousVelocity;

    public SpellProjectile(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    protected SpellProjectile(World world, LivingEntity owner) {
        super(SpellEngineMod.SPELL_PROJECTILE, world);
        this.setOwner(owner);
    }

    public enum Behaviour {
        FLY, FALL
    }

    public SpellProjectile(World world, LivingEntity caster, double x, double y, double z,
                           Behaviour behaviour, Spell spell, Entity target, SpellHelper.ImpactContext context) {
        this(world, caster);
        this.setPosition(x, y, z);
        this.spell = spell;
        var projectileData = projectileData();
        var gson = new Gson();
        this.context = context;
        this.getDataTracker().set(CLIENT_DATA, gson.toJson(projectileData));
        this.getDataTracker().set(BEHAVIOUR, behaviour.toString());
        setFollowedTarget(target);
    }

    private Spell.ProjectileData projectileData() {
        if (world.isClient) {
            return clientSyncedData;
        } else {
            return spell.release.target.projectile;
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

    private void setFollowedTarget(Entity target) {
        followedTarget = target;
        var id = 0;
        if (!world.isClient) {
            if (target != null) {
                id = target.getId();
            }
            this.getDataTracker().set(TARGET_ID, id);
        }
    }

    public Entity getFollowedTarget() {
        Entity entityReference = null;
        if (world.isClient) {
            var id = this.getDataTracker().get(TARGET_ID);
            if (id != null && id != 0) {
                entityReference = world.getEntityById(id);
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

    public void tick() {
        Entity entity = this.getOwner();
        var behaviour = behaviour();
        if (world.isClient) {
            updateClientSideData();
        }
        if (!this.world.isClient) {
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
        if (this.world.isClient || (entity == null || !entity.isRemoved()) && this.world.isChunkLoaded(this.getBlockPos())) {
            super.tick();

            if (!world.isClient) {
                HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
                if (hitResult.getType() != HitResult.Type.MISS) {
                    switch (behaviour) {
                        case FLY -> {
                            boolean shouldCollideWithEntity = true;
                            if (hitResult.getType() == HitResult.Type.ENTITY) {
                                var target = ((EntityHitResult) hitResult).getEntity();
                                if (SpellEngineMod.config.projectiles_pass_thru_irrelevant_targets
                                        && spell != null
                                        && spell.impact.length > 0
                                        && getOwner() instanceof LivingEntity owner) {
                                    var intent = SpellHelper.intent(spell);
                                    shouldCollideWithEntity = TargetHelper.actionAllowed(TargetHelper.TargetingMode.DIRECT, intent, owner, target);
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

            this.followTarget();
            this.checkBlockCollision();
            Vec3d velocity = this.getVelocity();
            double d = this.getX() + velocity.x;
            double e = this.getY() + velocity.y;
            double f = this.getZ() + velocity.z;
            ProjectileUtil.setRotationFromVelocity(this, 0.2F);

            float g = this.getDrag();
            if (this.isTouchingWater()) {
                for(int i = 0; i < 4; ++i) {
                    float h = 0.25F;
                    this.world.addParticle(ParticleTypes.BUBBLE, d - velocity.x * 0.25, e - velocity.y * 0.25, f - velocity.z * 0.25, velocity.x, velocity.y, velocity.z);
                }
                g = 0.8F;
            }
            // this.setVelocity(vec3d.add(this.powerX, this.powerY, this.powerZ).multiply((double)g));

            if (world.isClient) {
                if (projectileData() != null) {
                    for (var travel_particles : projectileData().client_data.travel_particles) {
                        ParticleHelper.play(world, this, getYaw(), getPitch(), travel_particles);
                    }
                }
            }

            this.setPosition(d, e, f);
            this.distanceTraveled += velocity.length();
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
            SpellHelper.fallImpact(livingEntity, this, this.spell, this.getPos(), context);
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
        if (!world.isClient) {
            var target = entityHitResult.getEntity();
            if (target != null && this.getOwner() != null && this.getOwner() instanceof LivingEntity caster) {
                setFollowedTarget(null);
                var context = this.context;
                if (context == null) {
                    context = new SpellHelper.ImpactContext();
                }
                var performed = SpellHelper.performImpacts(world, caster, target, spell, context.position(new Vec3d(prevX, prevY, prevZ)));
                if (performed) {
                    this.kill();
                }
            }
        }
    }

    // MARK: Helper

    public Spell getSpell() {
        return spell;
    }

    public SpellHelper.ImpactContext getImpactContext() {
        return context;
    }

    // MARK: FlyingSpellEntity

    public Spell.ProjectileData.Client renderData() {
        var data = projectileData();
        if (data != null) {
            return projectileData().client_data;
        }
        return null;
    }

    @Override
    public ItemStack getStack() {
        if (projectileData() != null && projectileData().client_data != null) {
            return Registry.ITEM.get(new Identifier(projectileData().client_data.model_id)).getDefaultStack();
        }
        return ItemStack.EMPTY;
    }

    // MARK: NBT (Persistence)

    private static String NBT_SPELL_DATA = "Spell.Data";
    private static String NBT_IMPACT_CONTEXT = "Impact.Context";

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        this.kill();
    }

    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        var gson = new Gson();
        nbt.putString(NBT_SPELL_DATA, gson.toJson(spell));
        nbt.putString(NBT_IMPACT_CONTEXT, gson.toJson(context));
    }

    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains(NBT_SPELL_DATA, NbtElement.STRING_TYPE)) {
            try {
                var gson = new Gson();
                this.spell = gson.fromJson(nbt.getString(NBT_SPELL_DATA), Spell.class);
                var recordReader = new GsonBuilder()
                        .registerTypeAdapterFactory(new RecordsWithGson.RecordTypeAdapterFactory())
                        .create();
                this.context = recordReader.fromJson(nbt.getString(NBT_IMPACT_CONTEXT), SpellHelper.ImpactContext.class);
            } catch (Exception e) {
                System.err.println("SpellProjectile - Failed to read spell data from NBT");
            }
        }
    }

    // MARK: DataTracker (client-server sync)

    @Override
    protected void initDataTracker() {
        var gson = new Gson();
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

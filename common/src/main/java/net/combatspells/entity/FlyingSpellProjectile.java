package net.combatspells.entity;

import com.google.gson.Gson;
import net.combatspells.CombatSpells;
import net.combatspells.api.SpellHelper;
import net.combatspells.api.spell.Spell;
import net.combatspells.client.render.FlyingSpellEntity;
import net.combatspells.utils.ParticleHelper;
import net.combatspells.utils.VectorHelper;
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


public class FlyingSpellProjectile extends ProjectileEntity implements FlyingSpellEntity {
    public float range = 128;
    private Spell spell;
    private Entity followedTarget;

    public FlyingSpellProjectile(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    protected FlyingSpellProjectile(World world, LivingEntity owner) {
        super(CombatSpells.SPELL_PROJECTILE, world);
        this.setOwner(owner);
    }

    public FlyingSpellProjectile(World world, LivingEntity caster, double x, double y, double z,
                                 Spell spell, Entity target) {
        this(world, caster);
        this.setPosition(x, y, z);
        this.spell = spell;
        var projectileData = projectileData();
        var velocity = projectileData.velocity;
        var divergence = projectileData.divergence;
        if (projectileData.inherit_shooter_velocity) {
            this.setVelocity(caster, caster.getPitch(), caster.getYaw(), caster.getRoll(), velocity, divergence);
        } else {
            var look = caster.getRotationVector().normalize();
            this.setVelocity(look.x, look.y, look.z, velocity, divergence);
        }
        var gson = new Gson();
        this.getDataTracker().set(CLIENT_DATA, gson.toJson(projectileData));
        setFollowedTarget(target);
    }

    private Spell.ProjectileData projectileData() {
        if (world.isClient) {
            return clientSyncedData;
        } else {
            return spell.on_release.target.projectile;
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

    private Entity getFollowedTarget() {
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

    public void tick() {
        Entity entity = this.getOwner();
        if (world.isClient) {
            updateClientSideData();
        }
        if (!this.world.isClient) {
            if (distanceTraveled >= range || age > 1200) { // 1200 ticks = 1 minute
                this.kill();
                return;
            }
        }
        if (this.world.isClient || (entity == null || !entity.isRemoved()) && this.world.isChunkLoaded(this.getBlockPos())) {
            super.tick();

            HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
            if (hitResult.getType() != HitResult.Type.MISS) {
                this.onCollision(hitResult);
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
                        ParticleHelper.play(world, this, getYaw(), getPitch() + 90, travel_particles);
                    }
                }
            }

            this.setPosition(d, e, f);
            this.distanceTraveled += velocity.length();
        } else {
            this.discard();
        }
    }

    private void followTarget() {
        var target = getFollowedTarget();
        if (target != null && projectileData().homing_angle > 0) {
            var distanceVector = (target.getPos().add(0, target.getHeight() / 2F, 0))
                    .subtract(this.getPos().add(0, this.getHeight() / 2F, 0));
            System.out.println((world.isClient ? "Client: " : "Server: ") + "Distance: " + distanceVector);
            System.out.println((world.isClient ? "Client: " : "Server: ") + "Velocity: " + getVelocity());
            var newVelocity = VectorHelper.rotateTowards(getVelocity(), distanceVector, projectileData().homing_angle);
            if (newVelocity.lengthSquared() > 0) {
                System.out.println((world.isClient ? "Client: " : "Server: ") + "Rotated to: " + newVelocity);
                this.setVelocity(newVelocity);
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
            if (target != null && this.getOwner() instanceof LivingEntity caster) {
                setFollowedTarget(null);
                var performed = SpellHelper.performImpacts(world, caster, target, spell);
                if (performed) {
                    this.kill();
                }
            }
        }
    }

    private static String NBT_SPELL_DATA = "Spell.Data";

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        this.kill();
    }

    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        var gson = new Gson();
        nbt.putString(NBT_SPELL_DATA, gson.toJson(spell));
    }

    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains(NBT_SPELL_DATA, NbtElement.STRING_TYPE)) {
            try {
                var gson = new Gson();
                this.spell = gson.fromJson(nbt.getString(NBT_SPELL_DATA), Spell.class);
            } catch (Exception e) {
                System.err.println("SpellProjectile - Failed to read spell data from NBT");
            }
        }
    }

    @Override
    public ItemStack getStack() {
        if (projectileData() != null && projectileData().client_data != null) {
            return Registry.ITEM.get(new Identifier(projectileData().client_data.item_id)).getDefaultStack();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public Spell.ProjectileData.Client.RenderMode renderMode() {
        var data = projectileData();
        if (data != null) {
            return projectileData().client_data.render;
        }
        return Spell.ProjectileData.Client.RenderMode.FLAT;
    }

    @Override
    protected void initDataTracker() {
        var gson = new Gson();
        this.getDataTracker().startTracking(CLIENT_DATA, "");
        this.getDataTracker().startTracking(TARGET_ID, 0);
    }

    private static final TrackedData<String> CLIENT_DATA;
    private static final TrackedData<Integer> TARGET_ID;

    static {
        CLIENT_DATA = DataTracker.registerData(FlyingSpellProjectile.class, TrackedDataHandlerRegistry.STRING);
        TARGET_ID = DataTracker.registerData(FlyingSpellProjectile.class, TrackedDataHandlerRegistry.INTEGER);
    }
}

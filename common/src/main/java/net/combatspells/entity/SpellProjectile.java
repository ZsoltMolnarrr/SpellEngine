package net.combatspells.entity;

import com.google.gson.Gson;
import net.combatspells.CombatSpells;
import net.combatspells.api.Spell;
import net.combatspells.utils.ParticleHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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


public class SpellProjectile extends ProjectileEntity implements FlyingItemEntity {
    public float range = 128;
    private Spell.ProjectileData projectileData;

    public SpellProjectile(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    protected SpellProjectile(World world, LivingEntity owner) {
        super(CombatSpells.SPELL_PROJECTILE, world);
        this.setOwner(owner);
    }

    public SpellProjectile(World world, LivingEntity caster, double x, double y, double z, Spell.ProjectileData projectileData) {
        this(world, caster);
        this.setPosition(x, y, z);
        this.projectileData = projectileData;
        var velocity = projectileData.velocity;
        var divergence = projectileData.divergence;
        if (projectileData.inherit_shooter_velocity) {
            this.setVelocity(caster, caster.getPitch(), caster.getYaw(), caster.getRoll(), velocity, divergence);
        } else {
            var look = caster.getRotationVector().normalize();
            this.setVelocity(look.x, look.y, look.z, velocity, divergence);
        }
        var gson = new Gson();
        this.getDataTracker().set(CLIENT_DATA, gson.toJson(projectileData.client_data));
    }

    private Spell.ProjectileData.Client cachedClientData;
    private Spell.ProjectileData.Client clientData() {
        if (cachedClientData != null) {
            return cachedClientData;
        }
        try {
            var gson = new Gson();
            var json = this.getDataTracker().get(CLIENT_DATA);
            var data = gson.fromJson(json, Spell.ProjectileData.Client.class);
            cachedClientData = data;
            return data;
        } catch (Exception e) {
            System.err.println("Failed to read Spell.ProjectileData.Client clientData()");
        }
        return null;
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
        if (!this.world.isClient) {
            if (distanceTraveled >= range) {
                this.kill();
                return;
            }
        }
        if (this.world.isClient || (entity == null || !entity.isRemoved()) && this.world.isChunkLoaded(this.getBlockPos())) {
            super.tick();
//            if (this.isBurning()) {
//                this.setOnFireFor(1);
//            }

            System.out.println("Spell projectile " + (this.world.isClient ? "Client" : "Server") + " ticks " + age);

            HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
            if (hitResult.getType() != HitResult.Type.MISS) {
                this.onCollision(hitResult);
            }

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

            this.setPosition(d, e, f);
            this.distanceTraveled += velocity.length();
            if (world.isClient) {
                var clientData = clientData();
                if (clientData != null) {
                    var origin = this.getPos().add(0, this.getHeight() / 2F, 0);
                    ParticleHelper.play(world, origin, clientData.travel_particles);
                }
            }
        } else {
            this.discard();
        }
    }

    protected float getDrag() {
        return 0.95F;
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        this.kill();
    }

    private static String NBT_DATA_KEY = "Spell.ProjectileData";

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        this.kill();
    }

    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        var gson = new Gson();
        var json = gson.toJson(projectileData);
        nbt.putString(NBT_DATA_KEY, json);
    }

    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains(NBT_DATA_KEY, NbtElement.STRING_TYPE)) {
            try {
                var json = nbt.getString(NBT_DATA_KEY);
                var gson = new Gson();
                this.projectileData = gson.fromJson(json, Spell.ProjectileData.class);
            } catch (Exception e) {
                System.err.println("SpellProjectile - Failed to read projectileData from NBT");
            }
        }
    }

    @Override
    public ItemStack getStack() {
        var clientData = clientData();
        if (clientData != null) {
            return Registry.ITEM.get(new Identifier(clientData.item_id)).getDefaultStack();
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void initDataTracker() {
        var gson = new Gson();
        this.getDataTracker().startTracking(CLIENT_DATA, "");
    }

    private static final TrackedData<String> CLIENT_DATA;

    static {
        CLIENT_DATA = DataTracker.registerData(SpellProjectile.class, TrackedDataHandlerRegistry.STRING);
    }
}

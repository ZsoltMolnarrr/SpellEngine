package net.combatspells.entity;

import net.combatspells.CombatSpells;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SpellProjectile extends ProjectileEntity implements FlyingItemEntity {
    private ItemStack renderedItem = Items.FIRE_CHARGE.getDefaultStack();
    public SpellProjectile(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    public SpellProjectile(World world, LivingEntity owner) {
        super(CombatSpells.SPELL_PROJECTILE, world);
        this.setOwner(owner);
    }

    public SpellProjectile(World world, LivingEntity owner, double x, double y, double z) {
        this(world, owner);
        this.setPosition(x, y, z);
    }

    public void tick() {
        Entity entity = this.getOwner();
        if (this.world.isClient || (entity == null || !entity.isRemoved()) && this.world.isChunkLoaded(this.getBlockPos())) {
            super.tick();
//            if (this.isBurning()) {
//                this.setOnFireFor(1);
//            }

            HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
            if (hitResult.getType() != HitResult.Type.MISS) {
                this.onCollision(hitResult);
            }

            this.checkBlockCollision();
            Vec3d vec3d = this.getVelocity();
            double d = this.getX() + vec3d.x;
            double e = this.getY() + vec3d.y;
            double f = this.getZ() + vec3d.z;
            ProjectileUtil.setRotationFromVelocity(this, 0.2F);
            float g = this.getDrag();
            if (this.isTouchingWater()) {
                for(int i = 0; i < 4; ++i) {
                    float h = 0.25F;
                    this.world.addParticle(ParticleTypes.BUBBLE, d - vec3d.x * 0.25, e - vec3d.y * 0.25, f - vec3d.z * 0.25, vec3d.x, vec3d.y, vec3d.z);
                }

                g = 0.8F;
            }

//            this.setVelocity(vec3d.add(this.powerX, this.powerY, this.powerZ).multiply((double)g));
//            this.world.addParticle(this.getParticleType(), d, e + 0.5, f, 0.0, 0.0, 0.0);
            this.setPosition(d, e, f);
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

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        this.kill();
    }

    @Override
    public ItemStack getStack() {
        return renderedItem;
    }

    @Override
    protected void initDataTracker() {
    }
}

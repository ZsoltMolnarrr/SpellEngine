package net.spell_engine.internals.arrow;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.CrossbowUser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellEvents;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.WorldScheduler;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ArrowHelper {
    public static void shootArrow(World world, LivingEntity shooter, SpellInfo spellInfo, SpellHelper.ImpactContext context) {
        shootArrow(world, shooter, spellInfo, context, true);
    }

    public static void shootArrow(World world, LivingEntity shooter, SpellInfo spellInfo, SpellHelper.ImpactContext context, boolean initial) {
        boolean isCreative = shooter instanceof PlayerEntity && ((PlayerEntity)shooter).getAbilities().creativeMode;

        var spell = spellInfo.spell();
        var shoot_arrow = spell.release.target.shoot_arrow;
        if (shoot_arrow != null) {
            var launchProperties = shoot_arrow.launch_properties.copy();
            var projectile = shoot(world, shooter, Hand.MAIN_HAND, shooter.getMainHandStack(),
                    new ItemStack(Items.ARROW), 1.0F, isCreative,
                    launchProperties.velocity, 1.0F, 0.0F, shoot_arrow.arrow_perks);
            if (SpellEvents.ARROW_FIRED.isListened()) {
                SpellEvents.ARROW_FIRED.invoke((listener) -> listener.onArrowLaunch(
                        new SpellEvents.ArrowLaunchEvent(projectile, launchProperties, shooter, spellInfo, context, initial)));
            }
            var extra_launch = launchProperties.extra_launch_count;
            if (initial && extra_launch > 0) {
                for (int i = 0; i < extra_launch; i++) {
                    var ticks = (i + 1) * launchProperties.extra_launch_delay;
                    ((WorldScheduler)world).schedule(ticks, () -> {
                        if (shooter == null || !shooter.isAlive()) {
                            return;
                        }
                        shootArrow(world, shooter, spellInfo, context, false);
                    });
                }
            }
        }
    }

    // Copied from CrossbowItem
    private static ProjectileEntity shoot(World world, LivingEntity shooter, Hand hand, ItemStack crossbow, ItemStack projectile, float soundPitch, boolean creative, float speed, float divergence, float simulated,
                              Spell.ArrowPerks arrowPerks) {
        boolean bl = projectile.isOf(Items.FIREWORK_ROCKET);
        ProjectileEntity projectileEntity;
        if (bl) {
            projectileEntity = new FireworkRocketEntity(world, projectile, shooter, shooter.getX(), shooter.getEyeY() - 0.15000000596046448, shooter.getZ(), true);
        } else {
            projectileEntity = createArrow(world, shooter, crossbow, projectile, arrowPerks);
            if (creative || simulated != 0.0F) {
                ((PersistentProjectileEntity)projectileEntity).pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
            }
        }

        if (shooter instanceof CrossbowUser) {
            CrossbowUser crossbowUser = (CrossbowUser)shooter;
            crossbowUser.shoot(crossbowUser.getTarget(), crossbow, (ProjectileEntity)projectileEntity, simulated);
        } else {
            Vec3d vec3d = shooter.getOppositeRotationVector(1.0F);
            Quaternionf quaternionf = (new Quaternionf()).setAngleAxis((double)(simulated * 0.017453292F), vec3d.x, vec3d.y, vec3d.z);
            Vec3d vec3d2 = shooter.getRotationVec(1.0F);
            Vector3f vector3f = vec3d2.toVector3f().rotate(quaternionf);
            ((ProjectileEntity)projectileEntity).setVelocity((double)vector3f.x(), (double)vector3f.y(), (double)vector3f.z(), speed, divergence);
        }

        crossbow.damage(bl ? 3 : 1, shooter, (e) -> {
            e.sendToolBreakStatus(hand);
        });
        world.spawnEntity((Entity)projectileEntity);
        world.playSound((PlayerEntity)null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 1.0F, soundPitch);
        return projectileEntity;
    }

    private static PersistentProjectileEntity createArrow(World world, LivingEntity entity, ItemStack crossbow, ItemStack arrow, Spell.ArrowPerks arrowPerks) {
        ArrowItem arrowItem = (ArrowItem)(arrow.getItem() instanceof ArrowItem ? arrow.getItem() : Items.ARROW);
        PersistentProjectileEntity persistentProjectileEntity = arrowItem.createArrow(world, arrow, entity);
        if (entity instanceof PlayerEntity) {
            persistentProjectileEntity.setCritical(true);
        }

        persistentProjectileEntity.setSound(SoundEvents.ITEM_CROSSBOW_HIT);
        persistentProjectileEntity.setShotFromCrossbow(true);
        int i = EnchantmentHelper.getLevel(Enchantments.PIERCING, crossbow);
        if (i > 0) {
            persistentProjectileEntity.setPierceLevel((byte)i);
        }

        ((ArrowPerkAdjustable)persistentProjectileEntity).applyArrowPerks(arrowPerks);

        return persistentProjectileEntity;
    }
}

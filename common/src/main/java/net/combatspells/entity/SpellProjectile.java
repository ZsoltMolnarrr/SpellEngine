package net.combatspells.entity;

import io.netty.buffer.Unpooled;
import net.combatspells.CombatSpells;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

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
    protected void initDataTracker() {

    }

    protected void onCollision(HitResult hitResult) {
        HitResult.Type type = hitResult.getType();
        if (type == HitResult.Type.ENTITY) {
            this.onEntityHit((EntityHitResult)hitResult);
            this.world.emitGameEvent(GameEvent.PROJECTILE_LAND, hitResult.getPos(), GameEvent.Emitter.of(this, (BlockState)null));
        } else if (type == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult)hitResult;
            this.onBlockHit(blockHitResult);
            BlockPos blockPos = blockHitResult.getBlockPos();
            this.world.emitGameEvent(GameEvent.PROJECTILE_LAND, blockPos, GameEvent.Emitter.of(this, this.world.getBlockState(blockPos)));
        }
    }

    @Override
    public ItemStack getStack() {
        return renderedItem;
    }


    public class SpawnPacket {
        public static final Identifier ID = new Identifier(CombatSpells.MOD_ID, "spell_projectile_spawn");

        public static Packet<?> create(Entity e, Identifier packetID) {
            if (e.world.isClient)
                throw new IllegalStateException("SpawnPacketUtil.create called on the logical client!");
            PacketByteBuf byteBuf = new PacketByteBuf(Unpooled.buffer());
            byteBuf.writeVarInt(Registry.ENTITY_TYPE.getRawId(e.getType()));
            byteBuf.writeUuid(e.getUuid());
            byteBuf.writeVarInt(e.getId());

            PacketBufUtil.writeVec3d(byteBuf, e.getPos());
            PacketBufUtil.writeAngle(byteBuf, e.getPitch());
            PacketBufUtil.writeAngle(byteBuf, e.getYaw());

            return ServerPlayNetworking.createS2CPacket(packetID, byteBuf);
        }
        public static final class PacketBufUtil {

            /**
             * Packs a floating-point angle into a {@code byte}.
             *
             * @param angle
             *         angle
             * @return packed angle
             */
            public static byte packAngle(float angle) {
                return (byte) MathHelper.floor(angle * 256 / 360);
            }

            /**
             * Unpacks a floating-point angle from a {@code byte}.
             *
             * @param angleByte
             *         packed angle
             * @return angle
             */
            public static float unpackAngle(byte angleByte) {
                return (angleByte * 360) / 256f;
            }

            /**
             * Writes an angle to a {@link PacketByteBuf}.
             *
             * @param byteBuf
             *         destination buffer
             * @param angle
             *         angle
             */
            public static void writeAngle(PacketByteBuf byteBuf, float angle) {
                byteBuf.writeByte(packAngle(angle));
            }

            /**
             * Reads an angle from a {@link PacketByteBuf}.
             *
             * @param byteBuf
             *         source buffer
             * @return angle
             */
            public static float readAngle(PacketByteBuf byteBuf) {
                return unpackAngle(byteBuf.readByte());
            }

            /**
             * Writes a {@link Vec3d} to a {@link PacketByteBuf}.
             *
             * @param byteBuf
             *         destination buffer
             * @param vec3d
             *         vector
             */
            public static void writeVec3d(PacketByteBuf byteBuf, Vec3d vec3d) {
                byteBuf.writeDouble(vec3d.x);
                byteBuf.writeDouble(vec3d.y);
                byteBuf.writeDouble(vec3d.z);
            }

            /**
             * Reads a {@link Vec3d} from a {@link PacketByteBuf}.
             *
             * @param byteBuf
             *         source buffer
             * @return vector
             */
            public static Vec3d readVec3d(PacketByteBuf byteBuf) {
                double x = byteBuf.readDouble();
                double y = byteBuf.readDouble();
                double z = byteBuf.readDouble();
                return new Vec3d(x, y, z);
            }
        }
    }
}

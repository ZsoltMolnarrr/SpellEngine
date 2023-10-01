package net.spell_engine.network;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.ParticleBatch;
import net.spell_engine.config.ServerConfig;
import net.spell_engine.internals.casting.SpellCast;

import java.util.ArrayList;
import java.util.List;

public class Packets {

    public record SpellCastSync(Identifier spellId, float speed, int length) {
        public static Identifier ID = new Identifier(SpellEngineMod.ID, "cast_sync");

        public PacketByteBuf write() {
            PacketByteBuf buffer = PacketByteBufs.create();
            if (spellId == null) {
                buffer.writeString("");
            } else {
                buffer.writeString(spellId.toString());
            }
            buffer.writeFloat(speed);
            buffer.writeInt(length);
            return buffer;
        }
        public static SpellCastSync read(PacketByteBuf buffer) {
            var string = buffer.readString();
            Identifier spellId = null;
            if (!string.isEmpty()) {
                spellId = new Identifier(string);
            }
            var speed = buffer.readFloat();
            var length = buffer.readInt();
            return new SpellCastSync(spellId, speed, length);
        }
    }

    public record SpellRequest(Hand hand, SpellCast.Action action, Identifier spellId, int slot, float progress, int[] targets) {
        public static Identifier ID = new Identifier(SpellEngineMod.ID, "release_request");

        public PacketByteBuf write() {
            PacketByteBuf buffer = PacketByteBufs.create();
            buffer.writeEnumConstant(hand);
            buffer.writeEnumConstant(action);
            buffer.writeString(spellId.toString());
            buffer.writeInt(slot);
            buffer.writeFloat(progress);
            buffer.writeIntArray(targets);
            return buffer;
        }
        public static SpellRequest read(PacketByteBuf buffer) {
            var hand = buffer.readEnumConstant(Hand.class);
            var action = buffer.readEnumConstant(SpellCast.Action.class);
            var spellId = new Identifier(buffer.readString());
            var slot = buffer.readInt();
            var progress = buffer.readFloat();
            var targets = buffer.readIntArray();
            return new SpellRequest(hand, action, spellId, slot, progress, targets);
        }
    }

    public record SpellCooldown(Identifier spellId, int duration) {
        public static Identifier ID = new Identifier(SpellEngineMod.ID, "spell_cooldown");
        public PacketByteBuf write() {
            PacketByteBuf buffer = PacketByteBufs.create();
            buffer.writeString(spellId.toString());
            buffer.writeInt(duration);
            return buffer;
        }

        public static SpellCooldown read(PacketByteBuf buffer) {
            var spellId = new Identifier(buffer.readString());
            int duration = buffer.readInt();
            return new SpellCooldown(spellId, duration);
        }
    }

    public record SpellAnimation(int playerId, SpellCast.Animation type, String name) {
        public static Identifier ID = new Identifier(SpellEngineMod.ID, "spell_animation");
        public PacketByteBuf write() {
            PacketByteBuf buffer = PacketByteBufs.create();
            buffer.writeInt(playerId);
            buffer.writeInt(type.ordinal());
            buffer.writeString(name);
            return buffer;
        }

        public static SpellAnimation read(PacketByteBuf buffer) {
            int playerId = buffer.readInt();
            var type = SpellCast.Animation.values()[buffer.readInt()];
            var name = buffer.readString();
            return new SpellAnimation(playerId, type, name);
        }
    }

    public record ParticleBatches(SourceType sourceType, List<Spawn> spawns) {
        public enum SourceType { ENTITY, COORDINATE }
        public record Spawn(int sourceEntityId, Vec3d sourceLocation, ParticleBatch batch) { }

        public static Identifier ID = new Identifier(SpellEngineMod.ID, "particle_effects");
        public PacketByteBuf write(float countMultiplier) {
            PacketByteBuf buffer = PacketByteBufs.create();
            buffer.writeInt(sourceType.ordinal());
            buffer.writeInt(spawns.size());
            for (var spawn: spawns) {
                buffer.writeInt(spawn.sourceEntityId);
                buffer.writeDouble(spawn.sourceLocation.x);
                buffer.writeDouble(spawn.sourceLocation.y);
                buffer.writeDouble(spawn.sourceLocation.z);
                write(spawn.batch, buffer, countMultiplier);
            }
            return buffer;
        }

        private static void write(ParticleBatch batch, PacketByteBuf buffer, float countMultiplier) {
            buffer.writeString(batch.particle_id);
            buffer.writeInt(batch.shape.ordinal());
            buffer.writeInt(batch.origin.ordinal());
            buffer.writeInt(batch.rotation != null ? batch.rotation.ordinal() : -1);
            buffer.writeFloat(batch.count * countMultiplier);
            buffer.writeFloat(batch.min_speed);
            buffer.writeFloat(batch.max_speed);
            buffer.writeFloat(batch.angle);
            buffer.writeFloat(batch.extent);
        }

        private static ParticleBatch readBatch(PacketByteBuf buffer) {
            return new ParticleBatch(
                    buffer.readString(),
                    ParticleBatch.Shape.values()[buffer.readInt()],
                    ParticleBatch.Origin.values()[buffer.readInt()],
                    ParticleBatch.Rotation.from(buffer.readInt()),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat()
            );
        }

        public static ParticleBatches read(PacketByteBuf buffer) {
            var sourceType = SourceType.values()[buffer.readInt()];
            var spawnCount = buffer.readInt();
            var spawns = new ArrayList<Spawn>();
            for (int i = 0; i < spawnCount; ++i) {
                spawns.add(new Spawn(
                        buffer.readInt(),
                        new Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                        readBatch(buffer)
                ));
            }
            return new ParticleBatches(sourceType, spawns);
        }
    }

    public static class SpellRegistrySync {
        public static Identifier ID = new Identifier(SpellEngineMod.ID, "spell_registry_sync");
    }

    public static class ConfigSync {
        public static Identifier ID = new Identifier(SpellEngineMod.ID, "config_sync");

        public static PacketByteBuf write(ServerConfig config) {
            var gson = new Gson();
            var json = gson.toJson(config);
            var buffer = PacketByteBufs.create();
            buffer.writeString(json);
            return buffer;
        }

        public static ServerConfig read(PacketByteBuf buffer) {
            var gson = new Gson();
            var json = buffer.readString();
            var config = gson.fromJson(json, ServerConfig.class);
            return config;
        }
    }
}

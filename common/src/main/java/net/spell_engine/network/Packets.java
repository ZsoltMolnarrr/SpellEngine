package net.spell_engine.network;

import com.google.gson.Gson;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.ParticleBatch;
import net.spell_engine.config.ServerConfig;
import net.spell_engine.internals.SpellAnimationType;
import net.spell_engine.internals.SpellCastAction;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;

public class Packets {
    public record SpellRequest(SpellCastAction action, int slot, int remainingUseTicks, int[] targets) {
        public static Identifier ID = new Identifier(SpellEngineMod.ID, "release_request");

        public PacketByteBuf write() {
            PacketByteBuf buffer = PacketByteBufs.create();
            buffer.writeEnumConstant(action);
            buffer.writeInt(slot);
            buffer.writeInt(remainingUseTicks);
            buffer.writeIntArray(targets);
            return buffer;
        }
        public static SpellRequest read(PacketByteBuf buffer) {
            var action = buffer.readEnumConstant(SpellCastAction.class);
            var slot = buffer.readInt();
            var remainingUseTicks = buffer.readInt();
            var targets = buffer.readIntArray();
            return new SpellRequest(action, slot, remainingUseTicks, targets);
        }
    }

    public record SpellAnimation(int playerId, SpellAnimationType type, String name) {
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
            var type = SpellAnimationType.values()[buffer.readInt()];
            var name = buffer.readString();
            return new SpellAnimation(playerId, type, name);
        }
    }

    public record ParticleBatches(int sourceEntityId, ParticleBatch[] batches) {
        public static Identifier ID = new Identifier(SpellEngineMod.ID, "particle_effects");
        public PacketByteBuf write(float countMultiplier) {
            PacketByteBuf buffer = PacketByteBufs.create();
            buffer.writeInt(sourceEntityId);
            buffer.writeInt(batches.length);
            for(var batch: batches) {
                write(batch, buffer, countMultiplier);
            }
            return buffer;
        }

        private static void write(ParticleBatch batch, PacketByteBuf buffer, float countMultiplier) {
            buffer.writeString(batch.particle_id);
            buffer.writeInt(batch.shape.ordinal());
            buffer.writeInt(batch.origin.ordinal());
            buffer.writeFloat(batch.count * countMultiplier);
            buffer.writeFloat(batch.min_speed);
            buffer.writeFloat(batch.max_speed);
        }

        private static ParticleBatch readBatch(PacketByteBuf buffer) {
            return new ParticleBatch(
                    buffer.readString(),
                    ParticleBatch.Shape.values()[buffer.readInt()],
                    ParticleBatch.Origin.values()[buffer.readInt()],
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat()
            );
        }

        public static ParticleBatches read(PacketByteBuf buffer) {
            var sourceEntityId = buffer.readInt();
            var batchCount = buffer.readInt();
            var batches = new ArrayList<ParticleBatch>();
            for (int i = 0; i < batchCount; ++i) {
                var batch = readBatch(buffer);
                batches.add(batch);
            }
            ParticleBatch[] array = new ParticleBatch[batches.size()];
            array = batches.toArray(array);
            return new ParticleBatches(sourceEntityId, array);
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

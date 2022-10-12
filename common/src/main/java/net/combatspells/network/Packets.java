package net.combatspells.network;

import com.google.gson.Gson;
import net.combatspells.CombatSpells;
import net.combatspells.api.spell.ParticleBatch;
import net.combatspells.config.ServerConfig;
import net.combatspells.internals.SpellAnimationType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;

public class Packets {
    public record ReleaseRequest(int slot, int remainingUseTicks, int[] targets) {
        public static Identifier ID = new Identifier(CombatSpells.MOD_ID, "release_request");
        public PacketByteBuf write() {
            PacketByteBuf buffer = PacketByteBufs.create();
            buffer.writeInt(slot);
            buffer.writeInt(remainingUseTicks);
            buffer.writeIntArray(targets);
            return buffer;
        }
        public static ReleaseRequest read(PacketByteBuf buffer) {
            var slot = buffer.readInt();
            var remainingUseTicks = buffer.readInt();
            var targets = buffer.readIntArray();
            return new ReleaseRequest(slot, remainingUseTicks, targets);
        }
    }

    public record SpellAnimation(int playerId, SpellAnimationType type, String name) {
        public static Identifier ID = new Identifier(CombatSpells.MOD_ID, "spell_animation");
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
        public static Identifier ID = new Identifier(CombatSpells.MOD_ID, "particle_effects");
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
        public static Identifier ID = new Identifier(CombatSpells.MOD_ID, "spell_registry_sync");
    }

    public static class ConfigSync {
        public static Identifier ID = new Identifier(CombatSpells.MOD_ID, "config_sync");

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

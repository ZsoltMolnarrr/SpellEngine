package net.spell_engine.network;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.config.ServerConfig;
import net.spell_engine.internals.cost.SpellCooldownManager;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.delivery.melee.Melee;

import java.util.*;

/// Every packet is a plain record with a `public static final Identifier ID`, an instance
/// `write(PacketByteBuf)` and a `static read(PacketByteBuf)` (1.20.1 has no `CustomPayload` /
/// `PacketCodec`). {@link Payload} is the tiny SE-owned contract the loader transports
/// (`Platform.util().networkS2C_Send(...)` etc.) use to encode a record into a buffer.
public class Packets {

    /// Loader-neutral payload contract: the packet's channel id plus its buffer encoder.
    public interface Payload {
        Identifier id();
        void write(PacketByteBuf buffer);

        /// Encodes this payload into a fresh buffer (the `ID` is NOT written — it is the channel).
        default PacketByteBuf toBuffer() {
            var buffer = new PacketByteBuf(Unpooled.buffer());
            write(buffer);
            return buffer;
        }
    }

    // MARK: Casting protocol — shared snapshot wire helpers below.

    private static void writeTargetSnapshot(PacketByteBuf buffer, SpellCast.TargetSnapshot snapshot) {
        buffer.writeIntArray(snapshot.entityIds().stream().mapToInt(Integer::intValue).toArray());
        var location = snapshot.location();
        if (location != null) {
            buffer.writeBoolean(true);
            buffer.writeDouble(location.x);
            buffer.writeDouble(location.y);
            buffer.writeDouble(location.z);
        } else {
            buffer.writeBoolean(false);
        }
    }

    private static SpellCast.TargetSnapshot readTargetSnapshot(PacketByteBuf buffer) {
        var entityIds = Arrays.stream(buffer.readIntArray()).boxed().toList();
        Vec3d location = null;
        if (buffer.readBoolean()) {
            location = new Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
        return new SpellCast.TargetSnapshot(entityIds, location);
    }

    /// C2S: begin casting an option. Instants carry their targeting snapshot along (and fire
    /// immediately); timed casts follow up with a TargetStream.
    public record CastRequest(Identifier spellId, SpellCast.TargetSnapshot snapshot) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "cast_request");
        @Override public Identifier id() { return ID; }

        public void write(PacketByteBuf buffer) {
            buffer.writeString(spellId.toString());
            writeTargetSnapshot(buffer, snapshot);
        }

        public static CastRequest read(PacketByteBuf buffer) {
            var spellId = new Identifier(buffer.readString());
            return new CastRequest(spellId, readTargetSnapshot(buffer));
        }
    }

    /// C2S: replication of the client's cursor targeting, sent every tick IF CHANGED while a
    /// cursor-driven cast is active. Rides the ordered play channel — arrival order is send
    /// order, so the receiver's last-received slot is always the newest.
    public record TargetStream(Identifier spellId, SpellCast.TargetSnapshot snapshot) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "target_stream");
        @Override public Identifier id() { return ID; }

        public void write(PacketByteBuf buffer) {
            buffer.writeString(spellId.toString());
            writeTargetSnapshot(buffer, snapshot);
        }

        public static TargetStream read(PacketByteBuf buffer) {
            var spellId = new Identifier(buffer.readString());
            var snapshot = readTargetSnapshot(buffer);
            return new TargetStream(spellId, snapshot);
        }
    }

    /// C2S: the player's end-input (key up): cancels a timed cast, completes a channel early,
    /// releases a charge — carrying the final snapshot of the release frame (zero staleness).
    public record CastInput(Identifier spellId, SpellCast.TargetSnapshot snapshot) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "cast_input");
        @Override public Identifier id() { return ID; }

        public void write(PacketByteBuf buffer) {
            buffer.writeString(spellId.toString());
            writeTargetSnapshot(buffer, snapshot);
        }

        public static CastInput read(PacketByteBuf buffer) {
            var spellId = new Identifier(buffer.readString());
            return new CastInput(spellId, readTargetSnapshot(buffer));
        }
    }

    public record SpellCooldown(Identifier spellId, int duration) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_cooldown");
        @Override public Identifier id() { return ID; }

        public void write(PacketByteBuf buffer) {
            buffer.writeString(spellId.toString());
            buffer.writeInt(duration);
        }

        public static SpellCooldown read(PacketByteBuf buffer) {
            var spellId = new Identifier(buffer.readString());
            int duration = buffer.readInt();
            return new SpellCooldown(spellId, duration);
        }
    }

    public record SpellCooldownSync(int baseTick, Map<Identifier, SpellCooldownManager.Entry> cooldowns) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "cooldown_sync");
        @Override public Identifier id() { return ID; }

        public void write(PacketByteBuf buffer) {
            buffer.writeInt(baseTick);
            buffer.writeInt(cooldowns.size());
            for (var entry: cooldowns.entrySet()) {
                buffer.writeString(entry.getKey().toString());
                buffer.writeInt(entry.getValue().startTick());
                buffer.writeInt(entry.getValue().endTick());
            }
        }

        public static SpellCooldownSync read(PacketByteBuf buffer) {
            int baseTick = buffer.readInt();
            int size = buffer.readInt();
            var cooldowns = new HashMap<Identifier, SpellCooldownManager.Entry>();
            for (int i = 0; i < size; ++i) {
                var spellId = new Identifier(buffer.readString());
                var startTick = buffer.readInt();
                var endTick = buffer.readInt();
                cooldowns.put(spellId, new SpellCooldownManager.Entry(startTick, endTick));
            }
            return new SpellCooldownSync(baseTick, cooldowns);
        }
    }

    public record SpellAnimation(int playerId, SpellCast.Animation type, String name, float speed) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_animation");
        @Override public Identifier id() { return ID; }

        public void write(PacketByteBuf buffer) {
            buffer.writeInt(playerId);
            buffer.writeInt(type.ordinal());
            buffer.writeString(name);
            buffer.writeFloat(speed);
        }

        public static SpellAnimation read(PacketByteBuf buffer) {
            int playerId = buffer.readInt();
            var type = SpellCast.Animation.values()[buffer.readInt()];
            var name = buffer.readString();
            var speed = buffer.readFloat();
            return new SpellAnimation(playerId, type, name, speed);
        }
    }

    public record SpellMessage(String translationKey, Formatting format) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_message");
        @Override public Identifier id() { return ID; }

        public void write(PacketByteBuf buffer) {
            buffer.writeString(translationKey);
            buffer.writeInt(format.ordinal());
        }

        public static SpellMessage read(PacketByteBuf buffer) {
            var text = buffer.readString();
            var format = Formatting.values()[buffer.readInt()];
            return new SpellMessage(text, format);
        }
    }

    public record ParticleEffects(SourceType sourceType, float countMultiplier, List<Spawn> spawns) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "particle_effects");
        @Override public Identifier id() { return ID; }

        public enum SourceType { ENTITY, COORDINATE }
        public record Spawn(int sourceEntityId, float yaw, float pitch, Vec3d sourceLocation, ParticleGroup effect) { }

        // The effect ships as GSON (same as `SpellContainerSync`): self-describing named
        // fields, so enums are no longer serialized by ordinal and none of them is
        // append-only. `countMultiplier` is a real packet field applied at spawn time,
        // instead of being baked into the counts at write time.
        private static final Gson gson = new Gson();

        public void write(PacketByteBuf buffer) {
            buffer.writeInt(sourceType.ordinal());
            buffer.writeFloat(countMultiplier);
            buffer.writeInt(spawns.size());
            for (var spawn: spawns) {
                buffer.writeInt(spawn.sourceEntityId);
                buffer.writeFloat(spawn.yaw);
                buffer.writeFloat(spawn.pitch);
                buffer.writeDouble(spawn.sourceLocation.x);
                buffer.writeDouble(spawn.sourceLocation.y);
                buffer.writeDouble(spawn.sourceLocation.z);
                buffer.writeString(gson.toJson(spawn.effect));
            }
        }

        public static ParticleEffects read(PacketByteBuf buffer) {
            var sourceType = SourceType.values()[buffer.readInt()];
            var countMultiplier = buffer.readFloat();
            var spawnCount = buffer.readInt();
            var spawns = new ArrayList<Spawn>();
            for (int i = 0; i < spawnCount; ++i) {
                spawns.add(new Spawn(
                        buffer.readInt(),
                        buffer.readFloat(),
                        buffer.readFloat(),
                        new Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                        gson.fromJson(buffer.readString(), ParticleGroup.class)
                ));
            }
            return new ParticleEffects(sourceType, countMultiplier, spawns);
        }
    }

    public record SpellContainerSync(LinkedHashMap<String, SpellContainer> containers) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_container_sync");
        @Override public Identifier id() { return ID; }

        private static final Gson gson = new Gson();
        public void write(PacketByteBuf buffer) {
            buffer.writeInt(containers.size());
            for (var entry: containers.entrySet()) {
                buffer.writeString(entry.getKey());
                var json = gson.toJson(entry.getValue());
                buffer.writeString(json);
            }
        }

        public static SpellContainerSync read(PacketByteBuf buffer) {
            int size = buffer.readInt();
            var containers = new LinkedHashMap<String, SpellContainer>();
            for (int i = 0; i < size; ++i) {
                var key = buffer.readString();
                var json = buffer.readString();
                var container = gson.fromJson(json, SpellContainer.class);
                containers.put(key, container);
            }
            return new SpellContainerSync(containers);
        }
    }

    /// S2C on JOIN: the server's config as one GSON string (must stay < 32767 chars).
    public record ConfigSync(ServerConfig config) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "config_sync");
        @Override public Identifier id() { return ID; }

        private static final Gson gson = new Gson();

        public void write(PacketByteBuf buffer) {
            var json = gson.toJson(this.config);
            buffer.writeString(json);
        }

        public static ConfigSync read(PacketByteBuf buffer) {
            var gson = new Gson();
            var json = buffer.readString();
            var config = gson.fromJson(json, ServerConfig.class);
            return new ConfigSync(config);
        }
    }

    /// S2C on JOIN: the spell-assignment table (`SpellAssignments.encoded`) as 10 000-char GSON
    /// chunks (a single `writeString` caps at 32767 chars). Still one custom payload, so the
    /// whole thing is bounded by the 1 MiB `CustomPayloadS2CPacket` limit.
    public record SpellRegistrySync(List<String> chunks) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_registry_sync");
        @Override public Identifier id() { return ID; }

        public void write(PacketByteBuf buffer) {
            buffer.writeInt(chunks.size());
            for (var chunk: chunks) {
                buffer.writeString(chunk);
            }
        }

        public static SpellRegistrySync read(PacketByteBuf buffer) {
            var chunkCount = buffer.readInt();
            var chunks = new ArrayList<String>();
            for (int i = 0; i < chunkCount; ++i) {
                chunks.add(buffer.readString());
            }
            return new SpellRegistrySync(chunks);
        }
    }

    public record AttackAvailable(Identifier spellId, List<Melee.Attack> attacks) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "attack_available");
        @Override public Identifier id() { return ID; }

        private static final Gson gson = new Gson();

        public void write(PacketByteBuf buffer) {
            buffer.writeString(spellId.toString());

            // Serialize MeleeAttack list to JSON
            buffer.writeInt(attacks.size());
            for (var attack : attacks) {
                var attackJson = gson.toJson(attack);
                buffer.writeString(attackJson);
            }
        }

        public static AttackAvailable read(PacketByteBuf buffer) {
            var spellId = new Identifier(buffer.readString());

            // Deserialize MeleeAttack list from JSON
            var attackCount = buffer.readInt();
            var attacks = new ArrayList<Melee.Attack>();
            for (int i = 0; i < attackCount; i++) {
                var attackJson = buffer.readString();
                var attack = gson.fromJson(attackJson, Melee.Attack.class);
                attacks.add(attack);
            }

            return new AttackAvailable(spellId, attacks);
        }
    }

    public record AttackPerform(Melee.AttackContext attackContext, int[] targetIds) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "attack_perform");
        @Override public Identifier id() { return ID; }

        public void write(PacketByteBuf buffer) {
            buffer.writeString(attackContext.spellId().toString());
            buffer.writeString(attackContext.attackId());
            buffer.writeFloat(attackContext.charge());
            buffer.writeIntArray(targetIds);
        }

        public static AttackPerform read(PacketByteBuf buffer) {
            var spellId = new Identifier(buffer.readString());
            var attackId = buffer.readString();
            var charge = buffer.readFloat();
            var context = new Melee.AttackContext(spellId, attackId, charge);
            var targetIds = buffer.readIntArray();
            return new AttackPerform(context, targetIds);
        }
    }

    public record AttackFxBroadcast(Melee.AttackContext attackContext) implements Payload {
        public static final Identifier ID = new Identifier(SpellEngineMod.ID, "attack_fx_broadcast");
        @Override public Identifier id() { return ID; }

        public void write(PacketByteBuf buffer) {
            buffer.writeString(attackContext.spellId().toString());
            buffer.writeString(attackContext.attackId());
            buffer.writeFloat(attackContext.charge());
        }

        public static AttackFxBroadcast read(PacketByteBuf buffer) {
            var spellId = new Identifier(buffer.readString());
            var attackId = buffer.readString();
            var charge = buffer.readFloat();
            return new AttackFxBroadcast(new Melee.AttackContext(spellId, attackId, charge));
        }
    }
}

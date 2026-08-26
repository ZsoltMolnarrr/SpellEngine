package net.spell_engine.network;

import com.google.gson.Gson;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.config.ServerConfig;
import net.spell_engine.internals.cost.SpellCooldownManager;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.delivery.melee.Melee;

import java.util.*;

public class Packets {

    // MARK: Casting protocol — shared snapshot wire helpers below.

    private static void writeTargetSnapshot(RegistryFriendlyByteBuf buffer, SpellCast.TargetSnapshot snapshot) {
        buffer.writeVarIntArray(snapshot.entityIds().stream().mapToInt(Integer::intValue).toArray());
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

    private static SpellCast.TargetSnapshot readTargetSnapshot(RegistryFriendlyByteBuf buffer) {
        var entityIds = Arrays.stream(buffer.readVarIntArray()).boxed().toList();
        Vec3 location = null;
        if (buffer.readBoolean()) {
            location = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
        return new SpellCast.TargetSnapshot(entityIds, location);
    }

    /// C2S: begin casting an option. Instants carry their targeting snapshot along (and fire
    /// immediately); timed casts follow up with a TargetStream.
    public record CastRequest(Identifier spellId, SpellCast.TargetSnapshot snapshot) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "cast_request");
        public static final CustomPacketPayload.Type<CastRequest> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CastRequest> CODEC = StreamCodec.ofMember(CastRequest::write, CastRequest::read);
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(spellId.toString());
            writeTargetSnapshot(buffer, snapshot);
        }

        public static CastRequest read(RegistryFriendlyByteBuf buffer) {
            var spellId = Identifier.parse(buffer.readUtf());
            return new CastRequest(spellId, readTargetSnapshot(buffer));
        }
    }

    /// C2S: replication of the client's cursor targeting, sent every tick IF CHANGED while a
    /// cursor-driven cast is active. Rides the ordered play channel — arrival order is send
    /// order, so the receiver's last-received slot is always the newest.
    public record TargetStream(Identifier spellId, SpellCast.TargetSnapshot snapshot) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "target_stream");
        public static final CustomPacketPayload.Type<TargetStream> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, TargetStream> CODEC = StreamCodec.ofMember(TargetStream::write, TargetStream::read);
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(spellId.toString());
            writeTargetSnapshot(buffer, snapshot);
        }

        public static TargetStream read(RegistryFriendlyByteBuf buffer) {
            var spellId = Identifier.parse(buffer.readUtf());
            var snapshot = readTargetSnapshot(buffer);
            return new TargetStream(spellId, snapshot);
        }
    }

    /// C2S: the player's end-input (key up): cancels a timed cast, completes a channel early,
    /// releases a charge — carrying the final snapshot of the release frame (zero staleness).
    public record CastInput(Identifier spellId, SpellCast.TargetSnapshot snapshot) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "cast_input");
        public static final CustomPacketPayload.Type<CastInput> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CastInput> CODEC = StreamCodec.ofMember(CastInput::write, CastInput::read);
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(spellId.toString());
            writeTargetSnapshot(buffer, snapshot);
        }

        public static CastInput read(RegistryFriendlyByteBuf buffer) {
            var spellId = Identifier.parse(buffer.readUtf());
            return new CastInput(spellId, readTargetSnapshot(buffer));
        }
    }

    public record SpellCooldown(Identifier spellId, int duration) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_cooldown");
        public static final CustomPacketPayload.Type<SpellCooldown> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SpellCooldown> CODEC = StreamCodec.ofMember(SpellCooldown::write, SpellCooldown::read);
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(spellId.toString());
            buffer.writeInt(duration);
        }

        public static SpellCooldown read(RegistryFriendlyByteBuf buffer) {
            var spellId = Identifier.parse(buffer.readUtf());
            int duration = buffer.readInt();
            return new SpellCooldown(spellId, duration);
        }
    }

    public record SpellCooldownSync(int baseTick, Map<Identifier, SpellCooldownManager.Entry> cooldowns) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "cooldown_sync");
        public static final CustomPacketPayload.Type<SpellCooldownSync> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SpellCooldownSync> CODEC = StreamCodec.ofMember(SpellCooldownSync::write, SpellCooldownSync::read);
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeInt(baseTick);
            buffer.writeInt(cooldowns.size());
            for (var entry: cooldowns.entrySet()) {
                buffer.writeUtf(entry.getKey().toString());
                buffer.writeInt(entry.getValue().startTick());
                buffer.writeInt(entry.getValue().endTick());
            }
        }

        public static SpellCooldownSync read(RegistryFriendlyByteBuf buffer) {
            int baseTick = buffer.readInt();
            int size = buffer.readInt();
            var cooldowns = new HashMap<Identifier, SpellCooldownManager.Entry>();
            for (int i = 0; i < size; ++i) {
                var spellId = Identifier.parse(buffer.readUtf());
                var startTick = buffer.readInt();
                var endTick = buffer.readInt();
                cooldowns.put(spellId, new SpellCooldownManager.Entry(startTick, endTick));
            }
            return new SpellCooldownSync(baseTick, cooldowns);
        }
    }

    public record SpellAnimation(int playerId, SpellCast.Animation animation, String name, float speed) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_animation");
        public static final CustomPacketPayload.Type<SpellAnimation> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SpellAnimation> CODEC = StreamCodec.ofMember(SpellAnimation::write, SpellAnimation::read);
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeInt(playerId);
            buffer.writeInt(animation.ordinal());
            buffer.writeUtf(name);
            buffer.writeFloat(speed);
        }

        public static SpellAnimation read(RegistryFriendlyByteBuf buffer) {
            int playerId = buffer.readInt();
            var type = SpellCast.Animation.values()[buffer.readInt()];
            var name = buffer.readUtf();
            var speed = buffer.readFloat();
            return new SpellAnimation(playerId, type, name, speed);
        }
    }

    public record SpellMessage(String translationKey, ChatFormatting format) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_message");
        public static final CustomPacketPayload.Type<SpellMessage> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SpellMessage> CODEC = StreamCodec.ofMember(SpellMessage::write, SpellMessage::read);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(translationKey);
            buffer.writeInt(format.ordinal());
        }

        public static SpellMessage read(RegistryFriendlyByteBuf buffer) {
            var text = buffer.readUtf();
            var format = ChatFormatting.values()[buffer.readInt()];
            return new SpellMessage(text, format);
        }
    }

    public record ParticleEffects(SourceType sourceType, float countMultiplier, List<Spawn> spawns) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "particle_effects");
        public static final CustomPacketPayload.Type<ParticleEffects> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ParticleEffects> CODEC = StreamCodec.ofMember(ParticleEffects::write, ParticleEffects::read);
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        public enum SourceType { ENTITY, COORDINATE }
        public record Spawn(int sourceEntityId, float yaw, float pitch, Vec3 sourceLocation, ParticleGroup effect) { }

        // The effect ships as GSON (same as `SpellContainerSync`): self-describing named
        // fields, so enums are no longer serialized by ordinal and none of them is
        // append-only. `countMultiplier` is a real packet field applied at spawn time,
        // instead of being baked into the counts at write time.
        private static final Gson gson = new Gson();

        public void write(RegistryFriendlyByteBuf buffer) {
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
                buffer.writeUtf(gson.toJson(spawn.effect));
            }
        }

        public static ParticleEffects read(RegistryFriendlyByteBuf buffer) {
            var sourceType = SourceType.values()[buffer.readInt()];
            var countMultiplier = buffer.readFloat();
            var spawnCount = buffer.readInt();
            var spawns = new ArrayList<Spawn>();
            for (int i = 0; i < spawnCount; ++i) {
                spawns.add(new Spawn(
                        buffer.readInt(),
                        buffer.readFloat(),
                        buffer.readFloat(),
                        new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                        gson.fromJson(buffer.readUtf(), ParticleGroup.class)
                ));
            }
            return new ParticleEffects(sourceType, countMultiplier, spawns);
        }
    }

    public record SpellContainerSync(LinkedHashMap<String, SpellContainer> containers) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_container_sync");
        public static final CustomPacketPayload.Type<SpellContainerSync> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, SpellContainerSync> CODEC = StreamCodec.ofMember(SpellContainerSync::write, SpellContainerSync::read);
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        private static final Gson gson = new Gson();
        public void write(FriendlyByteBuf buffer) {
            buffer.writeInt(containers.size());
            for (var entry: containers.entrySet()) {
                buffer.writeUtf(entry.getKey());
                var json = gson.toJson(entry.getValue());
                buffer.writeUtf(json);
            }
        }

        public static SpellContainerSync read(FriendlyByteBuf buffer) {
            int size = buffer.readInt();
            var containers = new LinkedHashMap<String, SpellContainer>();
            for (int i = 0; i < size; ++i) {
                var key = buffer.readUtf();
                var json = buffer.readUtf();
                var container = gson.fromJson(json, SpellContainer.class);
                containers.put(key, container);
            }
            return new SpellContainerSync(containers);
        }
    }

    public record ConfigSync(ServerConfig config) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "config_sync");
        public static final CustomPacketPayload.Type<ConfigSync> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, ConfigSync> CODEC = StreamCodec.ofMember(ConfigSync::write, ConfigSync::read);
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        private static final Gson gson = new Gson();

        public void write(FriendlyByteBuf buffer) {
            var json = gson.toJson(this.config);
            buffer.writeUtf(json);
        }

        public static ConfigSync read(FriendlyByteBuf buffer) {
            var gson = new Gson();
            var json = buffer.readUtf();
            var config = gson.fromJson(json, ServerConfig.class);
            return new ConfigSync(config);
        }
    }

    public record SpellRegistrySync(List<String> chunks) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_registry_sync");
        public static final CustomPacketPayload.Type<SpellRegistrySync> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, SpellRegistrySync> CODEC = StreamCodec.ofMember(SpellRegistrySync::write, SpellRegistrySync::read);

        private void write(FriendlyByteBuf buffer) {
            buffer.writeInt(chunks.size());
            for (var chunk: chunks) {
                buffer.writeUtf(chunk);
            }
        }

        private static SpellRegistrySync read(FriendlyByteBuf buffer) {
            var chunkCount = buffer.readInt();
            var chunks = new ArrayList<String>();
            for (int i = 0; i < chunkCount; ++i) {
                chunks.add(buffer.readUtf());
            }
            return new SpellRegistrySync(chunks);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    public record Ack(String code) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "ack");
        public static final CustomPacketPayload.Type<Ack> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, Ack> CODEC = StreamCodec.ofMember(Ack::write, Ack::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(code);
        }

        public static Ack read(FriendlyByteBuf buffer) {
            var code = buffer.readUtf();
            return new Ack(code);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    public record AttackAvailable(Identifier spellId, List<Melee.Attack> attacks) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "attack_available");
        public static final CustomPacketPayload.Type<AttackAvailable> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, AttackAvailable> CODEC = StreamCodec.ofMember(AttackAvailable::write, AttackAvailable::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        private static final Gson gson = new Gson();

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(spellId.toString());

            // Serialize MeleeAttack list to JSON
            buffer.writeInt(attacks.size());
            for (var attack : attacks) {
                var attackJson = gson.toJson(attack);
                buffer.writeUtf(attackJson);
            }
        }

        public static AttackAvailable read(FriendlyByteBuf buffer) {
            var spellId = Identifier.parse(buffer.readUtf());

            // Deserialize MeleeAttack list from JSON
            var attackCount = buffer.readInt();
            var attacks = new ArrayList<Melee.Attack>();
            for (int i = 0; i < attackCount; i++) {
                var attackJson = buffer.readUtf();
                var attack = gson.fromJson(attackJson, Melee.Attack.class);
                attacks.add(attack);
            }

            return new AttackAvailable(spellId, attacks);
        }
    }

    public record AttackPerform(Melee.AttackContext attackContext, int[] targetIds) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "attack_perform");
        public static final CustomPacketPayload.Type<AttackPerform> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, AttackPerform> CODEC = StreamCodec.ofMember(AttackPerform::write, AttackPerform::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(attackContext.spellId().toString());
            buffer.writeUtf(attackContext.attackId());
            buffer.writeFloat(attackContext.charge());
            buffer.writeVarIntArray(targetIds);
        }

        public static AttackPerform read(FriendlyByteBuf buffer) {
            var spellId = Identifier.parse(buffer.readUtf());
            var attackId = buffer.readUtf();
            var charge = buffer.readFloat();
            var context = new Melee.AttackContext(spellId, attackId, charge);
            var targetIds = buffer.readVarIntArray();
            return new AttackPerform(context, targetIds);
        }
    }

    public record AttackFxBroadcast(Melee.AttackContext attackContext) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "attack_fx_broadcast");
        public static final CustomPacketPayload.Type<AttackFxBroadcast> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, AttackFxBroadcast> CODEC = StreamCodec.ofMember(AttackFxBroadcast::write, AttackFxBroadcast::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(attackContext.spellId().toString());
            buffer.writeUtf(attackContext.attackId());
            buffer.writeFloat(attackContext.charge());
        }

        public static AttackFxBroadcast read(FriendlyByteBuf buffer) {
            var spellId = Identifier.parse(buffer.readUtf());
            var attackId = buffer.readUtf();
            var charge = buffer.readFloat();
            return new AttackFxBroadcast(new Melee.AttackContext(spellId, attackId, charge));
        }
    }
}

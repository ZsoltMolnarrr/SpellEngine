package net.spell_engine.internals;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.attachment.SyncedEntityData;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.api.effect.Synchronized;
import net.spell_engine.api.spell.fx.ModelEffectAttachment;

import java.lang.reflect.Type;
import java.util.List;

/// Every synced per-entity value Spell Engine keeps on vanilla entities, as native attachments.
/// Values are decoded by the packet codecs at receipt, so readers get typed, immutable values with
/// no per-read parsing. Initialised from `SpellEngineMod.init()` on both sides (the client has to
/// know the types to accept them).
public final class SpellEngineAttachments {
    private SpellEngineAttachments() { }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SpellEngineMod.ID, path);
    }

    /// Forces class initialisation, so every attachment is registered before the loaders need them.
    public static void init() { }

    // MARK: Players (casting + melee)

    /// JSON of the player's cast process (`SpellCast.Process.SyncFormat`), "" when not casting.
    public static final SyncedEntityData<String> CAST_PROCESS =
            SyncedEntityData.create(id("cast_process"), "", ByteBufCodecs.STRING_UTF8);

    /// JSON of the player's castable options (`SpellCast.Option.SyncFormat[]`), "" when none.
    public static final SyncedEntityData<String> CAST_OPTIONS =
            SyncedEntityData.create(id("cast_options"), "", ByteBufCodecs.STRING_UTF8);

    /// Extra ground slipperiness while a melee skill attack is in progress.
    public static final SyncedEntityData<Float> EXTRA_SLIPPERINESS =
            SyncedEntityData.create(id("extra_slipperiness"), 0F, ByteBufCodecs.FLOAT);

    // MARK: Living entities (status effect sync, tint, attached model FX)

    private static final StreamCodec<ByteBuf, Synchronized.Effect> EFFECT_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, effect -> BuiltInRegistries.MOB_EFFECT.getId(effect.effect()),
            ByteBufCodecs.VAR_INT, Synchronized.Effect::amplifier,
            ByteBufCodecs.VAR_LONG, Synchronized.Effect::appliedAtWorldTime,
            (rawId, amplifier, appliedAt) -> new Synchronized.Effect(BuiltInRegistries.MOB_EFFECT.byId(rawId), amplifier, appliedAt));

    /// The entity's synchronized status effects (those flagged `Synchronized`), for all trackers —
    /// vanilla only tells the affected player about its effects.
    public static final SyncedEntityData<List<Synchronized.Effect>> SYNCED_EFFECTS =
            SyncedEntityData.create(id("synced_effects"), List.of(),
                    EFFECT_CODEC.apply(ByteBufCodecs.list())
                            // An effect unknown to this side decodes to null — drop it instead of crashing a renderer.
                            .map(list -> list.stream().filter(e -> e.effect() != null).toList(), list -> list));

    /// Blended ARGB tint of the entity's rendered appearance, see `EntityTints`.
    public static final SyncedEntityData<Integer> TINT_ARGB =
            SyncedEntityData.create(id("tint_argb"), EntityTints.NEUTRAL, ByteBufCodecs.INT);

    private static final Gson gson = new Gson();
    private static final Type modelFxListType = new TypeToken<List<ModelEffectAttachment.Entry>>(){}.getType();

    /// Model FX following the entity. `ModelEffect` is a GSON data class (no Codec), so it travels
    /// as JSON — parsed once at receipt.
    public static final SyncedEntityData<List<ModelEffectAttachment.Entry>> MODEL_FX =
            SyncedEntityData.create(id("model_fx"), List.of(),
                    ByteBufCodecs.STRING_UTF8.map(json -> {
                        if (json.isEmpty()) { return List.<ModelEffectAttachment.Entry>of(); }
                        List<ModelEffectAttachment.Entry> parsed = gson.fromJson(json, modelFxListType);
                        return parsed == null ? List.<ModelEffectAttachment.Entry>of() : List.copyOf(parsed);
                    }, list -> list.isEmpty() ? "" : gson.toJson(list)));

    // MARK: Arrows

    /// Spells carried by a `PersistentProjectileEntity` (arrow perks + impacts). Persisted with the
    /// arrow, as before.
    public static final SyncedEntityData<List<Identifier>> ARROW_SPELLS =
            SyncedEntityData.create(id("arrow_spells"), List.of(),
                    Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    Identifier.CODEC.listOf());
}

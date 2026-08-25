package net.spell_engine.internals.cost;
import net.spell_engine.Platform;

import com.google.common.collect.Maps;
import net.minecraft.entity.Entity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.network.Packets;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SpellCooldownManager {
    public record Entry(int startTick, int endTick) {
        int timeLeft(int currentTick) {
            return Math.max(0, endTick - currentTick);
        }
    }
    private final Map<Identifier, Entry> entries = Maps.newHashMap();
    private int tick;

    private final Entity owner;

    public SpellCooldownManager(Entity owner) {
        this.owner = owner;
    }

    public void tickUpdate() {
        ++this.tick;
        this.update(true);
    }

    @Nullable private static Identifier groupId(Spell spell) {
        if (spell.cost.cooldown != null) {
            var group = spell.cost.cooldown.group;
            if (group != null) {
                return Identifier.of("group", group);
            }
        }
        return null;
    }

    public boolean isCoolingDown(RegistryEntry<Spell> spell) {
        return this.getCooldownProgress(spell, 0.0f) > 0.0f;
    }

    public float getCooldownProgress(RegistryEntry<Spell> spell, float tickDelta) {
        var id = spell.getKey().get().getValue();
        var groupId = groupId(spell.value());
        if (groupId == null) {
            return this.getCooldownProgress(id, tickDelta);
        } else {
            return Math.max(
                    this.getCooldownProgress(id, tickDelta),
                    this.getCooldownProgress(groupId, tickDelta)
            );
        }
    }

    protected float getCooldownProgress(Identifier spell, float tickDelta) {
        SpellCooldownManager.Entry entry = this.entries.get(spell);
        if (entry != null) {
            float f = entry.endTick - entry.startTick;
            float g = (float)entry.endTick - ((float)this.tick + tickDelta);
            return MathHelper.clamp(g / f, 0.0f, 1.0f);
        }
        return 0.0f;
    }

    public int getCooldownDuration(RegistryEntry<Spell> spell) {
        var id = spell.getKey().get().getValue();
        var groupId = groupId(spell.value());
        if (groupId == null) {
            return this.getCooldownDuration(id);
        } else {
            return Math.max(
                    this.getCooldownDuration(id),
                    this.getCooldownDuration(groupId)
            );
        }
    }

    protected int getCooldownDuration(Identifier spell) {
        SpellCooldownManager.Entry entry = this.entries.get(spell);
        if (entry != null) {
            return entry.timeLeft(this.tick);
        }
        return 0;
    }

    public void setDurationLeft(RegistryEntry<Spell> spell, int duration) {
        var spellId = spell.getKey().get().getValue();
        this.setDurationLeft(spellId, duration);
        var groupId = groupId(spell.value());
        if (groupId != null) {
            this.setDurationLeft(groupId, duration);
        }
    }

    protected void setDurationLeft(Identifier spell, int duration) {
        var existingEntry = this.entries.get(spell);
        if (existingEntry != null) {
            this.entries.put(spell, new Entry(this.tick, this.tick + duration));
        } else if (duration > 0) {
            this.entries.put(spell, new Entry(this.tick, this.tick + duration));
        }
    }

    public void update(boolean sync) {
        if (!this.entries.isEmpty()) {
            Iterator<Map.Entry<Identifier, Entry>> iterator = this.entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Identifier, Entry> entry = iterator.next();
                if (entry.getValue().endTick > this.tick) continue;
                iterator.remove();
                if (sync) {
                    this.cooldownCleared(entry.getKey());
                }
            }
        }
    }

    public void set(RegistryEntry<Spell> spell, int duration) {
        this.set(spell, duration, true);
    }

    public void set(RegistryEntry<Spell> spell, int duration, boolean force) {
        var spellId = spell.getKey().get().getValue();
        this.set(spellId, duration, force);
        var groupId = groupId(spell.value());
        if (groupId != null) {
            this.set(groupId, duration, force);
        }
    }

    protected void set(Identifier spell, int duration, boolean force) {
        if (force
                || !this.entries.containsKey(spell)
                || (this.entries.get(spell).timeLeft(tick) < duration)
        ) {
            this.entries.put(spell, new Entry(this.tick, this.tick + duration));
            this.cooldownSet(spell, duration);
        }
    }

    public void remove(Identifier spell) {
        this.entries.remove(spell);
        this.cooldownCleared(spell);
    }

    protected void cooldownSet(Identifier spell, int duration) {
        if (owner instanceof ServerPlayerEntity serverPlayer) {
            Platform.util().networkS2C_Send(serverPlayer, new Packets.SpellCooldown(spell, duration));
        }
    }

    protected void cooldownCleared(Identifier spell) {
        if (owner instanceof ServerPlayerEntity serverPlayer) {
            Platform.util().networkS2C_Send(serverPlayer, new Packets.SpellCooldown(spell, 0));
        }
    }

    private static final String NBT_KEY = "spell_engine_cooldowns";
    // Stored as a list (ReadView cannot enumerate keys), one element per spell
    public void writeCustomData(WriteView view) {
        var cooldowns = view.getList(NBT_KEY);
        for (var entry: entries.entrySet()) {
            var spell = entry.getKey();
            var cooldown = entry.getValue();
            var cooldownData = cooldowns.add();
            cooldownData.putString("spell", spell.toString());
            cooldownData.putInt("start", cooldown.startTick - tick);
            cooldownData.putInt("end", cooldown.endTick - tick);
        }
    }

    public void readCustomData(ReadView view) {
        view.getOptionalListReadView(NBT_KEY).ifPresent(cooldowns -> cooldowns.stream().forEach(cooldownData -> {
            var spellString = cooldownData.getString("spell", "");
            if (spellString.isEmpty()) { return; }
            var spell = Identifier.of(spellString);
            var start = cooldownData.getInt("start", 0);
            var end = cooldownData.getInt("end", 0);
            entries.put(spell, new Entry(start, end));
        }));
    }

    public void reset(@Nullable Identifier spellId) {
        if (spellId == null) {
            entries.clear();
        } else {
            entries.remove(spellId);
        }
        pushSync();
    }

    public void pushSync() {
        if (owner instanceof ServerPlayerEntity serverPlayer) {
            Platform.util().networkS2C_Send(serverPlayer, new Packets.SpellCooldownSync(this.tick, Map.copyOf(this.entries)));
        }
    }

    public void acceptSync(int baseTick, Map<Identifier, Entry> cooldowns) {
        this.tick = baseTick;
        this.entries.clear();
        this.entries.putAll(cooldowns);
    }

    public List<Identifier> spellsOnCooldown() {
        return this.entries.keySet().stream().toList();
    }
}

package net.spell_engine.internals;

import com.google.common.collect.Maps;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.spell_engine.network.Packets;

import java.util.Iterator;
import java.util.Map;

public class SpellCooldownManager {
    private final Map<Identifier, Entry> entries = Maps.newHashMap();
    private int tick;

    private final PlayerEntity owner;

    public SpellCooldownManager(PlayerEntity owner) {
        this.owner = owner;
    }

    public boolean isCoolingDown(Identifier spell) {
        return this.getCooldownProgress(spell, 0.0f) > 0.0f;
    }

    public float getCooldownProgress(Identifier spell, float tickDelta) {
        SpellCooldownManager.Entry entry = this.entries.get(spell);
        if (entry != null) {
            float f = entry.endTick - entry.startTick;
            float g = (float)entry.endTick - ((float)this.tick + tickDelta);
            return MathHelper.clamp(g / f, 0.0f, 1.0f);
        }
        return 0.0f;
    }

    public void update() {
        ++this.tick;
        if (!this.entries.isEmpty()) {
            Iterator<Map.Entry<Identifier, Entry>> iterator = this.entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Identifier, SpellCooldownManager.Entry> entry = iterator.next();
                if (entry.getValue().endTick > this.tick) continue;
                iterator.remove();
                this.cooldownCleared(entry.getKey());
            }
        }
    }

    public void set(Identifier spell, int duration) {
        this.entries.put(spell, new SpellCooldownManager.Entry(this.tick, this.tick + duration));
        this.cooldownSet(spell, duration);
    }

    public void remove(Identifier spell) {
        this.entries.remove(spell);
        this.cooldownCleared(spell);
    }

    protected void cooldownSet(Identifier spell, int duration) {
        if (owner instanceof ServerPlayerEntity serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, Packets.SpellCooldown.ID,
                    new Packets.SpellCooldown(spell, duration).write());
        }
    }

    protected void cooldownCleared(Identifier spell) {
        if (owner instanceof ServerPlayerEntity serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, Packets.SpellCooldown.ID,
                    new Packets.SpellCooldown(spell, 0).write());
        }
    }

    record Entry(int startTick, int endTick) { }
}

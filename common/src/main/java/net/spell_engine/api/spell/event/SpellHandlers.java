package net.spell_engine.api.spell.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellHelper;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpellHandlers {
    public static final Map<String, CustomDelivery> customDelivery = new HashMap<>();
    public interface CustomDelivery {
        boolean onSpellDelivery(World world, RegistryEntry<Spell> spellEntry, LivingEntity caster,
                                List<SpellHelper.DeliveryTarget> targets, SpellHelper.ImpactContext context,
                                @Nullable Vec3d targetLocation);
    }
    public static void registerCustomDelivery(Identifier id, CustomDelivery delivery) {
        customDelivery.put(id.toString(), delivery);
    }

    public static final Map<String, CustomImpact> customImpact = new HashMap<>();
    public record ImpactResult(boolean success, boolean critical) { }
    public interface CustomImpact {
        ImpactResult onSpellImpact(RegistryEntry<Spell> spellEntry, SpellPower.Result spellPower,
                           LivingEntity caster, @Nullable Entity target,
                           SpellHelper.ImpactContext context);
    }
    public static void registerCustomImpact(Identifier id, CustomImpact impact) {
        customImpact.put(id.toString(), impact);
    }
}

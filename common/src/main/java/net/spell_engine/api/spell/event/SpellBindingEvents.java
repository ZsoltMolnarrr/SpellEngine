package net.spell_engine.api.spell.event;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.spell_engine.api.event.Event;
import net.spell_engine.api.spell.Spell;
import org.jetbrains.annotations.Nullable;

/**
 * Events related to the Spell Binding Table.
 */
public class SpellBindingEvents {
    /**
     * Fired on the server, after a spell has been bound to an item at the Spell Binding Table.
     * Costs (levels, lapis) have already been consumed and the item stack already contains the spell.
     */
    public static final Event<SpellBound> SPELL_BOUND = new Event<>();
    public interface SpellBound {
        /**
         * @param player     the player who bound the spell
         * @param spell      the spell that was bound
         * @param itemStack  the item the spell was bound to, already updated
         * @param poolId     the spell pool of the item's container, if any
         * @param isComplete whether the container now holds every spell of its pool tier
         * @param world      the world of the binding table
         * @param pos        the position of the binding table
         */
        record Args(PlayerEntity player,
                    RegistryEntry<Spell> spell,
                    ItemStack itemStack,
                    @Nullable Identifier poolId,
                    boolean isComplete,
                    World world,
                    BlockPos pos) {}
        void onSpellBound(Args args);
    }
}

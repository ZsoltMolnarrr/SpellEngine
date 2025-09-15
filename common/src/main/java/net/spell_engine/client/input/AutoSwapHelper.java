package net.spell_engine.client.input;

import net.fabric_extras.ranged_weapon.api.EntityAttributes_RangedWeapon;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.compat.trinkets.TrinketsCompat;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.utils.AttributeModifierUtil;
import org.jetbrains.annotations.Nullable;

public class AutoSwapHelper {
    public static boolean autoSwapForAttack() {
        var player = MinecraftClient.getInstance().player;
        if (player == null || player.isSpectator()) { return false; }
        var mainHand = player.getMainHandStack();
        var offHand = player.getInventory().offHand.get(0);

        if (mainHand.isEmpty()
                || offHand.isEmpty()
                || isPlaceable(mainHand)
                || !isAnyWeapon(mainHand)
                || !(hasSpells(mainHand) || isUsable(mainHand))
        ) {
            return false;
        }

        if (!isMeleeWeapon(mainHand) && isMeleeWeapon(offHand)) {
            swapHeldItems();
            return true;
        } else {
            return false;
        }
    }

    public static boolean autoSwapForSpells() {
        var player = MinecraftClient.getInstance().player;
        if (player == null || player.isSpectator()) { return false; }
        var mainHand = player.getMainHandStack();
        var offHand = player.getInventory().offHand.get(0);

        if (mainHand.isEmpty()
                || offHand.isEmpty()
                || isUsable(mainHand)
                || !isAnyWeapon(mainHand)
                || !isAnyWeapon(offHand)) {
            return false;
        }

        var mainHandType = spellContentType(mainHand);
        var offHandType = spellContentType(offHand);
        var spellbookType = spellContentType(TrinketsCompat.getSpellBookStack(player));
        if (spellbookType != null) {
            if (!hasSpells(mainHand) && mainHandType != spellbookType && offHandType == spellbookType) {
                swapHeldItems();
                return true;
            }
        }
        return false;
    }

    public static void swapHeldItems() {
        MinecraftClient
                .getInstance()
                .getNetworkHandler()
                .sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
    }

    public static boolean isPlaceable(ItemStack itemStack) {
        return itemStack.getItem() instanceof BlockItem;
    }

    public static boolean isUsable(ItemStack itemStack) {
        return itemStack.getUseAction() != UseAction.NONE || itemStack.getItem() instanceof MiningToolItem;
    }

    public static boolean isMeleeWeapon(ItemStack itemStack) {
        return AttributeModifierUtil.hasModifier(itemStack, EntityAttributes.GENERIC_ATTACK_DAMAGE)
                && AttributeModifierUtil.hasModifier(itemStack, EntityAttributes.GENERIC_ATTACK_SPEED);
    }

    public static boolean isAnyWeapon(ItemStack itemStack) {
        if (isMeleeWeapon(itemStack)) {
            return true;
        }
        if (FabricLoader.getInstance().isModLoaded("ranged_weapon_api")
                && AttributeModifierUtil.hasModifier(itemStack, EntityAttributes_RangedWeapon.DAMAGE.entry)) {
            return true;
        }
        return false;
    }

    @Nullable
    private static SpellContainer.ContentType spellContentType(ItemStack itemStack) {
        var container = SpellContainerHelper.containerFromItemStack(itemStack);
        if (container != null) {
            return container.content();
        }
        return null;
    }

    private static boolean hasSpells(ItemStack itemStack) {
        var container = SpellContainerHelper.containerFromItemStack(itemStack);
        if (container != null) {
            return !container.spell_ids().isEmpty();
        }
        return false;
    }
}

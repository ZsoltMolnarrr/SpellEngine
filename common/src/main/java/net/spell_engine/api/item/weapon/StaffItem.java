package net.spell_engine.api.item.weapon;

import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.spell_engine.api.item.ItemAttributeModifiers;
import net.spell_engine.rpg_series.item.ConfigurableAttributes;
import org.jetbrains.annotations.Nullable;

public class StaffItem extends ToolItem implements ConfigurableAttributes {
    @Nullable private ItemAttributeModifiers attributeModifiers;

    public StaffItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    public void setAttributes(ItemAttributeModifiers attributeModifiers) {
        this.attributeModifiers = attributeModifiers;
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        if (attributeModifiers != null) {
            return attributeModifiers.forSlot(slot);
        }
        return super.getAttributeModifiers(slot);
    }

    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        return !miner.isCreative();
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.damage(1, attacker, entity -> entity.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
        return true;
    }
}

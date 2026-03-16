package net.spell_engine.mixin.entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.internals.container.SpellContainerSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(PlayerEntity.class)
public class PlayerSpellContainerMixin implements SpellContainerSource.Owner {
    private final Map<String, List<SpellContainerSource.SourcedContainer>> spellContainerCache = new LinkedHashMap<>();
    @Override
    public Map<String, List<SpellContainerSource.SourcedContainer>> spellContainerCache() {
        return spellContainerCache;
    }

    private final Map<Identifier, List<Spell.Modifier>> spellModifierCache = new HashMap<>();
    @Override
    public Map<Identifier, List<Spell.Modifier>> spellModifierCache() {
        return spellModifierCache;
    }

    private LinkedHashMap<String, SpellContainer> serverSideSpellContainers = new LinkedHashMap<>();
    @Override
    public LinkedHashMap<String, SpellContainer> serverSideSpellContainers() {
        return serverSideSpellContainers;
    }

    private boolean serverSideSpellContainersDirty = false;
    @Override
    public void markServerSideSpellContainersDirty() {
        serverSideSpellContainersDirty = true;
    }

    private SpellContainerSource.Result currentSpellContainers = SpellContainerSource.Result.EMPTY;
    @Override
    public void setSpellContainers(SpellContainerSource.Result result) {
        currentSpellContainers = result;
    }
    @Override
    public SpellContainerSource.Result getSpellContainers() {
        return currentSpellContainers;
    }

    private SpellContainer lastMainHandContainer = SpellContainer.EMPTY;
    public Map<String, Object> lastProviderStates = new HashMap<>();
    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine_SpellContainer(CallbackInfo ci) {
        var player = (PlayerEntity) (Object) this;

        if (serverSideSpellContainersDirty) {
            // If the server-side containers are dirty, we need to update them
            SpellContainerSource.syncServerSideContainers(player);
            serverSideSpellContainersDirty = false;
        }

        // Special treatment for main hand stack
        // as it changes the most frequently
        // Checking Container instead of ItemStack, as ItemStack instances sometimes
        // get their contents replaced, without a reference update
        var mainHandContainer = SpellContainerHelper.containerFromItemStack(player.getMainHandStack());
        if (!Objects.equals(mainHandContainer, lastMainHandContainer)) {
            SpellContainerSource.setDirty(player, SpellContainerSource.MAIN_HAND);
        }
        
        for (var entry: SpellContainerSource.sources) {
            if (entry.checker() == null) { continue; }
            var currentState = entry.checker().current(player);
            if (!currentState.equals(lastProviderStates.get(entry.name()))){
                SpellContainerSource.setDirty(player, entry.name());
            }
            lastProviderStates.put(entry.name(), currentState);
        }

        SpellContainerSource.update(player);

        lastMainHandContainer = mainHandContainer;
    }

//    @Inject(method = "equipStack", at = @At("TAIL"))
//    private void equipStack_TAIL_SpellEngine_SpellContainer(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
//        var player = (PlayerEntity) (Object) this;
//        if (slot == EquipmentSlot.MAINHAND) {
//            SpellContainerSource.setDirty(player, SpellContainerSource.MAIN_HAND);
//        } else if (slot == EquipmentSlot.OFFHAND) {
//            SpellContainerSource.setDirty(player, SpellContainerSource.OFF_HAND);
//        } else if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
//            SpellContainerSource.setDirty(player, SpellContainerSource.ARMOR);
//        }
//    }
}

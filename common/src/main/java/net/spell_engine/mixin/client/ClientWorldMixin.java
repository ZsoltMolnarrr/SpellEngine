package net.spell_engine.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.spell_engine.utils.SoundPlayerWorld;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientLevel.class)
public class ClientWorldMixin implements SoundPlayerWorld {
    public void playSoundFromEntity(Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch) {
        var clientWorld = (ClientLevel) (Object) this;
        var clientPlayer = Minecraft.getInstance().player;
        clientWorld.playSound(clientPlayer, entity, sound, category, volume, pitch);
    }
}

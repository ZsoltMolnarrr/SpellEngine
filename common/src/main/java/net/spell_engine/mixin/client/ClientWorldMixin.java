package net.spell_engine.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.spell_engine.utils.SoundPlayerWorld;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientWorld.class)
public class ClientWorldMixin implements SoundPlayerWorld {
    public void playSoundFromEntity(Entity entity, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        var clientWorld = (ClientWorld) (Object) this;
        var clientPlayer = MinecraftClient.getInstance().player;
        clientWorld.playSoundFromEntity(clientPlayer, entity, sound, category, volume, pitch);
    }
}

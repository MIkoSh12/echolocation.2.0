package com.example.echolocation.mixin;

import com.example.echolocation.EchoManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    // В 26.x метод play возвращает SoundEngine.PlayResult, поэтому CallbackInfoReturnable.
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"))
    private void echolocation$onPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        // пропускаем музыку, интерфейс и звуки без позиции
        if (sound.isRelative()) return;
        if (sound.getAttenuation() == SoundInstance.Attenuation.NONE) return;
        SoundSource src = sound.getSource();
        if (src == SoundSource.MUSIC || src == SoundSource.RECORDS
                || src == SoundSource.MASTER || src == SoundSource.AMBIENT) return;

        EchoManager.onSound(sound.getX(), sound.getY(), sound.getZ(), sound.getVolume());
    }
}

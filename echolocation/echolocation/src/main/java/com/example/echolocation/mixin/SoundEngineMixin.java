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
    // RETURN, а не HEAD: к этому моменту звук уже "разрешён" игрой,
    // и getVolume() не падает с NullPointerException.
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("RETURN"))
    private void echolocation$onPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        try {
            if (sound == null || sound.isRelative()) return;
            if (sound.getAttenuation() == SoundInstance.Attenuation.NONE) return;
            SoundSource src = sound.getSource();
            if (src == SoundSource.MUSIC || src == SoundSource.RECORDS
                    || src == SoundSource.MASTER || src == SoundSource.AMBIENT) return;

            float volume;
            try {
                volume = sound.getVolume();
            } catch (Throwable t) {
                volume = 1.0f; // запасной вариант, если громкость недоступна
            }
            EchoManager.onSound(sound.getX(), sound.getY(), sound.getZ(), volume);
        } catch (Throwable t) {
            // Мод никогда не должен ломать воспроизведение звука.
        }
    }
}

package com.example.echolocation.mixin;

import com.example.echolocation.EchoRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    /** Освобождаем буфер при закрытии рендерера (как требует документация Fabric). */
    @Inject(method = "close", at = @At("RETURN"))
    private void echolocation$close(CallbackInfo ci) {
        EchoRenderer.close();
    }
}

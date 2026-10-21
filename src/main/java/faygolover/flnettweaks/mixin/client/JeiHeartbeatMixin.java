package faygolover.flnettweaks.mixin.client;

import faygolover.flnettweaks.client.Heartbeat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Плавный вход: шаги запуска JEI (без JEI миксин не применяется). */
@Pseudo
@Mixin(targets = "mezz.jei.core.util.LoggedTimer", remap = false)
public abstract class JeiHeartbeatMixin {
    @Inject(method = "start", at = @At("HEAD"), require = 0, remap = false)
    private void fln$step(String message, CallbackInfo ci) {
        Heartbeat.step("JEI: " + message);
    }
}

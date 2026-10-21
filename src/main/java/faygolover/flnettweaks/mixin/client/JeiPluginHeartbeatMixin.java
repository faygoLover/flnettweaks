package faygolover.flnettweaks.mixin.client;

import faygolover.flnettweaks.client.Heartbeat;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Плавный вход: JEI вызывает плагин мода (без JEI миксин не применяется). */
@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCallerTimer", remap = false)
public abstract class JeiPluginHeartbeatMixin {
    @Inject(method = "begin", at = @At("HEAD"), require = 0, remap = false)
    private void fln$step(String title, ResourceLocation plugin, CallbackInfo ci) {
        Heartbeat.step("JEI: " + title + " — " + plugin);
    }
}

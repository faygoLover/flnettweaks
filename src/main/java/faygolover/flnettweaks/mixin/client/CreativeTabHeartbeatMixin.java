package faygolover.flnettweaks.mixin.client;

import faygolover.flnettweaks.client.Heartbeat;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Плавный вход: сборка вкладок предметов (список JEI) — точка для кадра загрузки. */
@Mixin(CreativeModeTab.class)
public abstract class CreativeTabHeartbeatMixin {
    @Inject(method = "buildContents", at = @At("HEAD"))
    private void fln$beat(CallbackInfo ci) {
        Heartbeat.beat();
    }
}

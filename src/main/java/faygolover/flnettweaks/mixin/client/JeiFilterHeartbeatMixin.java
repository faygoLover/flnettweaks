package faygolover.flnettweaks.mixin.client;

import faygolover.flnettweaks.client.Heartbeat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Плавный вход: JEI строит поиск — по предмету за раз (без JEI миксин не применяется). */
@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientFilter", remap = false)
public abstract class JeiFilterHeartbeatMixin {
    @Inject(method = "addIngredient", at = @At("HEAD"), require = 0, remap = false)
    private void fln$beat(CallbackInfo ci) {
        Heartbeat.beat();
    }
}

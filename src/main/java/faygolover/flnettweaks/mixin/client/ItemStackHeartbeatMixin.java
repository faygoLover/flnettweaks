package faygolover.flnettweaks.mixin.client;

import faygolover.flnettweaks.client.Heartbeat;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Плавный вход: подсказки всех предметов (поиск JEI) — точка для кадра загрузки. */
@Mixin(ItemStack.class)
public abstract class ItemStackHeartbeatMixin {
    @Inject(method = "getTooltipLines", at = @At("HEAD"))
    private void fln$beat(CallbackInfoReturnable<List<?>> cir) {
        Heartbeat.beat();
    }
}

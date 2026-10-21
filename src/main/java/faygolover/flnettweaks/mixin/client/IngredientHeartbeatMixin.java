package faygolover.flnettweaks.mixin.client;

import faygolover.flnettweaks.client.Heartbeat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Плавный вход: ингредиенты рецептов (рецепты JEI) — точка для кадра загрузки. */
@Mixin(Ingredient.class)
public abstract class IngredientHeartbeatMixin {
    @Inject(method = "getItems", at = @At("HEAD"))
    private void fln$beat(CallbackInfoReturnable<ItemStack[]> cir) {
        Heartbeat.beat();
    }
}

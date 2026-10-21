package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Предел NBT внутри пакета (ваниль 2 МБ). */
@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufMixin {
    @ModifyConstant(method = "readNbt()Lnet/minecraft/nbt/CompoundTag;", constant = @Constant(longValue = 2097152L))
    private long fln$max(long vanilla) {
        return FlnConfig.nbt();
    }
}

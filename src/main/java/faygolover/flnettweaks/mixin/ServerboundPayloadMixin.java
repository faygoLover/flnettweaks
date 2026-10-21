package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Пакет мода от игрока серверу: ваниль 32 КБ — главная причина отключений сразу после входа. */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class ServerboundPayloadMixin {
    @ModifyConstant(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", constant = @Constant(intValue = 32767))
    private int fln$max(int vanilla) {
        return FlnConfig.serverboundPayload();
    }
}

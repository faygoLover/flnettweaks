package faygolover.flnettweaks.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Данные пакета мода от игрока без копии — для разбивки статистики по типу сообщения. */
@Mixin(ServerboundCustomPayloadPacket.class)
public interface ServerboundPayloadAccess {
    @Accessor("data") FriendlyByteBuf fln$data();
}

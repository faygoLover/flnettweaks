package faygolover.flnettweaks.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Данные пакета мода без копии (getData() копирует весь буфер) — для разбивки статистики по типу сообщения. */
@Mixin(ClientboundCustomPayloadPacket.class)
public interface ClientboundPayloadAccess {
    @Accessor("data") FriendlyByteBuf fln$data();
}

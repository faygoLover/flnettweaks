package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Пакет мода от сервера игроку (ваниль 1 МБ) — и при создании на сервере, и при чтении у игрока. */
@Mixin(ClientboundCustomPayloadPacket.class)
public abstract class ClientboundPayloadMixin {
    @ModifyConstant(method = "<init>*", constant = @Constant(intValue = 1048576), require = 2)
    private int fln$max(int vanilla) {
        return FlnConfig.clientboundPayload();
    }
}

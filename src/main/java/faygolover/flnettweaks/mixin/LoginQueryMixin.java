package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Пакеты входа от сервера (Forge: список модов, реестры, конфиги; ваниль 1 МБ). */
@Mixin(ClientboundCustomQueryPacket.class)
public abstract class LoginQueryMixin {
    @ModifyConstant(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", constant = @Constant(intValue = 1048576))
    private int fln$max(int vanilla) {
        return FlnConfig.loginPayload();
    }
}

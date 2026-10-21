package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Подключение к серверу: тайм-аут тишины (ваниль 30 с). */
@Mixin(targets = "net.minecraft.network.Connection$1")
public abstract class ClientChannelInitMixin {
    @Redirect(method = "initChannel", remap = false, at = @At(value = "NEW", target = "(I)Lio/netty/handler/timeout/ReadTimeoutHandler;", remap = false))
    private ReadTimeoutHandler fln$timeout(int vanilla) {
        return new ReadTimeoutHandler(FlnConfig.readTimeout());
    }
}

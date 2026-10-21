package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Сервер: тайм-аут тишины от игрока (ваниль 30 с, Forge: -Dforge.readTimeout). */
@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public abstract class ServerChannelInitMixin {
    @Redirect(method = "initChannel", remap = false, at = @At(value = "NEW", target = "(I)Lio/netty/handler/timeout/ReadTimeoutHandler;", remap = false))
    private ReadTimeoutHandler fln$timeout(int vanilla) {
        return new ReadTimeoutHandler(Math.max(vanilla, FlnConfig.readTimeout()));
    }
}

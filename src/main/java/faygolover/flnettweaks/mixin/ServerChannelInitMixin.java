package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.Tls;
import io.netty.channel.Channel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Сервер: тайм-аут тишины от игрока (ваниль 30 с, Forge: -Dforge.readTimeout), приём TLS и проверка связи на том же порту. */
@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public abstract class ServerChannelInitMixin {
    @Redirect(method = "initChannel", remap = false, at = @At(value = "NEW", target = "(I)Lio/netty/handler/timeout/ReadTimeoutHandler;", remap = false))
    private ReadTimeoutHandler fln$timeout(int vanilla) {
        return new ReadTimeoutHandler(Math.max(vanilla, FlnConfig.readTimeout()));
    }

    @Inject(method = "initChannel", at = @At("TAIL"), remap = false)
    private void fln$tls(Channel ch, CallbackInfo ci) {
        faygolover.flnettweaks.Probe.serverChannel(ch);   // после TLS: addFirst ниже ставит определитель TLS перед проверкой связи
        Tls.serverChannel(ch);
    }
}

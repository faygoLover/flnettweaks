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

/** Подключение к серверу: тайм-аут тишины (ваниль 30 с) и TLS-обёртка (tls.client). */
@Mixin(targets = "net.minecraft.network.Connection$1")
public abstract class ClientChannelInitMixin {
    @Redirect(method = "initChannel", remap = false, at = @At(value = "NEW", target = "(I)Lio/netty/handler/timeout/ReadTimeoutHandler;", remap = false))
    private ReadTimeoutHandler fln$timeout(int vanilla) {
        return new ReadTimeoutHandler(faygolover.flnettweaks.NetPrefs.clientReadTimeout());
    }

    @Inject(method = "initChannel", at = @At("TAIL"), remap = false)
    private void fln$tls(Channel ch, CallbackInfo ci) {
        Tls.clientChannel(ch);
    }
}

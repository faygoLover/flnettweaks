package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.IntentionRaw;
import faygolover.flnettweaks.NetPrefs;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Сервер: настройки соединения игрока (NetPrefs) — на его соединение, ещё до входа. */
@Mixin(ServerHandshakePacketListenerImpl.class)
public abstract class ServerHandshakeMixin {
    @Shadow @Final private Connection connection;

    @Inject(method = "handleIntention", at = @At("HEAD"))
    private void fln$prefs(ClientIntentionPacket p, CallbackInfo ci) {
        if (p.getIntention() != ConnectionProtocol.LOGIN) return;
        NetPrefs prefs = NetPrefs.decode(((IntentionRaw) p).fln$raw());
        if (prefs != null) NetPrefs.applyServer(connection.channel(), prefs);
    }
}

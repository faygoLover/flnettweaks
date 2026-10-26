package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.ConnLog;
import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import faygolover.flnettweaks.Net;
import faygolover.flnettweaks.NetStats;
import io.netty.channel.Channel;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.TimeUnit;

/**
 * Keepalive: проверка раз в 15 с остаётся, но ждать ответ можно keepAliveSeconds; перед отключением — запись, почему.
 * Отключение: ваниль закрывает соединение, только когда сообщение «вы отключены» ушло игроку. В мёртвый канал оно не
 * уходит — игрок висит в мире призраком до readSeconds, а его повторный вход ждёт (DELAY_ACCEPT). Закрываем сами
 * через closeAfterKickSeconds.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGameListenerMixin {
    @Shadow private long keepAliveTime;
    @Shadow private boolean keepAlivePending;
    @Shadow public ServerPlayer player;
    @Shadow @Final public Connection connection;
    @Shadow @Final private MinecraftServer server;
    @Unique private long fln$loggedFor = -1;
    @Unique private String fln$keepAliveKick;
    @Unique private boolean fln$closing;

    @ModifyConstant(method = "tick", constant = @Constant(longValue = 15000L))
    private long fln$keepAlive(long vanilla) {
        if (!keepAlivePending) return vanilla;
        long limit = Math.max(vanilla, faygolover.flnettweaks.NetPrefs.keepAliveMs(connection));
        long waited = Util.getMillis() - keepAliveTime;
        // Отключение доходит не сразу (канал занят) — а tick зовёт нас каждый тик: пишем один раз на keepalive.
        if (waited >= limit && fln$loggedFor != keepAliveTime) {
            fln$loggedFor = keepAliveTime;
            NetStats.Conn st = NetStats.of(connection.channel());
            long now = System.currentTimeMillis();
            long quiet = st.lastInAt == 0 ? -1 : (now - st.lastInAt) / 1000;
            fln$keepAliveKick = "не ответил на keepalive за " + waited / 1000 + " с (последний пакет от игрока " + quiet + " с назад)";
            FlnTweaks.LOG.warn("flnettweaks: {} не ответил на keepalive за {} с — отключаем (последний пакет от игрока {} с назад: {}; предел — timeouts.keepAliveSeconds в {})",
                    player.getGameProfile().getName(), waited / 1000, quiet, st.lastIn, Net.CONFIG);
        }
        return limit;
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("TAIL"))
    private void fln$closeLater(Component reason, CallbackInfo ci) {
        if (fln$closing) return;   // после тайм-аута keepalive disconnect зовётся каждый тик
        fln$closing = true;
        ConnLog.kicked(connection, fln$keepAliveKick != null ? fln$keepAliveKick : ConnLog.reason(reason));
        int sec = FlnConfig.closeAfterKickSeconds();
        Channel ch = connection.channel();
        if (sec <= 0 || ch == null || connection.isMemoryConnection()) return;
        String name = player.getGameProfile().getName();
        ch.eventLoop().schedule(() -> {
            if (!ch.isOpen()) return;
            FlnTweaks.LOG.warn("flnettweaks: {} — сообщение об отключении не дошло за {} с, закрываем соединение (причина: {})",
                    name, sec, ConnLog.reason(reason));
            ConnLog.forced(ch, sec);
            ch.close();   // в сетевом потоке: основной поток сервера не ждёт закрытия (с TLS ждал 3 с); игрока уберёт следующий тик
        }, sec, TimeUnit.SECONDS);
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void fln$logout(Component reason, CallbackInfo ci) {
        ConnLog.serverLogout(connection, reason);
    }
}

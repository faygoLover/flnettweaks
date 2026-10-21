package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import faygolover.flnettweaks.Net;
import faygolover.flnettweaks.NetStats;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keepalive: проверка раз в 15 с остаётся, но ждать ответ можно keepAliveSeconds; перед отключением — запись, почему. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGameListenerMixin {
    @Shadow private long keepAliveTime;
    @Shadow private boolean keepAlivePending;
    @Shadow public ServerPlayer player;
    @Shadow @Final public Connection connection;
    @Unique private long fln$loggedFor = -1;

    @ModifyConstant(method = "tick", constant = @Constant(longValue = 15000L))
    private long fln$keepAlive(long vanilla) {
        if (!keepAlivePending) return vanilla;
        long limit = Math.max(vanilla, FlnConfig.keepAliveMs());
        long waited = Util.getMillis() - keepAliveTime;
        // Отключение доходит не сразу (канал занят) — а tick зовёт нас каждый тик: пишем один раз на keepalive.
        if (waited >= limit && fln$loggedFor != keepAliveTime) {
            fln$loggedFor = keepAliveTime;
            NetStats.Conn st = NetStats.of(connection.channel());
            long now = System.currentTimeMillis();
            FlnTweaks.LOG.warn("flnettweaks: {} не ответил на keepalive за {} с — отключаем (последний пакет от игрока {} с назад: {}; предел — timeouts.keepAliveSeconds в {})",
                    player.getGameProfile().getName(), waited / 1000, st.lastInAt == 0 ? -1 : (now - st.lastInAt) / 1000, st.lastIn, Net.CONFIG);
        }
        return limit;
    }
}

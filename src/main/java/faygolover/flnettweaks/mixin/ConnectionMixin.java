package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import faygolover.flnettweaks.Net;
import faygolover.flnettweaks.NetStats;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.TimeoutException;
import net.minecraft.network.Connection;
import net.minecraft.network.SkipPacketException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ошибки соединения: ваниль пишет их только в debug и показывает «Internal Exception: класс: текст».
 * Здесь — понятный текст (что за пакет/лимит), запись в лог и подробный тайм-аут.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow private Channel channel;
    @Shadow @Final private PacketFlow receiving;
    @Shadow public abstract void disconnect(Component reason);
    @Unique private boolean fln$logged;

    @ModifyVariable(method = "exceptionCaught", at = @At("HEAD"), argsOnly = true, remap = false)
    private Throwable fln$explain(Throwable t) {
        if (t instanceof SkipPacketException || t instanceof TimeoutException) return t;
        Throwable e = Net.explain(t);
        if (!fln$logged && channel != null && channel.isOpen()) {
            fln$logged = true;
            String who = Net.who(channel);
            if (e instanceof Net.Explained) FlnTweaks.LOG.warn("flnettweaks: соединение с {} закрыто: {}", who, e.getMessage(), e.getCause());
            else FlnTweaks.LOG.warn("flnettweaks: ошибка соединения с {}", who, t);
        }
        return e;
    }

    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true, remap = false)
    private void fln$timeout(ChannelHandlerContext ctx, Throwable t, CallbackInfo ci) {
        if (!(t instanceof TimeoutException) || channel == null || !channel.isOpen()) return;
        NetStats.Conn st = NetStats.of(channel);
        long now = System.currentTimeMillis();
        String last = st.lastInAt == 0 ? "ничего" : st.lastIn + " (" + Net.size(st.lastInSize) + ") " + (now - st.lastInAt) / 1000 + " с назад";
        String who = receiving == PacketFlow.CLIENTBOUND ? "сервера" : Net.who(channel);
        String msg = "flnettweaks: от " + who + " ничего не приходило " + FlnConfig.readTimeout() + " с (последнее: " + last
                + "). Предел — timeouts.readSeconds в " + Net.CONFIG;
        FlnTweaks.LOG.warn(msg);
        ci.cancel();
        disconnect(Component.translatable("disconnect.timeout").append("\n").append(Component.literal(msg)));
    }
}

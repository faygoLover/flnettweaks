package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import faygolover.flnettweaks.Net;
import faygolover.flnettweaks.client.JoinTracker;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Сколько пакет обрабатывался в основном потоке: долгие — в лог (кто тормозит вход/игру/сервер).
 * У клиента во время входа — флаг JoinTracker.handling: по нему client.Heartbeat рисует кадры загрузки.
 */
@Mixin(PacketUtils.class)
public abstract class PacketUtilsMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "*", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private static void fln$timed(Packet packet, PacketListener listener) {
        long t = System.nanoTime();
        boolean join = JoinTracker.active && !(listener instanceof ServerGamePacketListenerImpl) && !JoinTracker.handling;
        if (join) {
            JoinTracker.handlingWhat = Net.describe(packet);
            JoinTracker.handlingSince = System.currentTimeMillis();
            JoinTracker.handling = true;
            if (FMLEnvironment.dist.isClient()) faygolover.flnettweaks.client.Heartbeat.reset();
        }
        try {
            packet.handle(listener);
        } finally {
            if (join) JoinTracker.handling = false;
            long ns = System.nanoTime() - t;
            if (ns >= FlnConfig.slowPacketNs()) {
                String what = Net.describe(packet);
                if (listener instanceof ServerGamePacketListenerImpl g)
                    FlnTweaks.LOG.warn("flnettweaks: пакет {} от {} обрабатывался {} мс в потоке сервера", what, g.player.getGameProfile().getName(), ns / 1_000_000);
                else {
                    FlnTweaks.LOG.warn("flnettweaks: пакет {} обрабатывался {} мс в основном потоке", what, ns / 1_000_000);
                    JoinTracker.slow(what, ns);
                }
            }
        }
    }
}

package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.Net;
import faygolover.flnettweaks.NetStats;
import faygolover.flnettweaks.client.JoinTracker;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Приём: понятная причина, если пакет не разобрался (какой мод, размер, какой лимит поднять), и счётчики. */
@Mixin(PacketDecoder.class)
public abstract class PacketDecoderMixin {
    @Shadow @Final private PacketFlow flow;
    @Unique private int fln$size;
    @Unique private ConnectionProtocol fln$proto;

    @Inject(method = "decode", at = @At("HEAD"), remap = false)
    private void fln$head(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci) {
        fln$size = in.readableBytes();
        fln$proto = ctx.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get();
    }

    @Redirect(method = "decode", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/network/ConnectionProtocol;createPacket(Lnet/minecraft/network/protocol/PacketFlow;ILnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/Packet;"))
    private Packet<?> fln$create(ConnectionProtocol proto, PacketFlow flow, int id, FriendlyByteBuf buf) {
        int start = buf.readerIndex();
        try {
            return proto.createPacket(flow, id, buf);
        } catch (RuntimeException e) {
            throw Net.decodeFailure(e, proto, flow, id, buf, start, fln$size);
        }
    }

    @Inject(method = "decode", at = @At("RETURN"), remap = false)
    private void fln$tail(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci) {
        if (out.isEmpty() || !(out.get(out.size() - 1) instanceof Packet<?> p)) return;
        NetStats.in(ctx.channel(), p, fln$size);
        if (flow == PacketFlow.CLIENTBOUND) JoinTracker.packet(ctx.channel(), fln$proto, p, fln$size);
    }
}

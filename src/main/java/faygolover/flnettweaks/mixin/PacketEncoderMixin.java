package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.NetStats;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Отправка: предел пакета (ваниль 8 МБ) и счётчики. */
@Mixin(PacketEncoder.class)
public abstract class PacketEncoderMixin {
    @Unique private int fln$start;

    private static final String ENCODE = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V";

    @ModifyConstant(method = ENCODE, constant = @Constant(intValue = 8388608), remap = false)
    private int fln$max(int vanilla) {
        return FlnConfig.packet();
    }

    @Inject(method = ENCODE, at = @At("HEAD"), remap = false)
    private void fln$head(ChannelHandlerContext ctx, Packet<?> p, ByteBuf out, CallbackInfo ci) {
        fln$start = out.writerIndex();
        NetStats.of(ctx.channel()).lastOut = faygolover.flnettweaks.Net.key(p);
    }

    @Inject(method = ENCODE, at = @At("RETURN"), remap = false)
    private void fln$tail(ChannelHandlerContext ctx, Packet<?> p, ByteBuf out, CallbackInfo ci) {
        NetStats.out(ctx.channel(), p, out.writerIndex() - fln$start);
    }
}

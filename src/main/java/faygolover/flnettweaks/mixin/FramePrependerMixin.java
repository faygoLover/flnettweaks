package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.Net;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Varint21LengthFieldPrepender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Отправка кадров больше 2 МБ (до frameKB). */
@Mixin(Varint21LengthFieldPrepender.class)
public abstract class FramePrependerMixin {
    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void fln$encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out, CallbackInfo ci) {
        ci.cancel();
        int len = msg.readableBytes();
        if (len > FlnConfig.frame()) {
            throw new Net.Explained("flnettweaks: отправляемый кадр " + Net.size(len) + " для " + Net.who(ctx.channel())
                    + " больше лимита " + Net.size(FlnConfig.frame()) + ". Поднимите limits.frameKB в " + Net.CONFIG, null);
        }
        int head = FriendlyByteBuf.getVarIntSize(len);
        FriendlyByteBuf buf = new FriendlyByteBuf(out);
        buf.ensureWritable(head + len);
        buf.writeVarInt(len);
        buf.writeBytes(msg, msg.readerIndex(), len);
    }
}

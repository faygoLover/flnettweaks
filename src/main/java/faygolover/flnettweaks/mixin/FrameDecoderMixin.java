package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.Net;
import faygolover.flnettweaks.NetStats;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import net.minecraft.network.Varint21FrameDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Длина кадра до 5 байт (ваниль — 3 байта, т. е. до 2 МБ) с лимитом frameKB и прогрессом приёма больших кадров. */
@Mixin(Varint21FrameDecoder.class)
public abstract class FrameDecoderMixin {
    @Inject(method = "decode", at = @At("HEAD"), cancellable = true, remap = false)
    private void fln$decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci) {
        ci.cancel();
        in.markReaderIndex();
        int len = 0;
        for (int i = 0; i < 5; i++) {
            if (!in.isReadable()) {
                in.resetReaderIndex();
                return;
            }
            byte b = in.readByte();
            len |= (b & 0x7F) << (i * 7);
            if (b >= 0) {
                if (len < 0 || len > FlnConfig.frame()) {
                    throw new Net.Explained("flnettweaks: кадр " + Net.size(len & 0xFFFFFFFFL) + " от " + Net.who(ctx.channel())
                            + " больше лимита " + Net.size(FlnConfig.frame()) + ". Поднимите limits.frameKB в " + Net.CONFIG + " на сервере и у игроков", null);
                }
                NetStats.Conn st = NetStats.of(ctx.channel());
                if (in.readableBytes() >= len) {
                    st.pendingFrame = 0;
                    out.add(in.readBytes(len));
                } else {
                    if (len >= 65536) { st.pendingFrame = len; st.pendingHave = in.readableBytes(); }
                    in.resetReaderIndex();
                }
                return;
            }
        }
        throw new CorruptedFrameException("length wider than 35-bit");
    }
}

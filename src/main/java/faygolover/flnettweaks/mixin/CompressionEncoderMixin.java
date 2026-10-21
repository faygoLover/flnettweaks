package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import net.minecraft.network.CompressionEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Forge пишет в лог полный дамп пакета больше 8 МБ — порог по нашему лимиту. */
@Mixin(CompressionEncoder.class)
public abstract class CompressionEncoderMixin {
    @ModifyConstant(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V",
            constant = @Constant(intValue = 8388608), remap = false)
    private int fln$max(int vanilla) {
        return FlnConfig.packet();
    }
}

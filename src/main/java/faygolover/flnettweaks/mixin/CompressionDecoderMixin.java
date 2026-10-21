package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import net.minecraft.network.CompressionDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Предел размера пакета после распаковки (ваниль 8 МБ). */
@Mixin(CompressionDecoder.class)
public abstract class CompressionDecoderMixin {
    @ModifyConstant(method = "decode", constant = @Constant(intValue = 8388608), remap = false)
    private int fln$max(int vanilla) {
        return FlnConfig.packet();
    }
}

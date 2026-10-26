package faygolover.flnettweaks.mixin;

import net.minecraft.network.CompressionEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.zip.Deflater;

/** Сильное сжатие у игрока: уровень Deflater этого соединения. */
@Mixin(CompressionEncoder.class)
public interface CompressionEncoderAccess {
    @Accessor("deflater") Deflater fln$deflater();
}

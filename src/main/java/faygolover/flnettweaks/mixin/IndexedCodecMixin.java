package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.MsgTypes;
import net.minecraftforge.network.NetworkInstance;
import net.minecraftforge.network.simple.IndexedMessageCodec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** SimpleChannel: запоминаем, какой класс сообщения стоит за номером (первый байт данных) в каждом канале. */
@Mixin(value = IndexedMessageCodec.class, remap = false)
public abstract class IndexedCodecMixin {
    @Shadow @Final private NetworkInstance networkInstance;

    @Inject(method = "addCodecIndex", at = @At("RETURN"))
    private void fln$remember(int index, Class<?> type, java.util.function.BiConsumer<?, ?> enc, java.util.function.Function<?, ?> dec,
                              java.util.function.BiConsumer<?, ?> consumer, Optional<?> dir, CallbackInfoReturnable<?> cir) {
        if (networkInstance != null) MsgTypes.put(networkInstance.getChannelName(), index, type);
    }
}

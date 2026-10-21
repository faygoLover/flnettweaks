package faygolover.flnettweaks.mixin.client;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Обрезанные данные чанка от сервера: недостающие секции остаются пустыми вместо вылета. */
@Mixin(LevelChunk.class)
public abstract class ChunkSectionMixin {
    @Redirect(method = "replaceWithPacketData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;read(Lnet/minecraft/network/FriendlyByteBuf;)V"))
    private void fln$read(LevelChunkSection section, FriendlyByteBuf buf) {
        if (!FlnConfig.skipBrokenSections()) { section.read(buf); return; }
        if (!buf.isReadable()) return;
        try {
            section.read(buf);
        } catch (RuntimeException e) {
            FlnTweaks.LOG.warn("flnettweaks: битая секция чанка {} от сервера ({}), остальные секции чанка пропущены",
                    ((LevelChunk) (Object) this).getPos(), e.toString());
            buf.readerIndex(buf.writerIndex());
        }
    }
}

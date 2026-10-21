package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.JoinLog;
import faygolover.flnettweaks.client.JoinTracker;
import net.minecraft.network.Connection;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.HandshakeMessages;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/** Forge-рукопожатие: на сервере — что отправлено и чего ждём; у игрока — прогресс для экрана входа. */
@Mixin(value = HandshakeHandler.class, remap = false)
public abstract class HandshakeHandlerMixin {
    @Shadow private List<NetworkRegistry.LoginPayload> messageList;
    @Shadow private List<Integer> sentMessages;
    @Shadow private int packetPosition;
    @Shadow @Final private Connection manager;
    @Shadow @Final private List<Future<Void>> pendingFutures;

    @Unique
    private void fln$server() {
        JoinLog log = JoinLog.of(manager);
        if (log == null) return;
        Object[] pending;
        try { pending = sentMessages.toArray(); } catch (RuntimeException e) { pending = new Object[0]; } // ответы приходят из потока netty
        log.forge(messageList, pending, packetPosition, pendingFutures.size());
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void fln$tickServer(CallbackInfoReturnable<Boolean> cir) {
        fln$server();
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void fln$tickServerSent(CallbackInfoReturnable<Boolean> cir) {
        fln$server();
    }

    @Inject(method = "handleServerModListOnClient", at = @At("HEAD"))
    private void fln$modList(HandshakeMessages.S2CModList msg, Supplier<NetworkEvent.Context> c, CallbackInfo ci) {
        JoinTracker.modList(msg.getModList().size(), msg.getRegistries().size());
    }

    @Inject(method = "handleRegistryMessage", at = @At("HEAD"))
    private void fln$registry(HandshakeMessages.S2CRegistry msg, Supplier<NetworkEvent.Context> c, CallbackInfo ci) {
        JoinTracker.registry(String.valueOf(msg.getRegistryName()));
    }

    @Inject(method = "handleRegistryLoading", at = @At("HEAD"))
    private void fln$applyStart(Supplier<NetworkEvent.Context> c, CallbackInfoReturnable<Boolean> cir) {
        JoinTracker.applyRegistries(true);
    }

    @Inject(method = "handleRegistryLoading", at = @At("RETURN"))
    private void fln$applyEnd(Supplier<NetworkEvent.Context> c, CallbackInfoReturnable<Boolean> cir) {
        JoinTracker.applyRegistries(false);
    }

    @Inject(method = "handleConfigSync", at = @At("HEAD"))
    private void fln$config(HandshakeMessages.S2CConfigData msg, Supplier<NetworkEvent.Context> c, CallbackInfo ci) {
        JoinTracker.config(msg.getFileName());
    }
}

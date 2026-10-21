package faygolover.flnettweaks.mixin.client;

import faygolover.flnettweaks.client.ClientNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Куда подключаемся — чтобы после обрыва переподключиться туда же. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void fln$target(Screen parent, Minecraft mc, ServerAddress addr, ServerData data, boolean quickPlay, CallbackInfo ci) {
        ClientNet.target(addr, data);
    }
}

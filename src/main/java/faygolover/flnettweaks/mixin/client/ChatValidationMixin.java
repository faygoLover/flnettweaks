package faygolover.flnettweaks.mixin.client;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.SignedMessageValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Ошибка проверки подписи чата не выкидывает с сервера: сообщение показывается, в логе — запись. */
@Mixin(ClientPacketListener.class)
public abstract class ChatValidationMixin {
    @Unique private static long fln$lastLog;

    @Unique
    private static void fln$log(String what) {
        long now = System.currentTimeMillis();
        if (now - fln$lastLog < 10000) return;
        fln$lastLog = now;
        FlnTweaks.LOG.warn("flnettweaks: {} — отключение с сервера пропущено (tweaks.ignoreChatValidationErrors)", what);
    }

    @Redirect(method = "handlePlayerChat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/SignedMessageValidator;updateAndValidate(Lnet/minecraft/network/chat/PlayerChatMessage;)Z"))
    private boolean fln$validate(SignedMessageValidator v, PlayerChatMessage msg) {
        if (v.updateAndValidate(msg)) return true;
        if (!FlnConfig.ignoreChatValidation()) return false;
        fln$log("подпись сообщения чата не прошла проверку (от " + msg.link().sender() + ")");
        return true;
    }

    @Redirect(method = "handlePlayerChat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/Connection;disconnect(Lnet/minecraft/network/chat/Component;)V"))
    private void fln$disconnect(Connection c, Component reason) {
        if (!FlnConfig.ignoreChatValidation()) { c.disconnect(reason); return; }
        fln$log("сообщение чата от неизвестного отправителя");
    }
}

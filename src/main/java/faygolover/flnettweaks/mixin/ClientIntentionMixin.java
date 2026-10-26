package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.IntentionRaw;
import faygolover.flnettweaks.NetPrefs;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Первый пакет подключения: игрок дописывает к адресу свои настройки соединения ({@code адрес\0FML3\0flnet1:…}),
 * сервер запоминает адрес целиком. Forge берёт из адреса только первые два поля, остальное ему не мешает.
 */
@Mixin(ClientIntentionPacket.class)
public abstract class ClientIntentionMixin implements IntentionRaw {
    @Shadow @Final private ConnectionProtocol intention;
    @Unique private String fln$raw;

    @ModifyArg(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeUtf(Ljava/lang/String;)Lnet/minecraft/network/FriendlyByteBuf;"))
    private String fln$prefs(String host) {
        if (intention != ConnectionProtocol.LOGIN) return host;
        NetPrefs p = NetPrefs.mine();
        if (p.isDefault()) return host;
        String s = host + p.encode() + "\0";
        return s.length() <= 255 ? s : host;
    }

    @Redirect(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readUtf(I)Ljava/lang/String;"))
    private String fln$readRaw(FriendlyByteBuf buf, int max) {
        String s = buf.readUtf(max);
        fln$raw = s;
        return s;
    }

    @Override
    public String fln$raw() {
        return fln$raw;
    }
}

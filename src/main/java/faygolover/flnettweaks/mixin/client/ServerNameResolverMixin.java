package faygolover.flnettweaks.mixin.client;

import com.google.common.net.InetAddresses;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Адрес сервера введён как IP (у нас HOME_EXTERNAL_IP) — имя хоста = этот же IP, без обратного DNS-запроса. Иначе проверка
 * чёрного списка и пинг спрашивают у DNS «чей это IP» и у некоторых провайдеров ждут по нескольку секунд.
 * Как Fast IP Ping, но без перезаписи чужой лямбды.
 */
@Mixin(ServerNameResolver.class)
public abstract class ServerNameResolverMixin {
    @Redirect(method = "resolveAddress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/resolver/ServerAddressResolver;resolve(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Ljava/util/Optional;"))
    private Optional<ResolvedServerAddress> flnet$noReverseDns(ServerAddressResolver resolver, ServerAddress address) {
        Optional<ResolvedServerAddress> resolved = resolver.resolve(address);
        String host = address.getHost();
        if (resolved.isEmpty() || !InetAddresses.isInetAddress(host)) return resolved;
        try {
            InetSocketAddress socket = resolved.get().asInetSocketAddress();
            InetAddress named = InetAddress.getByAddress(host, socket.getAddress().getAddress());
            return Optional.of(ResolvedServerAddress.from(new InetSocketAddress(named, socket.getPort())));
        } catch (Exception e) {
            return resolved;
        }
    }
}

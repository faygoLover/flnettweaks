package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.JoinLog;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/** Вход на сервер: время на вход (ваниль 30 с) и хронология этапов. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginListenerMixin {
    @Shadow @Final Connection connection;
    @Shadow public com.mojang.authlib.GameProfile gameProfile;

    @Unique private static Field fln$stateField;

    /** state — пакетное поле с пакетным enum, из миксина его тип не назвать; читаем по SRG-имени. */
    @Unique
    private String fln$state() {
        try {
            if (fln$stateField == null) fln$stateField = ObfuscationReflectionHelper.findField(ServerLoginPacketListenerImpl.class, "f_10019_");
            return ((Enum<?>) fln$stateField.get(this)).name();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "?";
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fln$init(MinecraftServer server, Connection conn, CallbackInfo ci) {
        JoinLog.start(conn);
    }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 600))
    private int fln$loginTicks(int vanilla) {
        return Math.max(vanilla, FlnConfig.loginTicks());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void fln$tick(CallbackInfo ci) {
        JoinLog log = JoinLog.of(connection);
        if (log == null) return;
        if (gameProfile != null) log.name(gameProfile.getName() + " (" + connection.getRemoteAddress() + ")");
        log.tick(fln$state());
    }

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void fln$kick(Component reason, CallbackInfo ci) {
        JoinLog log = JoinLog.of(connection);
        if (log != null) log.finish("сервер отключил: " + reason.getString(), true);
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void fln$lost(Component reason, CallbackInfo ci) {
        JoinLog log = JoinLog.of(connection);
        if (log != null) log.finish("соединение закрыто: " + reason.getString(), true);
    }
}

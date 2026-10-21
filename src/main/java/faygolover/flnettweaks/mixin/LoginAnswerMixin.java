package faygolover.flnettweaks.mixin;

import faygolover.flnettweaks.FlnConfig;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Ответы игрока при входе (ваниль 1 МБ; проверка внутри лямбды конструктора). */
@Mixin(ServerboundCustomQueryPacket.class)
public abstract class LoginAnswerMixin {
    @ModifyConstant(method = "*", constant = @Constant(intValue = 1048576), require = 1)
    private static int fln$max(int vanilla) {
        return FlnConfig.loginPayload();
    }
}

package faygolover.flnettweaks.mixin.client;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Есть ли уже TCP-соединение (null — сервер ещё не ответил на подключение). */
@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {
    @Accessor("connection")
    Connection fln$connection();
}

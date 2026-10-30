package faygolover.flnettweaks;

import net.minecraft.Util;
import net.minecraftforge.event.TickEvent;

/**
 * Паузы самого сервера (большая вставка WorldEdit, сохранение): пока сервер стоит, ответы игроков на keepalive лежат
 * в очереди, и после паузы ваниль выкинула бы тех, чей keepalive был в пути. Отсчёт keepalive после паузы начинается
 * заново ({@link #since}).
 */
public final class ServerStall {
    private ServerStall() {}

    /** Пауза дольше — считается паузой сервера, мс. */
    private static final long STALL_MS = 2000;

    private static long tickStart, tickEnd;
    /** Когда кончилась последняя пауза сервера (Util.getMillis). */
    private static volatile long lastStallEnd;

    public static void onTick(TickEvent.ServerTickEvent e) {
        long now = Util.getMillis();
        if (e.phase == TickEvent.Phase.START) {
            // между тиками — задачи сервера (команды из чата, WorldEdit)
            if (tickEnd > 0 && now - tickEnd > STALL_MS) lastStallEnd = now;
            tickStart = now;
        } else {
            if (tickStart > 0 && now - tickStart > STALL_MS) lastStallEnd = now;
            tickEnd = now;
        }
    }

    /** С какого момента считать ожидание ответа: с отправки keepalive или с конца паузы сервера, что позже. */
    public static long since(long keepAliveSent) {
        return Math.max(keepAliveSent, lastStallEnd);
    }
}

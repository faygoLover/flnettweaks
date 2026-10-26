package faygolover.flnettweaks.client;

import io.netty.handler.timeout.TimeoutException;
import faygolover.flnettweaks.Net;

import java.io.IOException;

/**
 * Клиент: почему закрылось соединение с сервером. Пишут потоки netty (общие миксины), читает экран отключения —
 * переподключаться ли самим. Без клиентских классов — безопасно загружается и на сервере. Первая причина побеждает.
 */
public final class ConnCause {
    private ConnCause() {}

    public enum Cause {
        /** Обрыв сети: тайм-аут, сброс соединения, сервер закрыл канал без сообщения. Переподключаемся. */
        NETWORK,
        /** Сервер отключил с сообщением (кик, бан, рестарт, несовпадение модов). Не переподключаемся. */
        KICK,
        /** Закрыли сами: выход, отмена, ошибка на нашей стороне. Не переподключаемся. */
        LOCAL
    }

    public static volatile Cause cause;
    public static volatile String detail;
    /** Вход сорвался посреди большого пакета или на долгой тишине — подсказка про ограничение скорости. */
    public static volatile boolean burst;

    public static final String BURST_HINT = "Похоже, канал не выдерживает всплески — попробуйте ограничение скорости в настройках соединения";

    public static void reset() {
        cause = null;
        detail = null;
        burst = false;
    }

    /**
     * Обрыв во время входа: недопринятый кадр от 64 КБ (как у игрока через VPN на «Registry minecraft:item», 707 КБ) или
     * от сервера ничего нет 20 с и дольше. Ничего не включаем — только подсказка на экране отключения.
     */
    public static void noteJoin(io.netty.channel.Channel ch) {
        if (burst || ch == null || !JoinTracker.active || JoinTracker.stage == JoinTracker.Stage.DONE) return;
        faygolover.flnettweaks.NetStats.Conn st = faygolover.flnettweaks.NetStats.of(ch);
        boolean stuck = st.pendingFrame >= 65536 && st.pendingHave < st.pendingFrame;
        boolean quiet = st.lastInAt > 0 && System.currentTimeMillis() - st.lastInAt >= 20_000;
        if (stuck || quiet) burst = true;
    }

    public static synchronized void set(Cause c, String d) {
        if (cause != null) return;
        cause = c;
        detail = d;
    }

    /** Ошибка канала: сеть или наша (лимит, разбор пакета, TLS не договорился). */
    public static void error(Throwable t) {
        if (t instanceof TimeoutException) { set(Cause.NETWORK, "от сервера ничего не приходило"); return; }
        Throwable e = Net.explain(t);
        if (e instanceof Net.Explained) { set(Cause.LOCAL, e.getMessage()); return; }
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof io.netty.handler.ssl.SslHandshakeTimeoutException) { set(Cause.NETWORK, "TLS: сервер не ответил на рукопожатие"); return; }
            if (c instanceof javax.net.ssl.SSLException) break;
            if (c instanceof IOException) { set(Cause.NETWORK, c.getClass().getSimpleName() + ": " + c.getMessage()); return; }
        }
        set(Cause.LOCAL, String.valueOf(t));
    }
}

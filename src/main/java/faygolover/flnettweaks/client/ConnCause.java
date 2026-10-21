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

    public static void reset() {
        cause = null;
        detail = null;
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

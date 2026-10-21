package faygolover.flnettweaks;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Журнал соединений — отдельный файл logs/flnettweaks-connections.log. На сервере: входы и выходы игроков с причиной,
 * время в игре, пинг, паузы связи, трафик. У игрока (см. client.ClientNet) — свои подключения, обрывы и переподключения.
 * Пишет отдельный поток; файл больше 10 МБ уходит в flnettweaks-connections.old.log.
 */
public final class ConnLog {
    private ConnLog() {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_SIZE = 10L << 20;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "flnettweaks-connlog");
        t.setDaemon(true);
        return t;
    });
    private static BufferedWriter out;
    private static long written;
    private static boolean broken;

    // ---------- файл ----------

    public static void write(String line) {
        if (!FlnConfig.connLog()) return;
        String s = LocalDateTime.now().format(TIME) + "  " + line;
        IO.execute(() -> append(s));
    }

    public static void detail(String line) {
        if (FlnConfig.connLogDetails()) write(line);
    }

    private static void append(String s) {
        if (broken) return;
        try {
            Path p = FMLPaths.GAMEDIR.get().resolve("logs").resolve("flnettweaks-connections.log");
            if (out != null && written > MAX_SIZE) { out.close(); out = null; }
            if (out == null) {
                Files.createDirectories(p.getParent());
                if (Files.exists(p) && Files.size(p) > MAX_SIZE)
                    Files.move(p, p.resolveSibling("flnettweaks-connections.old.log"), StandardCopyOption.REPLACE_EXISTING);
                written = Files.exists(p) ? Files.size(p) : 0;
                out = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            out.write(s);
            out.newLine();
            out.flush();
            written += s.length() * 2L + 2;
        } catch (IOException e) {
            broken = true;
            FlnTweaks.LOG.warn("flnettweaks: не удалось писать журнал соединений, он выключен до перезапуска", e);
        }
    }

    // ---------- общие помощники ----------

    public static String dur(long ms) {
        long s = ms / 1000;
        if (s < 60) return String.format(Locale.ROOT, "%.1f с", ms / 1000.0);
        if (s < 3600) return (s / 60) + " мин " + (s % 60) + " с";
        return (s / 3600) + " ч " + (s % 3600 / 60) + " мин";
    }

    public static String ip(SocketAddress a) {
        if (a instanceof InetSocketAddress i) return i.getAddress() != null ? i.getAddress().getHostAddress() + ":" + i.getPort() : i.getHostString() + ":" + i.getPort();
        return String.valueOf(a);
    }

    /** Байты, которые ждут отправки в канале: растут, если игрок перестал принимать. */
    public static long queued(Channel ch) {
        try {
            ChannelOutboundBuffer b = ch.unsafe().outboundBuffer();
            return b == null ? 0 : b.totalPendingWriteBytes();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Причина отключения по-русски (сервер переводит только на английский). */
    public static String reason(Component c) {
        if (c == null) return "?";
        if (c.getContents() instanceof TranslatableContents t) {
            switch (t.getKey()) {
                case "disconnect.endOfStream": return "другая сторона закрыла соединение (выход из игры или обрыв)";
                case "disconnect.timeout": return "тайм-аут: " + c.getString().replace('\n', ' ');
                case "disconnect.genericReason": return "ошибка сети: " + (t.getArgs().length > 0 ? String.valueOf(t.getArgs()[0] instanceof Component a ? a.getString() : t.getArgs()[0]) : c.getString());
                case "disconnect.quitting": case "multiplayer.status.quitting": return "выход из игры";
                case "multiplayer.disconnect.duplicate_login": return "вход с этим именем с другого подключения (перезаход)";
                case "multiplayer.disconnect.server_shutdown": return "сервер выключается";
                case "multiplayer.disconnect.kicked": return "кик администратором";
                default: break;
            }
        }
        return c.getString().replace('\n', ' ');
    }

    // ---------- сервер: сессии игроков ----------

    /** Сессия игрока на сервере (пишет основной поток сервера). */
    public static final class Session {
        final String name, addr;
        final long start = System.currentTimeMillis();
        final boolean tls;
        long pingSum; int pingN, pingMax;
        int stalls; long stallSum, stallMax, stallStart, stallQueue;
        boolean inStall;
        String kick;
        int forcedAfter;

        Session(String name, String addr, boolean tls) { this.name = name; this.addr = addr; this.tls = tls; }
    }

    public static final AttributeKey<Session> SESSION = AttributeKey.valueOf("flnettweaks:session");
    private static final Map<String, String> RDNS = new ConcurrentHashMap<>();
    private static int ticks;

    public static void serverLogin(ServerPlayer sp) {
        Connection c = sp.connection.connection;
        Channel ch = c.channel();
        if (ch == null || c.isMemoryConnection()) return;
        boolean tls = Boolean.TRUE.equals(ch.attr(Tls.ON).get());
        Session s = new Session(sp.getGameProfile().getName(), ip(c.getRemoteAddress()), tls);
        ch.attr(SESSION).set(s);
        long join = System.currentTimeMillis() - NetStats.of(ch).created;
        write(String.format(Locale.ROOT, "ВХОД    %s  %s  вход за %s%s", s.name, s.addr, dur(join), tls ? "  TLS" : ""));
        if (FlnConfig.connLogDetails() && c.getRemoteAddress() instanceof InetSocketAddress isa && isa.getAddress() != null) {
            InetAddress a = isa.getAddress();
            String key = a.getHostAddress();
            String known = RDNS.get(key);
            if (known != null) detail("АДРЕС   " + s.name + "  " + key + " → " + known);
            else IO.execute(() -> {   // обратный DNS бывает медленным — не в основном потоке
                String host;
                try { host = a.getCanonicalHostName(); } catch (RuntimeException e) { host = key; }
                if (host.equals(key)) host = "имени нет";
                RDNS.put(key, host);
                detail("АДРЕС   " + s.name + "  " + key + " → " + host);
            });
        }
    }

    /** Каждый тик сервера: паузы приёма от игроков и пинг. */
    public static void serverTick(MinecraftServer server) {
        if (!FlnConfig.connLog()) return;
        boolean sample = ++ticks % 20 == 0;
        long now = System.currentTimeMillis(), limit = FlnConfig.logStallMs();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.connection == null) continue;
            Channel ch = sp.connection.connection.channel();
            Session s = ch == null ? null : ch.attr(SESSION).get();
            if (s == null) continue;
            if (sample && sp.latency > 0) { s.pingSum += sp.latency; s.pingN++; s.pingMax = Math.max(s.pingMax, sp.latency); }
            long last = NetStats.of(ch).lastInAt;
            if (last == 0) continue;
            if (!s.inStall && now - last >= limit) {
                s.inStall = true;
                s.stallStart = last;
                s.stallQueue = 0;
            }
            if (s.inStall) {
                s.stallQueue = Math.max(s.stallQueue, queued(ch));
                if (last > s.stallStart) endStall(s, last - s.stallStart, false);
            }
        }
    }

    private static void endStall(Session s, long ms, boolean cut) {
        s.inStall = false;
        s.stalls++;
        s.stallSum += ms;
        s.stallMax = Math.max(s.stallMax, ms);
        detail(String.format(Locale.ROOT, "ПАУЗА   %s  %s ничего не приходило от игрока%s%s", s.name, dur(ms),
                s.stallQueue >= 1024 ? ", очередь отправки до " + Net.size(s.stallQueue) : "", cut ? " — так и оборвалось" : ""));
    }

    /** Сервер решил отключить игрока (первый вызов ServerGamePacketListenerImpl.disconnect). */
    public static void kicked(Connection c, String reason) {
        Session s = c.channel() == null ? null : c.channel().attr(SESSION).get();
        if (s != null && s.kick == null) s.kick = reason;
    }

    /** Сообщение об отключении не дошло — соединение закрыто принудительно. */
    public static void forced(Channel ch, int seconds) {
        Session s = ch.attr(SESSION).get();
        if (s != null) s.forcedAfter = seconds;
    }

    public static void serverLogout(Connection c, Component reason) {
        Channel ch = c.channel();
        Session s = ch == null ? null : ch.attr(SESSION).get();
        if (s == null) return;
        ch.attr(SESSION).set(null);
        long now = System.currentTimeMillis();
        if (s.inStall) endStall(s, now - s.stallStart, true);
        NetStats.Conn st = NetStats.of(ch);
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "ВЫХОД   %s  %s  ", s.name, s.addr));
        sb.append(s.kick != null ? "сервер отключил: " + s.kick : reason(reason));
        if (s.forcedAfter > 0) sb.append(" (сообщение об этом не дошло — соединение закрыто через ").append(s.forcedAfter).append(" с)");
        sb.append(" · в игре ").append(dur(now - s.start));
        if (s.pingN > 0) sb.append(String.format(Locale.ROOT, " · пинг ср %d мс, макс %d", s.pingSum / s.pingN, s.pingMax));
        if (s.stalls > 0) sb.append(String.format(Locale.ROOT, " · паузы связи: %d, самая длинная %s, всего %s", s.stalls, dur(s.stallMax), dur(s.stallSum)));
        else sb.append(" · пауз связи не было");
        sb.append(" · трафик: игроку ").append(Net.size(st.outBytes)).append(", от игрока ").append(Net.size(st.inBytes));
        if (s.tls) sb.append(" · TLS");
        write(sb.toString());
    }

    /** Вход не удался (сервер): из хронологии JoinLog. */
    public static void loginFailed(String who, String result, long ms, String where) {
        write(String.format(Locale.ROOT, "НЕ ВОШЁЛ %s  %s за %s%s", who, result, dur(ms), where == null ? "" : " — " + where));
    }
}

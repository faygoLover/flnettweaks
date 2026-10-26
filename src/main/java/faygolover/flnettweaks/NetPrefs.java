package faygolover.flnettweaks;

import io.netty.channel.Channel;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;

import java.util.Locale;

/**
 * Настройки соединения игрока («Расширенные настройки соединения»): у каждого свои, хранятся у игрока (раздел [player]
 * конфига) и уходят серверу в первом же пакете подключения — хвостом к адресу в ClientIntentionPacket
 * ({@code адрес\0FML3\0flnet1:r=1000,p=1,c=1}; Forge читает только первые два поля). Сервер применяет их только к этому
 * соединению, ещё до входа: ограничение скорости отдачи (Netty ChannelTrafficShapingHandler), терпение (тишина,
 * keepalive и время на вход) и сжатие.
 */
public final class NetPrefs {
    /** Ограничение отдачи игроку, кбит/с: 0 — авто (без ограничения). */
    public final int rateKbit;
    /** Долгое терпение: 5 мин тишины вместо 2. */
    public final boolean longPatience;
    /** Сильное сжатие: Deflater 9 вместо 6 (порог сжатия — серверный: его меняет и Fabric API под Sinytra Connector). */
    public final boolean strongCompression;

    public NetPrefs(int rateKbit, boolean longPatience, boolean strongCompression) {
        this.rateKbit = Math.max(0, rateKbit);
        this.longPatience = longPatience;
        this.strongCompression = strongCompression;
    }

    public static final NetPrefs DEFAULT = new NetPrefs(0, false, false);
    public static final AttributeKey<NetPrefs> KEY = AttributeKey.valueOf("flnettweaks:prefs");
    public static final int[] RATES = {0, 4000, 2000, 1000, 500};
    private static final String TAG = "flnet1:";

    public static final int NORMAL_S = 120, LONG_S = 300;

    public boolean isDefault() {
        return rateKbit == 0 && !longPatience && !strongCompression;
    }

    // ---------- передача ----------

    public String encode() {
        return TAG + "r=" + rateKbit + ",p=" + (longPatience ? 1 : 0) + ",c=" + (strongCompression ? 1 : 0);
    }

    /** Из сырой строки адреса ClientIntentionPacket; нет нашего поля — null. */
    public static NetPrefs decode(String rawHost) {
        if (rawHost == null) return null;
        for (String part : rawHost.split("\0")) {
            if (!part.startsWith(TAG)) continue;
            int r = 0; boolean p = false, c = false;
            for (String kv : part.substring(TAG.length()).split(",")) {
                int i = kv.indexOf('=');
                if (i <= 0) continue;
                String k = kv.substring(0, i), v = kv.substring(i + 1);
                try {
                    switch (k) {
                        case "r" -> r = Math.max(0, Math.min(1_000_000, Integer.parseInt(v)));
                        case "p" -> p = v.equals("1");
                        case "c" -> c = v.equals("1");
                        default -> {}
                    }
                } catch (NumberFormatException ignored) {}
            }
            return new NetPrefs(r, p, c);
        }
        return null;
    }

    // ---------- текст ----------

    public static String rateName(int kbit) {
        if (kbit <= 0) return "авто";
        return (kbit % 1000 == 0 ? String.valueOf(kbit / 1000) : String.format(Locale.ROOT, "%.1f", kbit / 1000.0)) + " Мбит/с";
    }

    public static String patienceName(boolean longP) {
        return longP ? "долгое (5 мин)" : "обычное (2 мин)";
    }

    public static String compressionName(boolean strong) {
        return strong ? "сильное" : "обычное";
    }

    @Override
    public String toString() {
        return "скорость " + rateName(rateKbit) + ", терпение " + patienceName(longPatience) + ", сжатие " + compressionName(strongCompression);
    }

    // ---------- сервер ----------

    public static NetPrefs of(Channel ch) {
        NetPrefs p = ch == null ? null : ch.attr(KEY).get();
        return p == null ? DEFAULT : p;
    }

    public static NetPrefs of(Connection c) {
        return c == null ? DEFAULT : of(c.channel());
    }

    /** Сервер, рукопожатие: настройки игрока — на это соединение. */
    public static void applyServer(Channel ch, NetPrefs p) {
        if (ch == null || p == null) return;
        ch.attr(KEY).set(p);
        if (p.rateKbit > 0 && ch.pipeline().get("fln_shape") == null) {
            long bytes = p.rateKbit * 1000L / 8;
            // проверка каждые 100 мс: всплески не больше 1/10 секундной нормы
            ch.pipeline().addFirst("fln_shape", new ChannelTrafficShapingHandler(bytes, 0, 100));
        }
        if (p.longPatience && ch.pipeline().get("timeout") != null)
            ch.pipeline().replace("timeout", "timeout", new ReadTimeoutHandler(Math.max(FlnConfig.readTimeout(), LONG_S)));
    }

    /** Время на вход этому игроку, тиков. */
    public static int loginTicks(Connection c) {
        int t = FlnConfig.loginTicks();
        return of(c).longPatience ? Math.max(t, 2 * LONG_S * 20) : t;
    }

    /** Предел ответа на keepalive этому игроку, мс. */
    public static long keepAliveMs(Connection c) {
        long t = FlnConfig.keepAliveMs();
        return of(c).longPatience ? Math.max(t, LONG_S * 1000L) : t;
    }

    // ---------- клиент ----------

    /** Свои настройки (раздел [player] конфига). */
    public static NetPrefs mine() {
        return new NetPrefs(FlnConfig.playerRate(), FlnConfig.playerLongPatience(), FlnConfig.playerStrongCompression());
    }

    /** Тишина от сервера, после которой клиент рвёт связь, с. */
    public static int clientReadTimeout() {
        int t = FlnConfig.readTimeout();
        return FlnConfig.playerLongPatience() ? Math.max(t, LONG_S) : t;
    }
}

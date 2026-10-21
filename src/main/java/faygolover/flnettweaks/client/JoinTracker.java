package faygolover.flnettweaks.client;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import faygolover.flnettweaks.Net;
import io.netty.channel.Channel;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;

import java.util.Locale;

/**
 * Клиент: что сейчас происходит при входе на сервер. Пишут потоки netty (пакеты) и Forge-рукопожатие, читает экран.
 * Без клиентских классов — безопасно загружается и на сервере.
 */
public final class JoinTracker {
    private JoinTracker() {}

    public enum Stage {
        CONNECT("подключение к серверу"), LOGIN("вход"), AUTH("проверка аккаунта"), FORGE("Forge: моды, реестры, конфиги"),
        APPLY("применение реестров сервера"), PLAYER("сервер готовит игрока"), WORLD("получение мира"), DONE("готово");
        public final String title;
        Stage(String t) { title = t; }
    }

    public static volatile boolean active;
    /** Основной поток клиента обрабатывает пакет во время входа (для плавного входа, client.Heartbeat). */
    public static volatile boolean handling;
    public static volatile String handlingWhat;
    public static volatile long handlingSince;
    public static volatile Stage stage = Stage.DONE;
    public static volatile long start, stageStart;
    public static volatile Channel channel;
    public static volatile String lastWhat = "—";
    public static volatile int lastSize;
    public static volatile long lastAt;
    // Forge
    public static volatile int mods, regTotal, regDone, forgeMessages;
    public static volatile String forgeStep;
    // мир
    public static volatile int chunks;
    public static volatile long chunkBytes;
    private static final StringBuilder timeline = new StringBuilder();
    private static String slowest;
    private static long slowestNs;

    private static String sec(long ms) {
        return String.format(Locale.ROOT, "%.1f с", ms / 1000.0);
    }

    public static synchronized void begin() {
        active = true;
        start = stageStart = System.currentTimeMillis();
        stage = Stage.CONNECT;
        channel = null;
        lastWhat = "—"; lastSize = 0; lastAt = 0;
        mods = regTotal = regDone = forgeMessages = 0; forgeStep = null;
        chunks = 0; chunkBytes = 0;
        timeline.setLength(0);
        slowest = null; slowestNs = 0;
    }

    private static synchronized void to(Stage s) {
        if (!active || s.ordinal() <= stage.ordinal() && s != Stage.FORGE) return;
        if (s == stage) return;
        long now = System.currentTimeMillis();
        timeline.append(String.format(Locale.ROOT, "%n    %-40s %s", stage.title, sec(now - stageStart)));
        stage = s;
        stageStart = now;
    }

    /** Пакет от сервера (поток netty). */
    public static void packet(Channel ch, ConnectionProtocol proto, Packet<?> p, int size) {
        if (!active) return;
        if (channel == null) channel = ch;
        lastWhat = Net.describe(p);
        lastSize = size;
        lastAt = System.currentTimeMillis();
        if (stage == Stage.CONNECT) to(Stage.LOGIN);
        if (p instanceof ClientboundHelloPacket) to(Stage.AUTH);
        else if (p instanceof ClientboundCustomQueryPacket) { forgeMessages++; if (stage.ordinal() < Stage.FORGE.ordinal()) to(Stage.FORGE); }
        else if (p instanceof ClientboundGameProfilePacket) to(Stage.PLAYER);
        else if (p instanceof ClientboundLoginPacket) to(Stage.WORLD);
        else if (p instanceof ClientboundLevelChunkWithLightPacket) { chunks++; chunkBytes += size; }
    }

    // ---------- Forge-рукопожатие ----------

    public static void modList(int modCount, int registries) {
        mods = modCount;
        regTotal = registries;
        forgeStep = "список модов сервера: " + modCount;
    }

    public static void registry(String name) {
        regDone++;
        forgeStep = "реестр " + name;
    }

    public static void applyRegistries(boolean begin) {
        if (begin) to(Stage.APPLY);
        else { forgeStep = "реестры применены"; to(Stage.FORGE); }
    }

    public static void config(String file) {
        forgeStep = "конфиг " + file;
    }

    /** Пакет долго обрабатывался в основном потоке клиента. */
    public static synchronized void slow(String what, long ns) {
        if (!active) return;
        if (ns > slowestNs) { slowestNs = ns; slowest = what; }
    }

    public static synchronized void finish(String result) {
        if (!active) return;
        active = false;
        long now = System.currentTimeMillis();
        timeline.append(String.format(Locale.ROOT, "%n    %-40s %s", stage.title, sec(now - stageStart)));
        String lastStage = stage.title;
        long total = now - start;
        StringBuilder sb = new StringBuilder("flnettweaks: вход на сервер — ").append(result).append(" за ").append(sec(total)).append(timeline);
        if (forgeMessages > 0) sb.append(String.format(Locale.ROOT, "%n    Forge: сообщений %d, модов на сервере %d, реестров %d/%d", forgeMessages, mods, regDone, regTotal));
        sb.append(String.format(Locale.ROOT, "%n    чанков до входа в мир: %d (%s)", chunks, Net.size(chunkBytes)));
        if (slowest != null) sb.append(String.format(Locale.ROOT, "%n    дольше всего обрабатывался: %s — %d мс", slowest, slowestNs / 1_000_000));
        stage = Stage.DONE;
        if (!result.equals("в мире") && !result.equals("отменено"))
            faygolover.flnettweaks.ConnLog.write("НЕ ВОШЁЛ " + ClientNet.serverName() + "  " + result + " за " + sec(total) + " на этапе «" + lastStage + "»"
                    + (ConnCause.detail != null ? ": " + ConnCause.detail : ""));
        if (total >= FlnConfig.slowStepMs() * 3 || !result.equals("в мире")) FlnTweaks.LOG.warn(sb.toString());
        else FlnTweaks.LOG.info(sb.toString());
    }
}

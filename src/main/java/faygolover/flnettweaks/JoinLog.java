package faygolover.flnettweaks;

import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import net.minecraftforge.network.NetworkRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Сервер: хронология входа игрока по этапам. Пишется в лог одной записью, когда игрок вошёл или отвалился;
 * если этап затянулся — сразу предупреждение, чего именно ждём.
 */
public final class JoinLog {
    public static final AttributeKey<JoinLog> KEY = AttributeKey.valueOf("flnettweaks:join");

    private final Connection conn;
    private final long start = System.currentTimeMillis();
    private final StringBuilder lines = new StringBuilder();
    private String who;
    private String stage;
    private long stageStart = start;
    private boolean warned, finished;

    // Forge: что отправлено и на что ждём ответа
    private int forgeSent, forgeTotal;
    private long forgeBytes;
    private final Map<Integer, Long> sentAt = new HashMap<>();
    private final Map<Integer, String> context = new HashMap<>();
    private String slowestReply;
    private long slowestReplyMs;
    private int pendingFutures;
    private String waiting;

    private JoinLog(Connection conn) {
        this.conn = conn;
        this.who = String.valueOf(conn.getRemoteAddress());
    }

    public static void start(Connection c) {
        if (c.isMemoryConnection()) return;
        c.channel().attr(KEY).set(new JoinLog(c));
    }

    public static JoinLog of(Connection c) {
        return c == null || c.channel() == null ? null : c.channel().attr(KEY).get();
    }

    private static String sec(long ms) {
        return String.format(Locale.ROOT, "%.1f с", ms / 1000.0);
    }

    public synchronized void name(String name) {
        if (name != null && !name.isEmpty()) who = name;
    }

    /** Каждый тик входа: этап из ServerLoginPacketListenerImpl.state. */
    public synchronized void tick(String state) {
        if (finished) return;
        long now = System.currentTimeMillis();
        if (!state.equals(stage)) {
            if (stage != null) lines.append(String.format(Locale.ROOT, "%n    %-45s %s", stageName(stage), sec(now - stageStart)));
            stage = state;
            stageStart = now;
            warned = false;
        }
        long in = now - stageStart;
        if (!warned && in >= FlnConfig.slowStepMs()) {
            warned = true;
            FlnTweaks.LOG.warn("flnettweaks: вход {} — этап «{}» идёт уже {}: {}", who, stageName(stage), sec(in), waitingFor());
        }
    }

    /** Состояние Forge-рукопожатия (HandshakeHandler.tickServer). */
    public synchronized void forge(List<NetworkRegistry.LoginPayload> messages, Object[] pending, int position, int futures) {
        long now = System.currentTimeMillis();
        forgeTotal = messages.size();
        for (int i = forgeSent; i < Math.min(position, messages.size()); i++) {
            NetworkRegistry.LoginPayload m = messages.get(i);
            forgeBytes += m.getData().writerIndex();
            sentAt.put(i, now);
            context.put(i, m.getMessageContext() + " (" + m.getChannelName() + ", " + Net.size(m.getData().writerIndex()) + ")");
        }
        forgeSent = Math.max(forgeSent, position);
        pendingFutures = futures;
        // ответы пришли — запоминаем самый долгий
        java.util.Set<Integer> still = new java.util.HashSet<>();
        for (Object o : pending) if (o instanceof Integer i) still.add(i);
        sentAt.entrySet().removeIf(e -> {
            if (still.contains(e.getKey())) return false;
            long ms = now - e.getValue();
            if (ms > slowestReplyMs) { slowestReplyMs = ms; slowestReply = context.get(e.getKey()); }
            return true;
        });
        if (futures > 0) waiting = "ждём моды на сервере (PlayerNegotiationEvent: задач " + futures + ")";
        else if (!still.isEmpty()) {
            int first = still.stream().min(Integer::compare).get();
            Long at = sentAt.get(first);
            waiting = "ждём ответ игрока на «" + context.getOrDefault(first, "#" + first) + "»"
                    + (at == null ? "" : " — " + sec(now - at)) + (still.size() > 1 ? ", ещё ответов в ожидании: " + (still.size() - 1) : "");
        } else if (position < messages.size()) waiting = "отправляем сообщения входа " + position + "/" + messages.size();
        else waiting = "рукопожатие завершается";
    }

    private String waitingFor() {
        if (stage == null) return "ждём начала входа";
        return switch (stage) {
            case "HELLO" -> "ждём от игрока приветствие (имя)";
            case "KEY" -> "ждём ответ шифрования от игрока";
            case "AUTHENTICATING" -> "проверка аккаунта на сервере авторизации (Mojang/Ely.by)";
            case "NEGOTIATING" -> waiting == null ? "Forge-рукопожатие" : waiting;
            case "READY_TO_ACCEPT" -> "сервер принимает игрока (загрузка данных игрока)";
            case "DELAY_ACCEPT" -> "ждём, пока с сервера выйдет прежнее подключение этого игрока";
            default -> stage;
        };
    }

    private static String stageName(String s) {
        return switch (s) {
            case "HELLO" -> "приветствие";
            case "KEY" -> "шифрование";
            case "AUTHENTICATING" -> "проверка аккаунта";
            case "NEGOTIATING" -> "Forge: моды, реестры, конфиги";
            case "READY_TO_ACCEPT" -> "приём игрока";
            case "DELAY_ACCEPT" -> "ожидание выхода прежней копии";
            case "ACCEPTED" -> "загрузка игрока в мир";
            default -> s;
        };
    }

    /** Вход закончился: игрок в мире или отключился. Печатаем хронологию. */
    public synchronized void finish(String result, boolean failed) {
        if (finished) return;
        finished = true;
        long now = System.currentTimeMillis();
        if (stage != null) lines.append(String.format(Locale.ROOT, "%n    %-45s %s", stageName(stage), sec(now - stageStart)));
        conn.channel().attr(KEY).set(null);
        long total = now - start;
        if (!FlnConfig.joinTimeline() && !failed && total < FlnConfig.slowStepMs()) return;
        NetStats.Conn st = NetStats.of(conn.channel());
        StringBuilder sb = new StringBuilder();
        sb.append("flnettweaks: вход ").append(who).append(" — ").append(result).append(" за ").append(sec(total));
        sb.append(lines);
        if (forgeTotal > 0) {
            sb.append(String.format(Locale.ROOT, "%n    Forge: сообщений %d/%d, %s", forgeSent, forgeTotal, Net.size(forgeBytes)));
            if (slowestReply != null) sb.append("; дольше всего ответ на «").append(slowestReply).append("» — ").append(sec(slowestReplyMs));
        }
        if (failed && stage != null) sb.append("\n    на чём оборвалось: ").append(waitingFor());
        sb.append(String.format(Locale.ROOT, "%n    трафик: игроку %s (%d пак.), от игрока %s (%d пак.)",
                Net.size(st.outBytes), st.outPackets, Net.size(st.inBytes), st.inPackets));
        if (failed || total >= FlnConfig.slowStepMs()) FlnTweaks.LOG.warn(sb.toString());
        else FlnTweaks.LOG.info(sb.toString());
    }
}

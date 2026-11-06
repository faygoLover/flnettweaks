package faygolover.flnettweaks;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** /flnet stats [in|out] | big | who | limits | reset — сервер, права 2. */
public final class FlnCommands {
    private FlnCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var root = Commands.literal("flnet").requires(s -> s.hasPermission(2));
        if (FlnTweaks.DEBUG) DebugTools.command(root);
        d.register(root
                .then(Commands.literal("stats").executes(c -> stats(c, true))
                        .then(Commands.literal("in").executes(c -> stats(c, true))
                                .then(Commands.argument("key", StringArgumentType.greedyString()).suggests((c, b) -> keys(NetStats.IN_SUB, b))
                                        .executes(c -> detail(c, true))))
                        .then(Commands.literal("out").executes(c -> stats(c, false))
                                .then(Commands.argument("key", StringArgumentType.greedyString()).suggests((c, b) -> keys(NetStats.OUT_SUB, b))
                                        .executes(c -> detail(c, false)))))
                .then(Commands.literal("big").executes(FlnCommands::big))
                .then(Commands.literal("who").executes(FlnCommands::who))
                .then(Commands.literal("limits").executes(FlnCommands::limits))
                .then(Commands.literal("reset").executes(c -> {
                    NetStats.reset();
                    c.getSource().sendSuccess(() -> Component.literal("flnettweaks: статистика сброшена"), true);
                    return 1;
                })));
    }

    private static Consumer<String> out(CommandContext<CommandSourceStack> c) {
        return s -> c.getSource().sendSuccess(() -> Component.literal(s), false);
    }

    /** Общие строки — используются и клиентской командой. */
    public static void statsLines(boolean in, Consumer<String> o) {
        Map<String, NetStats.Counter> m = in ? NetStats.IN : NetStats.OUT;
        long mins = Math.max(1, (System.currentTimeMillis() - NetStats.since) / 60000);
        long total = m.values().stream().mapToLong(x -> x.bytes.sum()).sum();
        o.accept((in ? "Принято" : "Отправлено") + " за " + mins + " мин: " + Net.size(total) + ". Топ по объёму:");
        List<Map.Entry<String, NetStats.Counter>> top = new ArrayList<>(m.entrySet());
        top.sort(Comparator.comparingLong((Map.Entry<String, NetStats.Counter> e) -> e.getValue().bytes.sum()).reversed());
        for (int i = 0; i < Math.min(15, top.size()); i++) {
            var e = top.get(i);
            long b = e.getValue().bytes.sum(), n = e.getValue().n.sum();
            o.accept(String.format(" %2d. %s — %s, %d шт., макс %s, %s/мин", i + 1, label(e.getKey()), Net.size(b), n,
                    Net.size(e.getValue().max.get()), Net.size(b / mins)));
            var subs = (in ? NetStats.IN_SUB : NetStats.OUT_SUB).get(e.getKey());
            if (subs != null && i < 8) {
                List<Map.Entry<String, NetStats.Counter>> st = sorted(subs);
                for (int j = 0; j < Math.min(3, st.size()); j++) {
                    var x = st.get(j);
                    o.accept(String.format("      · %s — %s, %d шт., %s/с", x.getKey(), Net.size(x.getValue().bytes.sum()), x.getValue().n.sum(),
                            perSec(x.getValue().n.sum(), mins)));
                }
                if (st.size() > 3) o.accept("      · …ещё " + (st.size() - 3) + " — /flnet stats " + (in ? "in " : "out ") + e.getKey());
            }
        }
    }

    /** Подробная разбивка одного ключа: типы сообщений канала, атрибуты, типы BlockEntity, звуки. */
    public static void detailLines(boolean in, String key, Consumer<String> o) {
        var subs = (in ? NetStats.IN_SUB : NetStats.OUT_SUB).get(key.trim());
        if (subs == null || subs.isEmpty()) { o.accept("Разбивки для «" + key + "» нет (канал без SimpleChannel или пакетов не было)."); return; }
        long mins = Math.max(1, (System.currentTimeMillis() - NetStats.since) / 60000);
        List<Map.Entry<String, NetStats.Counter>> st = sorted(subs);
        o.accept(label(key.trim()) + " — по типам" + (key.contains("Attributes") ? " (пакет несёт несколько атрибутов: доли размера поделены поровну)" : "") + ":");
        for (int i = 0; i < Math.min(30, st.size()); i++) {
            var x = st.get(i);
            long b = x.getValue().bytes.sum(), n = x.getValue().n.sum();
            o.accept(String.format(" %2d. %s — %s, %d шт., %s/с, макс %s", i + 1, x.getKey(), Net.size(b), n, perSec(n, mins), Net.size(x.getValue().max.get())));
        }
    }

    private static List<Map.Entry<String, NetStats.Counter>> sorted(Map<String, NetStats.Counter> m) {
        List<Map.Entry<String, NetStats.Counter>> l = new ArrayList<>(m.entrySet());
        l.sort(Comparator.comparingLong((Map.Entry<String, NetStats.Counter> e) -> e.getValue().bytes.sum()).reversed());
        return l;
    }

    private static String perSec(long n, long mins) {
        double v = n / (mins * 60.0);
        return v >= 10 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> keys(
            Map<String, Map<String, NetStats.Counter>> m, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(m.keySet(), b);
    }

    private static int detail(CommandContext<CommandSourceStack> c, boolean in) {
        detailLines(in, StringArgumentType.getString(c, "key"), out(c));
        return 1;
    }

    public static void bigLines(Consumer<String> o) {
        List<NetStats.Big> b = NetStats.big();
        if (b.isEmpty()) { o.accept("Больших пакетов (от " + Net.size(FlnConfig.bigPacket()) + ") не было."); return; }
        long now = System.currentTimeMillis();
        o.accept("Большие пакеты (от " + Net.size(FlnConfig.bigPacket()) + "), последние " + b.size() + ":");
        for (int i = b.size() - 1; i >= Math.max(0, b.size() - 20); i--) {
            var x = b.get(i);
            o.accept(String.format(" %s назад: %s %s — %s, %s", ago(now - x.at()), x.in() ? "от" : "для", x.who(), x.what(), Net.size(x.size())));
        }
    }

    public static void limitLines(Consumer<String> o) {
        o.accept("Лимиты flnettweaks (" + Net.CONFIG + "):");
        o.accept(" пакет мода игрок→сервер " + Net.size(FlnConfig.serverboundPayload()) + " (ваниль 32 КБ)");
        o.accept(" пакет мода сервер→игрок " + Net.size(FlnConfig.clientboundPayload()) + " (ваниль 1 МБ)");
        o.accept(" пакеты входа " + Net.size(FlnConfig.loginPayload()) + " (ваниль 1 МБ)");
        o.accept(" пакет целиком " + Net.size(FlnConfig.packet()) + " (ваниль 8 МБ), кадр " + Net.size(FlnConfig.frame()) + " (ваниль 2 МБ)");
        o.accept(" NBT " + Net.size(FlnConfig.nbt()) + " (ваниль 2 МБ)");
        o.accept(" вход " + FlnConfig.loginTicks() / 20 + " с; без своих настроек: тишина " + FlnConfig.readTimeout() + " с, keepalive " + FlnConfig.keepAliveMs() / 1000
                + " с (ваниль 30/15); игроки с настройками — их «Сервер ждёт меня»; паузы сервера не считаются");
    }

    private static String label(String key) {
        int i = key.indexOf(':');
        return i > 0 ? key + " [" + Net.mod(key.substring(0, i)) + "]" : key;
    }

    static String ago(long ms) {
        long s = ms / 1000;
        return s < 60 ? s + " с" : s < 3600 ? s / 60 + " мин" : s / 3600 + " ч";
    }

    private static int stats(CommandContext<CommandSourceStack> c, boolean in) {
        statsLines(in, out(c));
        return 1;
    }

    private static int big(CommandContext<CommandSourceStack> c) {
        bigLines(out(c));
        return 1;
    }

    private static int limits(CommandContext<CommandSourceStack> c) {
        limitLines(out(c));
        return 1;
    }

    private static int who(CommandContext<CommandSourceStack> c) {
        var o = out(c);
        long now = System.currentTimeMillis();
        List<ServerPlayer> ps = c.getSource().getServer().getPlayerList().getPlayers();
        o.accept("Игроки: " + ps.size());
        for (ServerPlayer p : ps) {
            NetStats.Conn s = NetStats.of(p.connection.connection.channel());
            long mins = Math.max(1, (now - s.created) / 60000);
            String topIn = s.inBy.entrySet().stream().max(Comparator.comparingLong(e -> e.getValue()[1])).map(e -> label(e.getKey())).orElse("—");
            String topOut = s.outBy.entrySet().stream().max(Comparator.comparingLong(e -> e.getValue()[1])).map(e -> label(e.getKey())).orElse("—");
            NetPrefs np = NetPrefs.of(p.connection.connection);
            c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(String.format(" пинг %d мс, %d мин; ему %s (%s/мин, больше всего %s); от него %s (больше всего %s); последний от него %s назад%s",
                            p.latency, mins, Net.size(s.outBytes), Net.size(s.outBytes / mins), topOut, Net.size(s.inBytes), topIn,
                            ago(now - s.lastInAt), np.isDefault() ? "" : "; настройки: " + np)).withStyle(ChatFormatting.WHITE)), false);
        }
        return 1;
    }
}

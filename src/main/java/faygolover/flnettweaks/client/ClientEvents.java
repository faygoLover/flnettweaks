package faygolover.flnettweaks.client;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import faygolover.flnettweaks.DebugTools;
import faygolover.flnettweaks.FlnCommands;
import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import faygolover.flnettweaks.Net;
import faygolover.flnettweaks.NetStats;
import faygolover.flnettweaks.mixin.client.ConnectScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Клиент: подробности на экранах входа, хронология входа в лог, команда /flnetc. */
public final class ClientEvents {
    private ClientEvents() {}

    private static final int GRAY = 0xA8A8A8, WHITE = 0xE0E0E0, YELLOW = 0xFFE060;

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onOpen);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onRender);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onTick);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onCommands);
        ClientNet.init();
        if (FANCYMENU) FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientEvents::onClientSetup);
    }

    /** FancyMenu есть — подробности входа показывает меню-терминал (плейсхолдеры), см. FancyMenuCompat. */
    static final boolean FANCYMENU = ModList.get() != null && ModList.get().isLoaded("fancymenu");

    private static void onClientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(FancyMenuCompat::register);
    }

    /** Текущие подробности для плейсхолдеров: этап, остальные строки, строка «Ждём». */
    record Snapshot(String head, List<String> body, String waiting) {}

    private static final Snapshot EMPTY = new Snapshot("", List.of(), "");

    static Snapshot snapshot() {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        boolean connect = s instanceof ConnectScreen;
        if (!(connect || s instanceof ReceivingLevelScreen) || !JoinTracker.active || !FlnConfig.joinDetails()) return EMPTY;
        List<Line> lines = lines(mc, connect ? (ConnectScreen) s : null);
        String head = "", wait = "";
        List<String> body = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (i == 0) head = l.text;
            else if (l.color == YELLOW) wait = l.text;
            else body.add(l.text);
        }
        return new Snapshot(head, body, wait);
    }

    private static void onOpen(ScreenEvent.Opening e) {
        Screen s = e.getNewScreen();
        if (s instanceof ConnectScreen) JoinTracker.begin();
        else if (JoinTracker.active && s != null) {
            String n = s.getClass().getSimpleName();
            if (n.contains("Disconnect") || s instanceof ReconnectScreen) JoinTracker.finish("отключение (" + n + ")");
            else if (s instanceof JoinMultiplayerScreen || s instanceof TitleScreen) JoinTracker.finish("отменено");
        }
    }

    private static final boolean AUTOTEST = DebugTools.ON && Boolean.getBoolean("flnettweaks.debug.autotest");
    private static int testTicks;

    /** Автотест (dev): 100 КБ серверу с ответом 3 МБ (кадр больше ванильных 2 МБ), затем 9 МБ серверу — выше лимита, ждём понятное отключение. */
    private static void autotest(Minecraft mc) {
        var conn = mc.getConnection();
        if (conn == null || mc.player == null || mc.screen != null) return;
        testTicks++;
        if (testTicks == 60) {
            FlnTweaks.LOG.info("flnettweaks test: шаг 1 — 100 КБ серверу, прошу ответ 3000 КБ");
            conn.send(new ServerboundCustomPayloadPacket(DebugTools.TEST, DebugTools.noise(100, 3000)));
        } else if (testTicks == 160) {
            FlnTweaks.LOG.info("flnettweaks test: шаг 2 — 9000 КБ серверу (лимит 8192 КБ)");
            conn.send(new ServerboundCustomPayloadPacket(DebugTools.TEST, DebugTools.noise(9000, 0)));
        }
    }

    private static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (AUTOTEST) autotest(mc);
        if (!JoinTracker.active) return;
        if (JoinTracker.stage == JoinTracker.Stage.WORLD && mc.level != null && mc.player != null && !(mc.screen instanceof ReceivingLevelScreen))
            JoinTracker.finish("в мире");
    }

    private static void onRender(ScreenEvent.Render.Post e) {
        Screen s = e.getScreen();
        boolean connect = s instanceof ConnectScreen;
        if (!(connect || s instanceof ReceivingLevelScreen) || !JoinTracker.active || !FlnConfig.joinDetails()) return;
        if (FANCYMENU && FlnConfig.joinDetailsViaFancyMenu()) return;   // их выводит меню-терминал
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        GuiGraphics g = e.getGuiGraphics();
        List<Line> lines = lines(mc, connect ? (ConnectScreen) s : null);
        int y = s.height / 2 - 34;
        for (Line l : lines) {
            for (var part : font.split(Component.literal(l.text), Math.max(200, s.width - 40))) {
                g.drawCenteredString(font, part, s.width / 2, y, l.color);
                y += 10;
            }
        }
    }

    private record Line(String text, int color) {}

    private static String sec(long ms) {
        return String.format(Locale.ROOT, "%.1f с", ms / 1000.0);
    }

    private static List<Line> lines(Minecraft mc, ConnectScreen cs) {
        List<Line> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        JoinTracker.Stage st = JoinTracker.stage;
        out.add(new Line("Этап: " + st.title + " — " + sec(now - JoinTracker.stageStart) + " (всего " + sec(now - JoinTracker.start) + ")", WHITE));
        NetStats.Conn c = JoinTracker.channel != null ? NetStats.of(JoinTracker.channel) : null;
        if (c != null) out.add(new Line(String.format(Locale.ROOT, "Получено %d пак., %s · отправлено %d пак., %s",
                c.inPackets, Net.size(c.inBytes), c.outPackets, Net.size(c.outBytes)), GRAY));
        if (JoinTracker.lastAt > 0) out.add(new Line("Последнее от сервера: " + JoinTracker.lastWhat + " (" + Net.size(JoinTracker.lastSize) + "), "
                + sec(now - JoinTracker.lastAt) + " назад", GRAY));
        if (c != null && c.pendingFrame > 0)
            out.add(new Line(String.format(Locale.ROOT, "Принимаем пакет %s: %d %%", Net.size(c.pendingFrame),
                    (int) (100L * c.pendingHave / Math.max(1, c.pendingFrame))), WHITE));
        if (st == JoinTracker.Stage.FORGE || st == JoinTracker.Stage.APPLY) {
            String f = JoinTracker.regTotal > 0 ? "Реестры " + JoinTracker.regDone + "/" + JoinTracker.regTotal : "Сообщений Forge: " + JoinTracker.forgeMessages;
            if (JoinTracker.forgeStep != null) f += " · " + JoinTracker.forgeStep;
            out.add(new Line(f, GRAY));
        }
        BlockPos pos = mc.player != null ? mc.player.blockPosition() : null;
        if (st == JoinTracker.Stage.WORLD) {
            String w = "Чанков получено: " + JoinTracker.chunks + " (" + Net.size(JoinTracker.chunkBytes) + ")";
            if (pos != null && mc.level != null) {
                int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
                boolean have = mc.level.getChunkSource().hasChunk(cx, cz);
                boolean built = mc.levelRenderer.isChunkCompiled(pos);
                w += " · чанк под игроком (" + cx + ", " + cz + "): " + (have ? "получен" : "не получен") + ", " + (built ? "собран" : "не собран");
            }
            out.add(new Line(w, GRAY));
        }
        long quiet = now - Math.max(JoinTracker.stageStart, JoinTracker.lastAt);
        if (quiet >= FlnConfig.slowStepMs()) out.add(new Line("Ждём: " + hint(st, cs, c, pos) + " — " + sec(quiet), YELLOW));
        return out;
    }

    private static String hint(JoinTracker.Stage st, ConnectScreen cs, NetStats.Conn c, BlockPos pos) {
        if (c != null && c.pendingFrame > 0) return "приходит большой пакет от сервера (" + Net.size(c.pendingFrame) + ")";
        return switch (st) {
            case CONNECT -> cs != null && ((ConnectScreenAccessor) cs).fln$connection() == null
                    ? "TCP-подключение — сервер не отвечает (выключен, неверный адрес или порт закрыт)"
                    : "ответ сервера на запрос входа";
            case LOGIN -> "ответ сервера на вход";
            case AUTH -> "проверку аккаунта (сервер авторизации Mojang/Ely.by) и ответ сервера";
            case FORGE -> "следующее сообщение от сервера — сервер занят (тик сервера или моды проверяют вход)";
            case APPLY -> "игра применяет реестры сервера";
            case PLAYER -> "сервер загружает данные игрока и мир вокруг него";
            case WORLD -> pos == null ? "данные игрока от сервера" : "чанк под игроком и его сборку";
            case DONE -> "—";
        };
    }

    // ---------- /flnetc ----------

    private static void onCommands(RegisterClientCommandsEvent e) {
        var root = Commands.literal("flnetc");
        if (DebugTools.ON) root.then(Commands.literal("test").then(Commands.argument("kb", IntegerArgumentType.integer(1, 262144)).executes(c -> {
            int kb = IntegerArgumentType.getInteger(c, "kb");
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ServerboundCustomPayloadPacket(DebugTools.TEST, DebugTools.noise(kb)));
            c.getSource().sendSuccess(() -> Component.literal("flnettweaks: отправлено серверу " + kb + " КБ"), false);
            return 1;
        })));
        e.getDispatcher().register(root
                .then(Commands.literal("stats").executes(c -> run(c, o -> FlnCommands.statsLines(true, o)))
                        .then(Commands.literal("in").executes(c -> run(c, o -> FlnCommands.statsLines(true, o))))
                        .then(Commands.literal("out").executes(c -> run(c, o -> FlnCommands.statsLines(false, o)))))
                .then(Commands.literal("big").executes(c -> run(c, FlnCommands::bigLines)))
                .then(Commands.literal("limits").executes(c -> run(c, FlnCommands::limitLines))));
    }

    private static int run(CommandContext<CommandSourceStack> c, Consumer<Consumer<String>> f) {
        f.accept(s -> c.getSource().sendSuccess(() -> Component.literal(s), false));
        return 1;
    }
}

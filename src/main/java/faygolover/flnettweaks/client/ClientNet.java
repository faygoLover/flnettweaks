package faygolover.flnettweaks.client;

import faygolover.flnettweaks.ConnLog;
import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import faygolover.flnettweaks.Net;
import faygolover.flnettweaks.NetStats;
import faygolover.flnettweaks.Tls;
import faygolover.flnettweaks.mixin.client.DisconnectedScreenAccessor;
import io.netty.channel.Channel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.Locale;

/**
 * Клиент и обрывы связи: «Сервер не отвечает» в строке над хотбаром, само-переподключение после обрыва сети
 * (не после кика) с отсчётом на экране отключения (в сборке его оформляет FancyMenu — плейсхолдер flnet_reconnect),
 * свой журнал соединений.
 */
public final class ClientNet {
    private ClientNet() {}

    private static final int[] DELAYS = {3, 10, 30};

    // куда подключались (ConnectScreen.startConnecting) и сколько попыток подряд
    private static ServerAddress address;
    private static ServerData server;
    static int attempt;
    private static boolean ours;

    // сессия для журнала
    private static Channel session;
    private static long sessionStart, stallStart;
    private static int stalls;
    private static long stallMax, stallSum;
    private static boolean inStall;

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ClientNet::onOpen);
        MinecraftForge.EVENT_BUS.addListener(ClientNet::onInit);
        MinecraftForge.EVENT_BUS.addListener(ClientNet::onRender);
        MinecraftForge.EVENT_BUS.addListener(ClientNet::onTick);
        MinecraftForge.EVENT_BUS.addListener(ClientNet::onLogout);
        MinecraftForge.EVENT_BUS.addListener(NetTest::onOpen);
        if (FlnTweaks.NETTEST) MinecraftForge.EVENT_BUS.addListener(NetAutotest::tick);
    }

    /** ConnectScreen.startConnecting: запоминаем сервер для переподключения. */
    public static void target(ServerAddress addr, ServerData data) {
        address = addr;
        server = data;
    }

    /** Последний сервер, к которому подключались (для проверки связи). */
    static ServerData lastServer() {
        return server;
    }

    static String serverName() {
        return server != null ? server.ip : address != null ? address.getHost() + ":" + address.getPort() : "?";
    }

    // ---------- переподключение ----------

    /** Экран отключения, на котором идёт отсчёт, и когда переподключаться. */
    private static Screen waitScreen;
    private static long reconnectAt;
    private static String gaveUp;

    private static void onOpen(ScreenEvent.Opening e) {
        Screen s = e.getNewScreen();
        if (s instanceof ConnectScreen) {
            if (!ours) attempt = 0;   // игрок подключается сам — счёт попыток заново
            ours = false;
            waitScreen = null;
            gaveUp = null;
            ConnCause.reset();
            return;
        }
        if (s == null || s.getClass() != DisconnectedScreen.class) return;
        ConnCause.Cause c = ConnCause.cause;
        if (ConnCause.burst && c != ConnCause.Cause.KICK)
            ConnLog.write("ПОДСКАЗКА " + serverName() + "  вход оборвался посреди большого пакета или долгой тишины — предложено ограничение скорости"
                    + " (сейчас: " + faygolover.flnettweaks.NetPrefs.mine() + ")");
        // нет причины при attempt > 0 — не удалось даже подключиться (сервер ещё не поднялся, сеть не вернулась)
        boolean network = c == ConnCause.Cause.NETWORK || c == null && attempt > 0;
        if (!network || !FlnConfig.autoReconnect() || server == null || address == null) return;
        String reason = ((DisconnectedScreenAccessor) s).fln$reason().getString().replace('\n', ' ');
        if (attempt >= FlnConfig.reconnectAttempts()) {
            ConnLog.write("СДАЛСЯ  " + serverName() + "  " + attempt + " попыток переподключения не помогли: " + reason);
            gaveUp = "переподключиться не удалось: попыток " + attempt;
            attempt = 0;
            return;
        }
        int delay = DELAYS[Math.min(attempt, DELAYS.length - 1)];
        ConnLog.write(String.format(Locale.ROOT, "ОБРЫВ   %s  %s — переподключение через %d с (попытка %d из %d)",
                serverName(), reason, delay, attempt + 1, FlnConfig.reconnectAttempts()));
        waitScreen = s;
        reconnectAt = System.currentTimeMillis() + delay * 1000L;
    }

    /** Строка для экрана отключения: отсчёт до переподключения (пусто, если его нет). */
    public static String reconnectLine() {
        Minecraft mc = Minecraft.getInstance();
        if (waitScreen != null && mc.screen == waitScreen) {
            long left = Math.max(0, (reconnectAt - System.currentTimeMillis() + 999) / 1000);
            return "переподключение через " + left + " с · попытка " + (attempt + 1) + " из " + FlnConfig.reconnectAttempts();
        }
        if (gaveUp != null && mc.screen instanceof DisconnectedScreen) return gaveUp;
        return "";
    }

    /** Подключиться сейчас, не дожидаясь отсчёта (кнопка без FancyMenu, действие flnet_reconnect_now в FancyMenu). */
    public static void reconnectNow() {
        if (waitScreen != null && Minecraft.getInstance().screen == waitScreen) reconnect();
    }

    private static void reconnectTick(Minecraft mc) {
        if (waitScreen == null) return;
        if (mc.screen != waitScreen) {   // игрок ушёл с экрана («Вернуться») — отмена
            waitScreen = null;
            attempt = 0;
            return;
        }
        if (System.currentTimeMillis() >= reconnectAt) reconnect();
    }

    static void reconnect() {
        Minecraft mc = Minecraft.getInstance();
        waitScreen = null;
        if (server == null || address == null) return;
        attempt++;
        ours = true;
        FlnTweaks.LOG.info("flnettweaks: переподключение к {} (попытка {})", serverName(), attempt);
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, address, server, false);
    }

    private static boolean viaMenu() {
        return ClientEvents.FANCYMENU && FlnConfig.joinDetailsViaFancyMenu();
    }

    /** Экран отключения: подсказка «канал не выдерживает всплески» (пусто — её нет). Плейсхолдер flnet_hint. */
    public static String hintLine() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof DisconnectedScreen && ConnCause.burst && ConnCause.cause != ConnCause.Cause.KICK ? ConnCause.BURST_HINT : "";
    }

    /** Без FancyMenu-терминала: кнопка «Подключиться сейчас» и строка отсчёта на обычном экране отключения. */
    private static void onInit(ScreenEvent.Init.Post e) {
        if (e.getScreen() instanceof DisconnectedScreen && !ClientEvents.FANCYMENU && !hintLine().isEmpty()) {
            Screen s = e.getScreen();
            e.addListener(Button.builder(Component.literal("Настройки соединения"), b -> Minecraft.getInstance().setScreen(new NetOptionsScreen(s)))
                    .bounds(s.width / 2 - 100, 8, 200, 20).build());
        }
        if (e.getScreen() != waitScreen || viaMenu()) return;
        Screen s = e.getScreen();
        e.addListener(Button.builder(Component.literal("Подключиться сейчас"), b -> reconnectNow())
                .bounds(s.width / 2 - 100, s.height - 30, 200, 20).build());
    }

    private static void onRender(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof DisconnectedScreen)) return;
        String h = ClientEvents.FANCYMENU ? "" : hintLine();
        if (!h.isEmpty()) e.getGuiGraphics().drawCenteredString(Minecraft.getInstance().font, h, e.getScreen().width / 2, 34, 0xFFE060);
        if (viaMenu()) return;
        String l = reconnectLine();
        if (!l.isEmpty()) e.getGuiGraphics().drawCenteredString(Minecraft.getInstance().font, l, e.getScreen().width / 2, e.getScreen().height - 46, 0xFFE060);
    }

    // ---------- «Сервер не отвечает» — в строке над хотбаром ----------

    private static Channel gameChannel(Minecraft mc) {
        var l = mc.getConnection();
        if (l == null || mc.level == null) return null;
        Connection c = l.getConnection();
        return c.isMemoryConnection() ? null : c.channel();
    }

    private static long shownSec = -1;

    private static void stallMessage(Minecraft mc, Channel ch) {
        long warn = FlnConfig.stallWarnMs();
        long last = NetStats.of(ch).lastInAt;
        long gap = System.currentTimeMillis() - last;
        if (warn <= 0 || last == 0 || gap < warn) { shownSec = -1; return; }
        long sec = gap / 1000;
        if (sec == shownSec) return;
        shownSec = sec;
        long left = Math.max(0, faygolover.flnettweaks.NetPrefs.clientReadTimeout() - sec);
        String t = "Сервер не отвечает " + sec + " с · ждём ещё " + left + " с" + (FlnConfig.autoReconnect() ? ", потом переподключимся" : "");
        mc.gui.setOverlayMessage(Component.literal(t).withStyle(ChatFormatting.YELLOW), false);
    }

    // ---------- журнал ----------

    private static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        reconnectTick(mc);
        Channel ch = gameChannel(mc);
        if (ch == null || mc.player == null) return;
        stallMessage(mc, ch);
        long now = System.currentTimeMillis();
        if (session != ch) {
            session = ch;
            sessionStart = now;
            stalls = 0; stallMax = stallSum = 0; inStall = false;
            attempt = 0;   // мы в мире — серия переподключений удалась
            boolean tls = Boolean.TRUE.equals(ch.attr(Tls.ON).get());
            ConnLog.write(String.format(Locale.ROOT, "ВХОД    %s  %s  вход за %s%s", serverName(), ConnLog.ip(ch.remoteAddress()),
                    ConnLog.dur(now - NetStats.of(ch).created), tls ? "  TLS" : ""));
        }
        long last = NetStats.of(ch).lastInAt;
        if (last == 0) return;
        if (!inStall && now - last >= FlnConfig.logStallMs()) { inStall = true; stallStart = last; }
        if (inStall && last > stallStart) endStall(last - stallStart, false);
    }

    private static void endStall(long ms, boolean cut) {
        inStall = false;
        stalls++;
        stallSum += ms;
        stallMax = Math.max(stallMax, ms);
        ConnLog.detail("ПАУЗА   " + serverName() + "  " + ConnLog.dur(ms) + " ничего не приходило от сервера" + (cut ? " — так и оборвалось" : ""));
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        if (session == null) return;
        Channel ch = session;
        session = null;
        long now = System.currentTimeMillis();
        if (inStall) endStall(now - stallStart, true);
        NetStats.Conn st = NetStats.of(ch);
        ConnCause.Cause c = ConnCause.cause;
        String why = c == null ? "выход" : switch (c) {
            case NETWORK -> "обрыв связи: " + ConnCause.detail;
            case KICK -> "сервер отключил: " + ConnCause.detail;
            case LOCAL -> ConnCause.detail;
        };
        StringBuilder sb = new StringBuilder("ВЫХОД   ").append(serverName()).append("  ").append(why);
        sb.append(" · в игре ").append(ConnLog.dur(now - sessionStart));
        if (stalls > 0) sb.append(String.format(Locale.ROOT, " · паузы связи: %d, самая длинная %s, всего %s", stalls, ConnLog.dur(stallMax), ConnLog.dur(stallSum)));
        else sb.append(" · пауз связи не было");
        sb.append(" · трафик: от сервера ").append(Net.size(st.inBytes)).append(", серверу ").append(Net.size(st.outBytes));
        ConnLog.write(sb.toString());
    }
}

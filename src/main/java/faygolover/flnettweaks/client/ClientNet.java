package faygolover.flnettweaks.client;

import faygolover.flnettweaks.ConnLog;
import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import faygolover.flnettweaks.Net;
import faygolover.flnettweaks.NetStats;
import faygolover.flnettweaks.Tls;
import faygolover.flnettweaks.mixin.client.DisconnectedScreenAccessor;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.Locale;

/**
 * Клиент и обрывы связи: надпись «Сервер не отвечает» поверх игры, само-переподключение после обрыва сети
 * (не после кика), свой журнал соединений.
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
        MinecraftForge.EVENT_BUS.addListener(ClientNet::onHud);
        MinecraftForge.EVENT_BUS.addListener(ClientNet::onTick);
        MinecraftForge.EVENT_BUS.addListener(ClientNet::onLogout);
    }

    /** ConnectScreen.startConnecting: запоминаем сервер для переподключения. */
    public static void target(ServerAddress addr, ServerData data) {
        address = addr;
        server = data;
    }

    static String serverName() {
        return server != null ? server.ip : address != null ? address.getHost() + ":" + address.getPort() : "?";
    }

    // ---------- переподключение ----------

    private static void onOpen(ScreenEvent.Opening e) {
        Screen s = e.getNewScreen();
        if (s instanceof ConnectScreen) {
            if (!ours) attempt = 0;   // игрок подключается сам — счёт попыток заново
            ours = false;
            ConnCause.reset();
            return;
        }
        if (s == null || s.getClass() != DisconnectedScreen.class) return;
        ConnCause.Cause c = ConnCause.cause;
        // нет причины при attempt > 0 — не удалось даже подключиться (сервер ещё не поднялся, сеть не вернулась)
        boolean network = c == ConnCause.Cause.NETWORK || c == null && attempt > 0;
        String reason = ((DisconnectedScreenAccessor) s).fln$reason().getString().replace('\n', ' ');
        if (!network || !FlnConfig.autoReconnect() || server == null || address == null) return;
        if (attempt >= FlnConfig.reconnectAttempts()) {
            ConnLog.write("СДАЛСЯ  " + serverName() + "  " + attempt + " попыток переподключения не помогли: " + reason);
            attempt = 0;
            return;
        }
        int delay = DELAYS[Math.min(attempt, DELAYS.length - 1)];
        ConnLog.write(String.format(Locale.ROOT, "ОБРЫВ   %s  %s — переподключение через %d с (попытка %d из %d)",
                serverName(), reason, delay, attempt + 1, FlnConfig.reconnectAttempts()));
        e.setNewScreen(new ReconnectScreen(s.getTitle(), ((DisconnectedScreenAccessor) s).fln$reason(), delay, attempt + 1, FlnConfig.reconnectAttempts()));
    }

    static void reconnect() {
        Minecraft mc = Minecraft.getInstance();
        if (server == null || address == null) { cancel(); return; }
        attempt++;
        ours = true;
        FlnTweaks.LOG.info("flnettweaks: переподключение к {} (попытка {})", serverName(), attempt);
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, address, server, false);
    }

    static void cancel() {
        attempt = 0;
        Minecraft.getInstance().setScreen(new JoinMultiplayerScreen(new TitleScreen()));
    }

    // ---------- надпись поверх игры ----------

    private static Channel gameChannel(Minecraft mc) {
        var l = mc.getConnection();
        if (l == null || mc.level == null) return null;
        Connection c = l.getConnection();
        return c.isMemoryConnection() ? null : c.channel();
    }

    private static void onHud(RenderGuiEvent.Post e) {
        long warn = FlnConfig.stallWarnMs();
        if (warn <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        Channel ch = gameChannel(mc);
        if (ch == null) return;
        long last = NetStats.of(ch).lastInAt;
        long gap = System.currentTimeMillis() - last;
        if (last == 0 || gap < warn) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int w = g.guiWidth();
        long left = Math.max(0, FlnConfig.readTimeout() * 1000L - gap);
        String l1 = String.format(Locale.ROOT, "Сервер не отвечает: %d с", gap / 1000);
        String l2 = "Ждём связь ещё " + left / 1000 + " с" + (FlnConfig.autoReconnect() ? ", потом переподключимся сами" : "");
        String l3 = gap >= 20_000 ? "Esc → «Отключиться» — зайти заново сразу" : null;
        int y = 32;
        int bw = Math.max(font.width(l1), Math.max(font.width(l2), l3 == null ? 0 : font.width(l3))) + 12;
        g.fill(w / 2 - bw / 2, y - 4, w / 2 + bw / 2, y + (l3 == null ? 22 : 32), 0xA0000000);
        g.drawCenteredString(font, l1, w / 2, y, 0xFFE060);
        g.drawCenteredString(font, l2, w / 2, y + 11, 0xC8C8C8);
        if (l3 != null) g.drawCenteredString(font, l3, w / 2, y + 21, 0xA8A8A8);
    }

    // ---------- журнал ----------

    private static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Channel ch = gameChannel(mc);
        if (ch == null || mc.player == null) return;
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

package faygolover.flnettweaks.client;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import faygolover.flnettweaks.NetPrefs;
import faygolover.flnettweaks.Probe;
import faygolover.flnettweaks.mixin.client.JoinMultiplayerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraftforge.client.event.ScreenEvent;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Проверка связи из окна настроек соединения: пинг (6 раз) и пробная загрузка с сервера (до 8 МБ или 7 с) — тем же
 * путём, что и вход (TLS, если он включён для этого адреса). По итогу — совет: ограничение скорости, долгое терпение,
 * сильное сжатие. Сам ничего не включает: совет применяется кнопкой.
 */
public final class NetTest {
    private NetTest() {}

    public enum State { IDLE, RUNNING, DONE, FAILED }

    public static volatile State state = State.IDLE;
    public static volatile String progress = "";
    public static volatile List<String> result = List.of();
    public static volatile NetPrefs advice;
    private static volatile ServerData lastSelected;

    // ---------- какой сервер проверять ----------

    /** Уходя с экрана серверов, запоминаем выбранный в списке сервер. */
    public static void onOpen(ScreenEvent.Opening e) {
        Screen cur = e.getCurrentScreen();
        if (cur instanceof JoinMultiplayerScreen js) {
            ServerSelectionList list = ((JoinMultiplayerScreenAccessor) js).fln$list();
            if (list != null && list.getSelected() instanceof ServerSelectionList.OnlineServerEntry se) lastSelected = se.getServerData();
        }
    }

    /** Выбранный на экране серверов → последний, к которому подключались → первый в списке. */
    public static ServerData target() {
        if (lastSelected != null) return lastSelected;
        ServerData last = ClientNet.lastServer();
        if (last != null) return last;
        ServerList l = new ServerList(Minecraft.getInstance());
        l.load();
        return l.size() > 0 ? l.get(0) : null;
    }

    public static String targetName() {
        ServerData d = target();
        return d == null ? "нет серверов в списке" : d.name + " (" + d.ip + ")";
    }

    // ---------- проверка ----------

    public static synchronized void start() {
        if (state == State.RUNNING) return;
        ServerData d = target();
        if (d == null) { fail("нет сервера для проверки — добавьте его в список"); return; }
        state = State.RUNNING;
        advice = null;
        result = List.of();
        progress = "подключение к " + d.name + "…";
        Thread t = new Thread(() -> run(d), "flnettweaks-probe");
        t.setDaemon(true);
        t.start();
    }

    private static void fail(String why) {
        result = List.of("проверка не удалась: " + why);
        advice = null;
        state = State.FAILED;
        FlnTweaks.LOG.info("flnettweaks: проверка связи — {}", why);
    }

    private static void run(ServerData d) {
        ServerAddress addr = ServerAddress.parseString(d.ip);
        Optional<ResolvedServerAddress> res;
        try { res = ServerNameResolver.DEFAULT.resolveAddress(addr); } catch (RuntimeException e) { res = Optional.empty(); }
        if (res.isEmpty()) { fail("не найден адрес " + d.ip); return; }
        InetSocketAddress isa = res.get().asInetSocketAddress();
        boolean tls = FlnConfig.tlsFor(addr.getHost());
        long[] rtt = new long[6];
        try (Socket raw = new Socket()) {
            raw.connect(isa, 5000);
            raw.setTcpNoDelay(true);
            raw.setSoTimeout(5000);
            Socket s = tls ? wrapTls(raw, addr) : raw;
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            InputStream in = s.getInputStream();
            out.write(Probe.MAGIC);
            for (int i = 0; i < rtt.length; i++) {
                progress = "пинг " + (i + 1) + " из " + rtt.length + "…";
                long t0 = System.nanoTime();
                out.write('P');
                out.flush();
                if (in.read() != 'p') { fail("сервер не отвечает на проверку (нужен flnettweaks 1.2.0+ на сервере)"); return; }
                rtt[i] = (System.nanoTime() - t0) / 1_000_000;
            }
            out.write('D');
            out.writeInt(Probe.MAX_DOWNLOAD);
            out.flush();
            int ack = in.read();
            if (ack == 'b') { fail("сервер занят другой проверкой — повторите через 10 с"); return; }
            if (ack != 'd') { fail("сервер оборвал проверку"); return; }
            s.setSoTimeout(8000);
            byte[] buf = new byte[65536];
            long start = System.nanoTime(), first = 0, last = 0, got = 0, maxGap = 0;
            int stalls = 0;
            boolean cut = false;
            while (got < Probe.MAX_DOWNLOAD) {
                int n;
                try { n = in.read(buf); } catch (SocketTimeoutException e) { cut = true; stalls++; maxGap = Math.max(maxGap, 8000); break; }
                if (n < 0) break;
                long now = System.nanoTime();
                if (first == 0) first = now;
                else {
                    long gap = (now - last) / 1_000_000;
                    maxGap = Math.max(maxGap, gap);
                    if (gap >= 1000) stalls++;
                }
                last = now;
                got += n;
                long ms = (now - start) / 1_000_000;
                progress = String.format(Locale.ROOT, "загрузка: %.1f МБ за %.1f с…", got / 1048576.0, ms / 1000.0);
                if (ms >= 7000) break;
            }
            double sec = Math.max(0.05, (last - first) / 1e9);
            double mbit = got * 8 / sec / 1e6;
            finish(rtt, mbit, got, stalls, maxGap, cut, tls);
        } catch (IOException e) {
            fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static Socket wrapTls(Socket raw, ServerAddress addr) throws IOException {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
            SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket(raw, addr.getHost(), addr.getPort(), true);
            String sni = FlnConfig.tlsSni().isEmpty() ? addr.getHost() : FlnConfig.tlsSni();
            if (!sni.matches("[0-9.]+") && !sni.contains(":")) {
                SSLParameters p = s.getSSLParameters();
                p.setServerNames(List.of(new SNIHostName(sni)));
                s.setSSLParameters(p);
            }
            s.startHandshake();
            return s;
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static void finish(long[] rtt, double mbit, long got, int stalls, long maxGap, boolean cut, boolean tls) {
        long min = Long.MAX_VALUE, max = 0, sum = 0;
        for (long r : rtt) { min = Math.min(min, r); max = Math.max(max, r); sum += r; }
        long avg = sum / rtt.length, jitter = max - min;
        boolean bursty = stalls > 0 || maxGap >= 1500;
        int rate = 0;
        if (bursty) {
            rate = 500;
            for (int r : new int[]{4000, 2000, 1000}) if (r <= mbit * 1000 * 0.6) { rate = r; break; }
        }
        boolean longP = avg > 250 || jitter > 500 || bursty;
        boolean strong = mbit < 2;
        NetPrefs a = new NetPrefs(rate, longP, strong);
        List<String> l = new ArrayList<>();
        l.add(String.format(Locale.ROOT, "пинг %d мс (от %d до %d)%s", avg, min, max, tls ? ", через TLS" : ""));
        l.add(String.format(Locale.ROOT, "скорость %.1f Мбит/с (%.1f МБ)", mbit, got / 1048576.0)
                + (stalls == 0 ? ", замираний нет" : ", замираний: " + stalls + ", самое долгое " + maxGap / 1000.0 + " с" + (cut ? " — так и оборвалось" : "")));
        l.add(a.isDefault() ? "совет: всё обычное — менять ничего не нужно" : "совет: " + a);
        result = l;
        advice = a;
        state = State.DONE;
        FlnTweaks.LOG.info("flnettweaks: проверка связи — {}", String.join("; ", l));
    }

    /** Кнопка «Применить совет». */
    public static void applyAdvice() {
        NetPrefs a = advice;
        if (a != null) FlnConfig.setPlayerPrefs(a);
    }

    /** Строка состояния проверки по номеру: 0 — сервер/ход, 1..3 — итог. */
    public static String line(int i) {
        if (i == 0) return state == State.RUNNING ? progress : "сервер: " + targetName();
        List<String> r = result;
        return i - 1 < r.size() ? r.get(i - 1) : "";
    }
}

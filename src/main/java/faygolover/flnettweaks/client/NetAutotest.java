package faygolover.flnettweaks.client;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import faygolover.flnettweaks.NetPrefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;

/**
 * Проверка окна настроек соединения на стенде (-Dflnettweaks.debug.nettest=true): экран серверов → окно (FancyMenu
 * dept_net или обычное) → проверка связи → скриншоты → экран отключения с подсказкой → вход с настройками из
 * -Dflnettweaks.debug.rate (кбит/с). Ничего не делает без свойства.
 */
public final class NetAutotest {
    private NetAutotest() {}

    public static final boolean ON = Boolean.getBoolean("flnettweaks.debug.nettest");
    private static int t, step;

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "flnet_" + name + ".png", mc.getMainRenderTarget(), m -> FlnTweaks.LOG.info("flnettweaks autotest: снимок {}", name));
    }

    public static void tick(TickEvent.ClientTickEvent e) {
        if (!ON || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (step == 0 && !(mc.screen instanceof TitleScreen)) return;
        t++;
        switch (step) {
            case 0 -> { if (t > 100) { mc.setScreen(new JoinMultiplayerScreen(mc.screen)); next(); } }
            case 1 -> { if (t > 80) { shot(mc, "1_servers"); next(); } }
            case 2 -> { if (t > 5) {
                if (ClientEvents.FANCYMENU) FancyMenuCompat.run("opengui", "dept_net");
                else mc.setScreen(new NetOptionsScreen(mc.screen));
                NetTest.start();
                next(); } }
            case 3 -> { if (t > 60) { shot(mc, "2_netopts_running"); next(); } }
            case 4 -> { if (NetTest.state == NetTest.State.DONE || NetTest.state == NetTest.State.FAILED || t > 600) {
                FlnTweaks.LOG.info("flnettweaks autotest: проверка связи — {} / {} / {} / {}", NetTest.line(0), NetTest.line(1), NetTest.line(2), NetTest.line(3));
                next(); } }
            case 5 -> { if (t > 20) { shot(mc, "3_netopts_done"); next(); } }
            case 6 -> { if (t > 5) {
                ConnCause.reset();
                ConnCause.burst = true;
                mc.setScreen(new DisconnectedScreen(new JoinMultiplayerScreen(new TitleScreen()), Component.literal("Отключено"),
                        Component.literal("flnettweaks autotest: от сервера ничего не приходило")));
                next(); } }
            case 7 -> { if (t > 80) { shot(mc, "4_disconnect_hint"); next(); } }
            case 8 -> { if (t > 5) {
                int rate = Integer.getInteger("flnettweaks.debug.rate", -1);
                try {   // файл в папке игры важнее свойства (лаунчер держит аргументы JVM у себя)
                    java.nio.file.Path f = mc.gameDirectory.toPath().resolve("flnet_autotest_rate.txt");
                    if (java.nio.file.Files.exists(f)) rate = Integer.parseInt(java.nio.file.Files.readString(f).trim());
                } catch (Exception ignored) {}
                if (rate >= 0) FlnConfig.setPlayerPrefs(new NetPrefs(rate, NetPrefs.mine().patienceS, NetPrefs.mine().strongCompression));
                ConnCause.reset();
                ServerData d = NetTest.target();
                FlnTweaks.LOG.info("flnettweaks autotest: вход на {} с настройками {}", d == null ? "?" : d.ip, NetPrefs.mine());
                if (d != null) ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(d.ip), d, false);
                next(); } }
            default -> {}
        }
    }

    private static void next() {
        step++;
        t = 0;
    }
}

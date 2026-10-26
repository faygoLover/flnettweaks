package faygolover.flnettweaks.client;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.NetPrefs;

import java.util.Locale;

/** Общее для окна настроек соединения: переключатели и тексты (FancyMenu и обычный экран). */
public final class NetOpts {
    private NetOpts() {}

    static final String RATE_HELP = "Сколько сервер отдаёт вам в секунду. Авто — без ограничения. Ограничение помогает, если VPN или "
            + "мобильная сеть теряет данные на всплесках и вход обрывается на большом пакете. Вход станет дольше.";
    static final String PATIENCE_HELP = "Сколько ждать молчащий сервер, прежде чем считать связь оборванной: 2 мин или 5 мин "
            + "(сервер ждёт вас так же). Для медленного или нестабильного канала.";
    static final String COMPRESS_HELP = "Сильное — сервер сжимает данные сильнее: меньше трафика, вход на медленном канале быстрее.";

    /** Значение для плейсхолдера flnet_opt (name = rate / patience / compress / tls). */
    public static String value(String name) {
        NetPrefs p = NetPrefs.mine();
        return switch (name == null ? "" : name.trim().toLowerCase(Locale.ROOT)) {
            case "rate" -> NetPrefs.rateName(p.rateKbit);
            case "patience" -> NetPrefs.patienceName(p.longPatience);
            case "compress" -> NetPrefs.compressionName(p.strongCompression);
            case "tls" -> FlnConfig.tlsClient() ? "вкл" : "выкл";
            default -> "";
        };
    }

    /** Действие flnet_opt: «rate» / «rate:next» — следующее значение, «rate:1000», «patience:long», «compress:toggle»… */
    public static void cycle(String spec) {
        String s = spec == null ? "" : spec.trim().toLowerCase(Locale.ROOT);
        String name = s, v = "next";
        int i = s.indexOf(':');
        if (i >= 0) { name = s.substring(0, i); v = s.substring(i + 1); }
        NetPrefs p = NetPrefs.mine();
        int rate = p.rateKbit;
        boolean longP = p.longPatience, strong = p.strongCompression;
        switch (name) {
            case "rate" -> {
                if (v.equals("next") || v.equals("toggle")) {
                    int k = 0;
                    for (int j = 0; j < NetPrefs.RATES.length; j++) if (NetPrefs.RATES[j] == rate) k = j;
                    rate = NetPrefs.RATES[(k + 1) % NetPrefs.RATES.length];
                } else if (v.equals("auto")) rate = 0;
                else try { rate = Integer.parseInt(v); } catch (NumberFormatException ignored) {}
            }
            case "patience" -> longP = v.equals("long") || (!v.equals("normal") && !longP);
            case "compress" -> strong = v.equals("strong") || (!v.equals("normal") && !strong);
            case "tls" -> { FlnConfig.setTlsClient(v.equals("on") || (!v.equals("off") && !FlnConfig.tlsClient())); return; }
            default -> { return; }
        }
        FlnConfig.setPlayerPrefs(new NetPrefs(rate, longP, strong));
    }
}

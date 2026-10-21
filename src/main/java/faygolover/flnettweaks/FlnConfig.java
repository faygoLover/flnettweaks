package faygolover.flnettweaks;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.Locale;

/**
 * Конфиг config/flnettweaks-common.toml. Лимиты должны совпадать на сервере и у игроков: каждая сторона проверяет то,
 * что получает, и то, что отправляет. Значения читаются при каждом обращении — до загрузки конфига действуют значения
 * по умолчанию.
 */
public final class FlnConfig {
    private FlnConfig() {}

    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue SERVERBOUND_PAYLOAD_KB, CLIENTBOUND_PAYLOAD_KB, LOGIN_PAYLOAD_KB, PACKET_KB, FRAME_KB, NBT_KB;
    public static final ForgeConfigSpec.IntValue LOGIN_TIMEOUT_S, READ_TIMEOUT_S, KEEPALIVE_TIMEOUT_S;
    public static final ForgeConfigSpec.IntValue BIG_PACKET_KB, SLOW_STEP_S, SLOW_PACKET_MS;
    public static final ForgeConfigSpec.BooleanValue JOIN_TIMELINE, JOIN_DETAILS_ON_SCREEN, JOIN_DETAILS_VIA_FANCYMENU, IGNORE_CHAT_VALIDATION, SKIP_BROKEN_CHUNK_SECTIONS;
    public static final ForgeConfigSpec.IntValue CLOSE_AFTER_KICK_S, STALL_WARN_S, RECONNECT_ATTEMPTS, LOG_STALL_S;
    public static final ForgeConfigSpec.BooleanValue AUTO_RECONNECT, CONN_LOG, CONN_LOG_DETAILS, TLS_SERVER, TLS_CLIENT, SMOOTH_JOIN;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TLS_CLIENT_HOSTS;
    public static final ForgeConfigSpec.ConfigValue<String> TLS_SNI;

    static {
        B.comment("Лимиты размеров. Ваниль: пакет мода игрок->сервер 32 КБ, сервер->игрок 1 МБ, пакет 8 МБ, кадр 2 МБ, NBT 2 МБ.",
                "Ставьте одинаково на сервере и у игроков.").push("limits");
        SERVERBOUND_PAYLOAD_KB = B.comment("Пакет мода от игрока серверу (custom payload), КБ.").defineInRange("serverboundPayloadKB", 8192, 32, 262144);
        CLIENTBOUND_PAYLOAD_KB = B.comment("Пакет мода от сервера игроку, КБ.").defineInRange("clientboundPayloadKB", 32768, 1024, 262144);
        LOGIN_PAYLOAD_KB = B.comment("Пакеты модов во время входа (Forge: список модов, реестры, конфиги), КБ.").defineInRange("loginPayloadKB", 32768, 1024, 262144);
        PACKET_KB = B.comment("Любой пакет целиком (до сжатия), КБ.").defineInRange("packetKB", 32768, 8192, 262144);
        FRAME_KB = B.comment("Кадр в сети (после сжатия), КБ. Ваниль умеет только до 2 МБ.").defineInRange("frameKB", 32768, 2048, 262144);
        NBT_KB = B.comment("NBT внутри пакета (предметы, блоки, данные модов), КБ.").defineInRange("nbtKB", 65536, 2048, 524288);
        B.pop();
        B.comment("Тайм-ауты.").push("timeouts");
        LOGIN_TIMEOUT_S = B.comment("Сколько секунд даётся на вход (ваниль 30). Большие сборки входят дольше.").defineInRange("loginSeconds", 300, 30, 3600);
        READ_TIMEOUT_S = B.comment("Обрыв, если от другой стороны ничего не приходит столько секунд (ваниль 30).").defineInRange("readSeconds", 120, 30, 3600);
        KEEPALIVE_TIMEOUT_S = B.comment("Сервер: интервал и предел ответа на keepalive, секунд (ваниль 15: «Timed out» через 15–30 с).").defineInRange("keepAliveSeconds", 60, 15, 600);
        B.pop();
        B.comment("Диагностика.").push("diagnostics");
        BIG_PACKET_KB = B.comment("Пакеты не меньше этого размера (КБ) пишутся в лог и в /flnet big.").defineInRange("bigPacketKB", 512, 16, 262144);
        SLOW_STEP_S = B.comment("Этап входа дольше стольких секунд — предупреждение в логе и подсказка «чего ждём» на экране.").defineInRange("slowStepSeconds", 10, 2, 600);
        SLOW_PACKET_MS = B.comment("Обработка одного пакета в основном потоке дольше стольких мс — запись в лог (кто тормозит игру/сервер).").defineInRange("slowPacketMs", 500, 20, 600000);
        JOIN_TIMELINE = B.comment("Сервер: писать в лог хронологию каждого входа по этапам.").define("joinTimeline", true);
        JOIN_DETAILS_ON_SCREEN = B.comment("Клиент: подробности на экранах «Подключение» и «Загрузка местности».").define("joinDetailsOnScreen", true);
        JOIN_DETAILS_VIA_FANCYMENU = B.comment("Клиент: если стоит FancyMenu, подробности входа выводит меню-терминал через плейсхолдеры",
                "flnet_head / flnet_body / flnet_wait, а мод сам их не рисует. false — мод рисует их поверх экрана, как раньше.").define("joinDetailsViaFancyMenu", true);
        B.pop();
        B.comment("Устойчивость.").push("tweaks");
        IGNORE_CHAT_VALIDATION = B.comment("Клиент: не отключаться от сервера из-за ошибки проверки подписи чата (только запись в лог).").define("ignoreChatValidationErrors", true);
        SKIP_BROKEN_CHUNK_SECTIONS = B.comment("Клиент: пропускать пустые/обрезанные секции чанка вместо вылета.").define("skipBrokenChunkSections", true);
        SMOOTH_JOIN = B.comment("Клиент: плавный вход — пока игра долго обрабатывает данные сервера (теги → запуск JEI), экран загрузки",
                "продолжает рисоваться, и окно не «Не отвечает».").define("smoothJoin", true);
        B.pop();
        B.comment("Обрывы связи.").push("connection");
        CLOSE_AFTER_KICK_S = B.comment("Сервер: если сообщение об отключении не дошло до игрока за столько секунд, соединение закрывается",
                "(иначе игрок висит в мире до readSeconds, а его повторный вход ждёт). 0 — как в ванили.").defineInRange("closeAfterKickSeconds", 5, 0, 120);
        STALL_WARN_S = B.comment("Клиент: от сервера ничего не приходит столько секунд — надпись «Сервер не отвечает» поверх игры. 0 — не показывать.")
                .defineInRange("stallWarnSeconds", 3, 0, 60);
        AUTO_RECONNECT = B.comment("Клиент: при обрыве связи (не кик и не бан) переподключаться самому: через 3 с, 10 с, дальше каждые 30 с.").define("autoReconnect", true);
        RECONNECT_ATTEMPTS = B.comment("Клиент: сколько попыток переподключения подряд.").defineInRange("reconnectAttempts", 10, 1, 100);
        B.pop();
        B.comment("Журнал соединений: logs/flnettweaks-connections.log (на сервере — все игроки, у игрока — свои подключения).").push("connectionLog");
        CONN_LOG = B.comment("Вести журнал: входы, выходы с причиной, время в игре, пинг, паузы связи, трафик.").define("enabled", true);
        CONN_LOG_DETAILS = B.comment("Подробно: каждая пауза связи отдельной строкой, очередь отправки, имя хоста адреса игрока, TLS.").define("details", true);
        LOG_STALL_S = B.comment("Пауза связи — ничего не приходит от другой стороны столько секунд.").defineInRange("stallSeconds", 3, 1, 60);
        B.pop();
        B.comment("TLS-обёртка: соединение с сервером снаружи выглядит как HTTPS (провайдер, VPN и DPI не видят, что это Minecraft) и шифруется.",
                "Сервер принимает и TLS, и обычный вход на одном порту.").push("tls");
        TLS_SERVER = B.comment("Сервер: принимать TLS-подключения.").define("server", true);
        TLS_CLIENT = B.comment("Клиент: подключаться к серверам через TLS. Сервер должен быть с flnettweaks 1.1.0+ и tls.server = true.").define("client", false);
        TLS_CLIENT_HOSTS = B.comment("Клиент: к каким адресам (как в списке серверов, без порта) подключаться через TLS. Пусто — ко всем.")
                .defineListAllowEmpty("clientHosts", List.of(), o -> o instanceof String);
        TLS_SNI = B.comment("Клиент: имя сайта в начале TLS (SNI), его видит провайдер. Пусто — адрес сервера, если это имя, а не IP.").define("sni", "");
        B.pop();
        SPEC = B.build();
    }

    private static int get(ForgeConfigSpec.IntValue v, int def) {
        try { return v.get(); } catch (RuntimeException e) { return def; }
    }

    private static boolean get(ForgeConfigSpec.BooleanValue v, boolean def) {
        try { return v.get(); } catch (RuntimeException e) { return def; }
    }

    public static int serverboundPayload() { return get(SERVERBOUND_PAYLOAD_KB, 8192) * 1024; }
    public static int clientboundPayload() { return get(CLIENTBOUND_PAYLOAD_KB, 32768) * 1024; }
    public static int loginPayload() { return get(LOGIN_PAYLOAD_KB, 32768) * 1024; }
    public static int packet() { return get(PACKET_KB, 32768) * 1024; }
    public static int frame() { return get(FRAME_KB, 32768) * 1024; }
    public static long nbt() { return get(NBT_KB, 65536) * 1024L; }
    public static int loginTicks() { return get(LOGIN_TIMEOUT_S, 300) * 20; }
    public static int readTimeout() { return get(READ_TIMEOUT_S, 120); }
    public static long keepAliveMs() { return get(KEEPALIVE_TIMEOUT_S, 60) * 1000L; }
    public static int bigPacket() { return get(BIG_PACKET_KB, 512) * 1024; }
    public static long slowStepMs() { return get(SLOW_STEP_S, 10) * 1000L; }
    public static long slowPacketNs() { return get(SLOW_PACKET_MS, 500) * 1_000_000L; }
    public static boolean joinTimeline() { return get(JOIN_TIMELINE, true); }
    public static boolean joinDetails() { return get(JOIN_DETAILS_ON_SCREEN, true); }
    public static boolean joinDetailsViaFancyMenu() { return get(JOIN_DETAILS_VIA_FANCYMENU, true); }
    public static boolean ignoreChatValidation() { return get(IGNORE_CHAT_VALIDATION, true); }
    public static boolean skipBrokenSections() { return get(SKIP_BROKEN_CHUNK_SECTIONS, true); }
    public static boolean smoothJoin() { return get(SMOOTH_JOIN, true); }
    public static int closeAfterKickSeconds() { return get(CLOSE_AFTER_KICK_S, 5); }
    public static long stallWarnMs() { return get(STALL_WARN_S, 3) * 1000L; }
    public static boolean autoReconnect() { return get(AUTO_RECONNECT, true); }
    public static int reconnectAttempts() { return get(RECONNECT_ATTEMPTS, 10); }
    public static boolean connLog() { return get(CONN_LOG, true); }
    public static boolean connLogDetails() { return connLog() && get(CONN_LOG_DETAILS, true); }
    public static long logStallMs() { return get(LOG_STALL_S, 3) * 1000L; }
    public static boolean tlsServer() { return get(TLS_SERVER, true); }
    public static boolean tlsClient() { return get(TLS_CLIENT, false); }

    public static String tlsSni() {
        try { return TLS_SNI.get().trim(); } catch (RuntimeException e) { return ""; }
    }

    /** Подключаться ли к этому адресу через TLS. */
    public static boolean tlsFor(String host) {
        if (!tlsClient()) return false;
        List<? extends String> hosts;
        try { hosts = TLS_CLIENT_HOSTS.get(); } catch (RuntimeException e) { hosts = List.of(); }
        if (hosts.isEmpty()) return true;
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        for (String x : hosts) if (x.trim().toLowerCase(Locale.ROOT).equals(h)) return true;
        return false;
    }
}

package faygolover.flnettweaks.client;

import de.keksuccino.fancymenu.customization.action.Action;
import de.keksuccino.fancymenu.customization.action.ActionRegistry;
import de.keksuccino.fancymenu.customization.placeholder.DeserializedPlaceholderString;
import de.keksuccino.fancymenu.customization.placeholder.Placeholder;
import de.keksuccino.fancymenu.customization.placeholder.PlaceholderRegistry;
import faygolover.flnettweaks.FlnConfig;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Подробности входа как плейсхолдеры FancyMenu — их показывает меню-терминал сборки.
 * Класс загружается только если FancyMenu установлен (см. ClientEvents).
 * flnet_head — этап, flnet_body (значение line = 0, 1, …) — остальные строки по одной, flnet_wait — «Ждём: …»
 * (пусто, пока не нужно). Строки по номеру: FancyMenu переносит %n% до подстановки плейсхолдеров.
 * flnet_reconnect — отсчёт до переподключения на экране отключения; действие flnet_reconnect_now — подключиться сейчас.
 * flnet_tls — «ВКЛ»/«ВЫКЛ» (tls.client); действие flnet_tls_toggle (on / off / toggle) — переключить до подключения.
 */
final class FancyMenuCompat {
    private FancyMenuCompat() {}

    static void register() {
        PlaceholderRegistry.register(new Simple("flnet_head", "Этап входа и время", () -> ClientEvents.snapshot().head()));
        PlaceholderRegistry.register(new Body());
        PlaceholderRegistry.register(new Simple("flnet_wait", "Чего ждём, если этап затянулся", () -> ClientEvents.snapshot().waiting()));
        PlaceholderRegistry.register(new Simple("flnet_reconnect", "Экран отключения: отсчёт до переподключения после обрыва (пусто — его нет)",
                () -> ClientNet.reconnectLine().toUpperCase(java.util.Locale.ROOT)));
        PlaceholderRegistry.register(new Simple("flnet_tls", "TLS при подключении к серверам: ВКЛ / ВЫКЛ",
                () -> FlnConfig.tlsClient() ? "ВКЛ" : "ВЫКЛ"));
        ActionRegistry.register(new ReconnectNow());
        ActionRegistry.register(new TlsToggle());
    }

    /** Кнопка в меню серверов: TLS вкл/выкл (значение on, off или toggle), сразу пишется в конфиг. */
    private static final class TlsToggle extends Action {
        TlsToggle() { super("flnet_tls_toggle"); }

        @Override public boolean hasValue() { return true; }

        @Override
        public void execute(String value) {
            String v = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
            FlnConfig.setTlsClient(v.equals("on") || (!v.equals("off") && !FlnConfig.tlsClient()));
        }

        @Override public Component getDisplayName() { return Component.literal("flnettweaks: TLS вкл/выкл"); }
        @Override public Component getDescription() { return Component.literal("Подключаться к серверам через TLS: on, off или toggle (переключить)"); }
        @Override public Component getValueDisplayName() { return Component.literal("on / off / toggle"); }
        @Override public String getValuePreset() { return "toggle"; }
    }

    /** Кнопка в меню: переподключиться сейчас, не дожидаясь отсчёта. */
    private static final class ReconnectNow extends Action {
        ReconnectNow() { super("flnet_reconnect_now"); }

        @Override public boolean hasValue() { return false; }
        @Override public void execute(String value) { ClientNet.reconnectNow(); }
        @Override public Component getDisplayName() { return Component.literal("flnettweaks: переподключиться сейчас"); }
        @Override public Component getDescription() { return Component.literal("На экране отключения после обрыва связи — не ждать отсчёт"); }
        @Override public Component getValueDisplayName() { return Component.empty(); }
        @Override public String getValuePreset() { return null; }
    }

    /** flnet_body: строка с номером line (0 — трафик, дальше последний пакет, реестры, чанки); нет такой — пусто. */
    private static final class Body extends Simple {
        Body() { super("flnet_body", "Строка подробностей по номеру (значение line: 0, 1, …)", () -> ""); }

        @Override
        public String getReplacementFor(DeserializedPlaceholderString dps) {
            try {
                int n = Integer.parseInt(dps.values.getOrDefault("line", "0").trim());
                List<String> body = ClientEvents.snapshot().body();
                return n >= 0 && n < body.size() ? body.get(n) : "";
            } catch (RuntimeException e) {
                return "";
            }
        }

        @Override
        public List<String> getValueNames() { return List.of("line"); }

        @Override
        public DeserializedPlaceholderString getDefaultPlaceholderString() {
            DeserializedPlaceholderString d = new DeserializedPlaceholderString(getIdentifier(), null, "");
            d.values.put("line", "0");
            return d;
        }
    }

    private static class Simple extends Placeholder {
        private final String desc;
        private final Supplier<String> value;

        Simple(String id, String desc, Supplier<String> value) {
            super(id);
            this.desc = desc;
            this.value = value;
        }

        @Override
        public String getReplacementFor(DeserializedPlaceholderString dps) {
            try {
                return value.get();
            } catch (RuntimeException e) {
                return "";
            }
        }

        @Override
        public List<String> getValueNames() { return null; }

        @Override
        public String getDisplayName() { return "flnettweaks: " + getIdentifier(); }

        @Override
        public List<String> getDescription() { return List.of(desc); }

        @Override
        public String getCategory() { return "flnettweaks"; }

        @Override
        public DeserializedPlaceholderString getDefaultPlaceholderString() {
            return new DeserializedPlaceholderString(getIdentifier(), null, "");
        }
    }
}

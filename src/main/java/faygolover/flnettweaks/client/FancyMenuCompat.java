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
 * Окно настроек соединения (custom GUI dept_net): flnet_opt (name = rate / patience / compress / tls) — значение;
 * действие flnet_opt («rate», «rate:1000», «patience:long», «compress:toggle»…); flnet_test (line = 0…3) — ход и итог
 * проверки связи, flnet_test_state — idle / running / done / failed / advice (есть совет); действия flnet_test — начать,
 * flnet_test_apply — применить совет. flnet_hint — подсказка на экране отключения (пусто — её нет).
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
        PlaceholderRegistry.register(new Valued("flnet_opt", "name", "rate", "Настройка соединения: name = rate / patience / compress / tls",
                v -> NetOpts.value(v).toUpperCase(java.util.Locale.ROOT)));
        PlaceholderRegistry.register(new Valued("flnet_test", "line", "0", "Проверка связи: строка line = 0 (сервер или ход), 1–3 (итог)",
                v -> { try { return NetTest.line(Integer.parseInt(v.trim())).toUpperCase(java.util.Locale.ROOT); } catch (RuntimeException e) { return ""; } }));
        PlaceholderRegistry.register(new Simple("flnet_test_state", "Проверка связи: idle / running / done / failed / advice (готов совет)", () -> {
            NetTest.State st = NetTest.state;
            if (st == NetTest.State.DONE && NetTest.advice != null && !NetTest.advice.isDefault()) return "advice";
            return st.name().toLowerCase(java.util.Locale.ROOT);
        }));
        PlaceholderRegistry.register(new Simple("flnet_hint", "Экран отключения: подсказка про ограничение скорости (пусто — её нет)",
                () -> ClientNet.hintLine().toUpperCase(java.util.Locale.ROOT)));
        ActionRegistry.register(new ReconnectNow());
        ActionRegistry.register(new TlsToggle());
        ActionRegistry.register(new Run("flnet_opt", true, "flnettweaks: настройка соединения", "rate / patience / compress / tls, можно с :значением", "rate", NetOpts::cycle));
        ActionRegistry.register(new Run("flnet_test", false, "flnettweaks: проверить связь", "Пинг и пробная загрузка с сервера", null, v -> NetTest.start()));
        ActionRegistry.register(new Run("flnet_test_apply", false, "flnettweaks: применить совет проверки", "Записать советованные настройки соединения", null, v -> NetTest.applyAdvice()));
        ActionRegistry.register(new Run("flnet_netopts", false, "flnettweaks: окно настроек соединения", "Обычное окно (без меню-терминала)", null,
                v -> net.minecraft.client.Minecraft.getInstance().setScreen(new NetOptionsScreen(net.minecraft.client.Minecraft.getInstance().screen))));
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

    /** Выполнить действие FancyMenu по имени (автотест стенда). */
    static void run(String action, String value) {
        Action a = ActionRegistry.getAction(action);
        if (a != null) a.execute(value);
    }

    /** Действие с кодом. */
    private static final class Run extends Action {
        private final boolean hasValue;
        private final String name, desc, preset;
        private final java.util.function.Consumer<String> run;

        Run(String id, boolean hasValue, String name, String desc, String preset, java.util.function.Consumer<String> run) {
            super(id);
            this.hasValue = hasValue; this.name = name; this.desc = desc; this.preset = preset; this.run = run;
        }

        @Override public boolean hasValue() { return hasValue; }
        @Override public void execute(String value) { run.accept(value); }
        @Override public Component getDisplayName() { return Component.literal(name); }
        @Override public Component getDescription() { return Component.literal(desc); }
        @Override public Component getValueDisplayName() { return hasValue ? Component.literal("значение") : Component.empty(); }
        @Override public String getValuePreset() { return preset; }
    }

    /** Плейсхолдер с одним значением. */
    private static final class Valued extends Simple {
        private final String key, def;
        private final java.util.function.Function<String, String> f;

        Valued(String id, String key, String def, String desc, java.util.function.Function<String, String> f) {
            super(id, desc, () -> "");
            this.key = key; this.def = def; this.f = f;
        }

        @Override
        public String getReplacementFor(DeserializedPlaceholderString dps) {
            try { return f.apply(dps.values.getOrDefault(key, def)); } catch (RuntimeException e) { return ""; }
        }

        @Override public List<String> getValueNames() { return List.of(key); }

        @Override
        public DeserializedPlaceholderString getDefaultPlaceholderString() {
            DeserializedPlaceholderString d = new DeserializedPlaceholderString(getIdentifier(), null, "");
            d.values.put(key, def);
            return d;
        }
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

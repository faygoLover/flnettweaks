package faygolover.flnettweaks.client;

import de.keksuccino.fancymenu.customization.placeholder.DeserializedPlaceholderString;
import de.keksuccino.fancymenu.customization.placeholder.Placeholder;
import de.keksuccino.fancymenu.customization.placeholder.PlaceholderRegistry;

import java.util.List;
import java.util.function.Supplier;

/**
 * Подробности входа как плейсхолдеры FancyMenu — их показывает меню-терминал сборки.
 * Класс загружается только если FancyMenu установлен (см. ClientEvents).
 * flnet_head — этап, flnet_body (значение line = 0, 1, …) — остальные строки по одной, flnet_wait — «Ждём: …»
 * (пусто, пока не нужно). Строки по номеру: FancyMenu переносит %n% до подстановки плейсхолдеров.
 */
final class FancyMenuCompat {
    private FancyMenuCompat() {}

    static void register() {
        PlaceholderRegistry.register(new Simple("flnet_head", "Этап входа и время", () -> ClientEvents.snapshot().head()));
        PlaceholderRegistry.register(new Body());
        PlaceholderRegistry.register(new Simple("flnet_wait", "Чего ждём, если этап затянулся", () -> ClientEvents.snapshot().waiting()));
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

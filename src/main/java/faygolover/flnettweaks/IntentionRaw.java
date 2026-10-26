package faygolover.flnettweaks;

/** ClientIntentionPacket на сервере: адрес целиком, как пришёл (с полями после \0) — там настройки игрока (NetPrefs). */
public interface IntentionRaw {
    String fln$raw();
}

# faygoLover's Network Tweaks — указатель кода

Создано `python _family/tools/mapgen.py` 15.11.2026, версия 1.2.2-1.20.1-forge, коммит `224893d`.
**Руками не править**: пересоздаётся скриптом. Смысл, потоки работы и рецепты — в `docs/MAP.md`.
Разбор по шаблонам, без компиляции: необычно оформленная регистрация может отсутствовать в списках.

Всего: 61 файлов, 4431 строк Java.

## 1. Пакеты

| Пакет | Файлов | Строк |
|---|---:|---:|
| `faygolover.flnettweaks` | 14 | 1784 |
| `faygolover.flnettweaks.client` | 10 | 1460 |
| `faygolover.flnettweaks.mixin` | 24 | 898 |
| `faygolover.flnettweaks.mixin.client` | 13 | 289 |

## 2. Классы

### `faygolover.flnettweaks`
- `ConnLog`, 243 — Журнал соединений — отдельный файл logs/flnettweaks-connections.log.
- `DebugTools`, 79 — Только для проверки мода (-Dflnettweaks.debug=true): большие пакеты и медленный вход.
- `FlnCommands`, 174 — /flnet stats [in|out] | big | who | limits | reset — сервер, права 2.
- `FlnConfig`, 187 — Конфиг config/flnettweaks-common.toml.
- `FlnTweaks`, 63 — *без описания*
- `IntentionRaw` (interface), 7 — ClientIntentionPacket на сервере: адрес целиком, как пришёл (с полями после \0) — там настройки игрока (NetPrefs).
- `JoinLog`, 158 — Сервер: хронология входа игрока по этапам.
- `MsgTypes`, 95 — Разбивка статистики внутри одного ключа: сообщения SimpleChannel по классу (первый байт данных — номер сообщения), атрибуты и их модификаторы, тип BlockEntity, звук.
- `Net`, 162 — Общие помощники: размеры, имена модов, описание пакетов и понятные причины ошибок.
- `NetPrefs`, 150 — Настройки соединения игрока («Расширенные настройки соединения»): у каждого свои, хранятся у игрока (раздел [player] конфига) и уходят серверу в первом же пакете подключения — хвостом к адресу в ClientIntentionPacket (адрес\0FML3\…
- `NetStats`, 107 — Счётчики трафика: по соединению, по каналам/пакетам в целом и журнал больших пакетов.
- `Probe`, 104 — Проверка связи на порту сервера (кнопка в окне настроек соединения).
- `ServerStall`, 38 — Паузы самого сервера (большая вставка WorldEdit, сохранение): пока сервер стоит, ответы игроков на keepalive лежат в очереди, и после паузы ваниль выкинула бы тех, чей keepalive был в пути.
- `Tls`, 217 — TLS-обёртка соединения.

### `faygolover.flnettweaks.client`
- `ClientEvents`, 253 — Клиент: подробности на экранах входа, хронология входа в лог, команда /flnetc.
- `ClientNet`, 263 — Клиент и обрывы связи: «Сервер не отвечает» в строке над хотбаром, само-переподключение после обрыва сети (не после кика) с отсчётом на экране отключения (в сборке его оформляет FancyMenu — плейсхолдер flnet_reconnect), свой журна…
- `ConnCause`, 68 — Клиент: почему закрылось соединение с сервером.
- `FancyMenuCompat`, 201 — Подробности входа как плейсхолдеры FancyMenu — их показывает меню-терминал сборки.
- `Heartbeat`, 99 — Плавный вход.
- `JoinTracker`, 139 — Клиент: что сейчас происходит при входе на сервер.
- `NetAutotest`, 78 — Проверка окна настроек соединения на стенде (-Dflnettweaks.debug.nettest=true): экран серверов → окно (FancyMenu dept_net или обычное) → проверка связи → скриншоты → экран отключения с подсказкой → вход с настройками из -Dflnettwe…
- `NetOptionsScreen`, 71 — «Настройки соединения» без меню-терминала FancyMenu (с ним то же окно — custom GUI dept_net из Menu Concepts/fm/gen.py, на плейсхолдерах flnet_opt / flnet_test и действиях flnet_opt / flnet_test / flnet_test_apply).
- `NetOpts`, 65 — Общее для окна настроек соединения: переключатели и тексты (FancyMenu и обычный экран).
- `NetTest`, 223 — Проверка связи из окна настроек соединения: пинг (6 раз) и пробная загрузка с сервера (до 8 МБ или 7 с) — тем же путём, что и вход (TLS, если он включён для этого адреса).

### `faygolover.flnettweaks.mixin`
- `ClientChannelInitMixin`, 26 — Подключение к серверу: тайм-аут тишины (ваниль 30 с) и TLS-обёртка (tls.client).
- `ClientIntentionMixin`, 47 — Первый пакет подключения: игрок дописывает к адресу свои настройки соединения (адрес\0FML3\0flnet1:…), сервер запоминает адрес целиком.
- `ClientboundPayloadAccess` (interface), 13 — Данные пакета мода без копии (getData() копирует весь буфер) — для разбивки статистики по типу сообщения.
- `ClientboundPayloadMixin`, 17 — Пакет мода от сервера игроку (ваниль 1 МБ) — и при создании на сервере, и при чтении у игрока.
- `CompressionDecoderMixin`, 17 — Предел размера пакета после распаковки (ваниль 8 МБ).
- `CompressionEncoderAccess` (interface), 14 — Сильное сжатие у игрока: уровень Deflater этого соединения.
- `CompressionEncoderMixin`, 18 — Forge пишет в лог полный дамп пакета больше 8 МБ — порог по нашему лимиту.
- `ConnectionMixin`, 113 — Ошибки соединения: ваниль пишет их только в debug и показывает «Internal Exception: класс: текст».
- `FrameDecoderMixin`, 51 — Длина кадра до 5 байт (ваниль — 3 байта, т.
- `FramePrependerMixin`, 32 — Отправка кадров больше 2 МБ (до frameKB).
- `FriendlyByteBufMixin`, 17 — Предел NBT внутри пакета (ваниль 2 МБ).
- `HandshakeHandlerMixin`, 76 — Forge-рукопожатие: на сервере — что отправлено и чего ждём; у игрока — прогресс для экрана входа.
- `IndexedCodecMixin`, 26 — SimpleChannel: запоминаем, какой класс сообщения стоит за номером (первый байт данных) в каждом канале.
- `LoginAnswerMixin`, 17 — Ответы игрока при входе (ваниль 1 МБ; проверка внутри лямбды конструктора).
- `LoginQueryMixin`, 17 — Пакеты входа от сервера (Forge: список модов, реестры, конфиги; ваниль 1 МБ).
- `PacketDecoderMixin`, 62 — Приём: понятная причина, если пакет не разобрался (какой мод, размер, какой лимит поднять), и счётчики.
- `PacketEncoderMixin`, 40 — Отправка: предел пакета (ваниль 8 МБ) и счётчики.
- `PacketUtilsMixin`, 51 — Сколько пакет обрабатывался в основном потоке: долгие — в лог (кто тормозит вход/игру/сервер).
- `ServerChannelInitMixin`, 27 — Сервер: тайм-аут тишины от игрока (ваниль 30 с, Forge: -Dforge.readTimeout), приём TLS и проверка связи на том же порту.
- `ServerGameListenerMixin`, 88 — Keepalive: проверка раз в 15 с остаётся, но ждать ответ можно keepAliveSeconds; перед отключением — запись, почему.
- `ServerHandshakeMixin`, 28 — Сервер: настройки соединения игрока (NetPrefs) — на его соединение, ещё до входа.
- `ServerLoginListenerMixin`, 71 — Вход на сервер: время на вход (ваниль 30 с) и хронология этапов.
- `ServerboundPayloadAccess` (interface), 13 — Данные пакета мода от игрока без копии — для разбивки статистики по типу сообщения.
- `ServerboundPayloadMixin`, 17 — Пакет мода от игрока серверу: ваниль 32 КБ — главная причина отключений сразу после входа.

### `faygolover.flnettweaks.mixin.client`
- `ChatValidationMixin`, 44 — Ошибка проверки подписи чата не выкидывает с сервера: сообщение показывается, в логе — запись.
- `ChunkSectionMixin`, 29 — Обрезанные данные чанка от сервера: недостающие секции остаются пустыми вместо вылета.
- `ConnectScreenAccessor` (interface), 14 — Есть ли уже TCP-соединение (null — сервер ещё не ответил на подключение).
- `ConnectScreenMixin`, 22 — Куда подключаемся — чтобы после обрыва переподключиться туда же.
- `CreativeTabHeartbeatMixin`, 18 — Плавный вход: сборка вкладок предметов (список JEI) — точка для кадра загрузки.
- `DisconnectedScreenAccessor` (interface), 14 — Причина отключения — для экрана переподключения.
- `IngredientHeartbeatMixin`, 19 — Плавный вход: ингредиенты рецептов (рецепты JEI) — точка для кадра загрузки.
- `ItemStackHeartbeatMixin`, 20 — Плавный вход: подсказки всех предметов (поиск JEI) — точка для кадра загрузки.
- `JeiFilterHeartbeatMixin`, 19 — Плавный вход: JEI строит поиск — по предмету за раз (без JEI миксин не применяется).
- `JeiHeartbeatMixin`, 19 — Плавный вход: шаги запуска JEI (без JEI миксин не применяется).
- `JeiPluginHeartbeatMixin`, 20 — Плавный вход: JEI вызывает плагин мода (без JEI миксин не применяется).
- `JoinMultiplayerScreenAccessor` (interface), 13 — Выбранный в списке сервер — для проверки связи.
- `ServerNameResolverMixin`, 38 — Адрес сервера введён как IP (у нас HOME_EXTERNAL_IP) — имя хоста = этот же IP, без обратного DNS-запроса.

Классов без описания: 1 из 61.

## 3. Подписки на события

| Событие | Кто слушает |
|---|---|
| `ClientPlayerNetworkEvent.LoggingOut` | `ClientNet.onLogout` |
| `PlayerEvent.PlayerLoggedInEvent` | `FlnTweaks.onLoggedIn` |
| `RegisterClientCommandsEvent` | `ClientEvents.onCommands` |
| `RegisterCommandsEvent` | `FlnTweaks.onCommands` |
| `ScreenEvent.Init.Post` | `ClientEvents.onInit`, `ClientNet.onInit` |
| `ScreenEvent.Opening` | `ClientEvents.onOpen`, `ClientNet.onOpen`, `NetTest.onOpen` |
| `ScreenEvent.Render.Post` | `ClientEvents.onRender`, `ClientNet.onRender` |
| `ServerAboutToStartEvent` | `FlnTweaks.onServerStarting` |
| `TickEvent.ClientTickEvent` | `ClientEvents.onTick`, `ClientNet.onTick`, `NetAutotest.tick` |
| `TickEvent.ServerTickEvent` | `FlnTweaks.onServerTick`, `ServerStall.onTick` |
| `net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent` | `ClientEvents.onConstruct` |

## 4. Сеть

Канал: `faygolover/flnettweaks/DebugTools.java`, имя `test`, версия протокола `не объявлена константой`.
- пакеты регистрируются через свой помощник, см. файл
Канал: `faygolover/flnettweaks/FlnTweaks.java`, имя `main`, версия протокола `не объявлена константой`.
- пакеты регистрируются через свой помощник, см. файл

Классы с обработчиками пакетов (2): `DebugTools`, `HandshakeHandlerMixin`.

## 5. Команды

- `faygolover/flnettweaks/DebugTools.java`: `test`
- `faygolover/flnettweaks/FlnCommands.java`: `flnet` `stats` `in` `out` `big` `who` `limits` `reset`
- `faygolover/flnettweaks/client/ClientEvents.java`: `flnetc` `test` `stats` `in` `out` `big` `limits`

## 6. Данные

- свои файлы на диске: `ConnLog`
- свои файлы на диске: `Tls`

## 7. Конфиги

- common: `FlnConfig`, вызовов define 34

## 8. Что берёт из RP Core и что ему даёт

Не использует.

## 9. Связи с другими модами

Проверки `isLoaded`: `fancymenu` (ClientEvents).

Миксины (37):
- `ClientboundPayloadAccess` (mixins) → ClientboundCustomPayloadPacket.class
- `ClientIntentionMixin` (mixins) → ClientIntentionPacket.class
- `CompressionEncoderAccess` (mixins) → CompressionEncoder.class
- `ClientboundPayloadMixin` (mixins) → ClientboundCustomPayloadPacket.class
- `IndexedCodecMixin` (mixins) → IndexedMessageCodec.class, remap = false
- `ServerboundPayloadAccess` (mixins) → ServerboundCustomPayloadPacket.class
- `ClientChannelInitMixin` (mixins) → targets = "net.minecraft.network.Connection$1"
- `CompressionDecoderMixin` (mixins) → CompressionDecoder.class
- `CompressionEncoderMixin` (mixins) → CompressionEncoder.class
- `ConnectionMixin` (mixins) → Connection.class
- `FrameDecoderMixin` (mixins) → Varint21FrameDecoder.class
- `FramePrependerMixin` (mixins) → Varint21LengthFieldPrepender.class
- `FriendlyByteBufMixin` (mixins) → FriendlyByteBuf.class
- `HandshakeHandlerMixin` (mixins) → HandshakeHandler.class, remap = false
- `LoginAnswerMixin` (mixins) → ServerboundCustomQueryPacket.class
- `LoginQueryMixin` (mixins) → ClientboundCustomQueryPacket.class
- `PacketDecoderMixin` (mixins) → PacketDecoder.class
- `PacketEncoderMixin` (mixins) → PacketEncoder.class
- `PacketUtilsMixin` (mixins) → PacketUtils.class
- `ServerboundPayloadMixin` (mixins) → ServerboundCustomPayloadPacket.class
- `ServerChannelInitMixin` (mixins) → targets = "net.minecraft.server.network.ServerConnectionListener$1"
- `ServerGameListenerMixin` (mixins) → ServerGamePacketListenerImpl.class
- `ServerHandshakeMixin` (mixins) → ServerHandshakePacketListenerImpl.class
- `ServerLoginListenerMixin` (mixins) → ServerLoginPacketListenerImpl.class
- `client.ChatValidationMixin` (client) → ClientPacketListener.class
- `client.ChunkSectionMixin` (client) → LevelChunk.class
- `client.ConnectScreenAccessor` (client) → ConnectScreen.class
- `client.ConnectScreenMixin` (client) → ConnectScreen.class
- `client.CreativeTabHeartbeatMixin` (client) → CreativeModeTab.class
- `client.DisconnectedScreenAccessor` (client) → DisconnectedScreen.class
- `client.JoinMultiplayerScreenAccessor` (client) → JoinMultiplayerScreen.class
- `client.IngredientHeartbeatMixin` (client) → Ingredient.class
- `client.ItemStackHeartbeatMixin` (client) → ItemStack.class
- `client.JeiFilterHeartbeatMixin` (client) → targets = "mezz.jei.gui.ingredients.IngredientFilter", remap = false
- `client.JeiHeartbeatMixin` (client) → targets = "mezz.jei.core.util.LoggedTimer", remap = false
- `client.JeiPluginHeartbeatMixin` (client) → targets = "mezz.jei.library.load.PluginCallerTimer", remap = false
- `client.ServerNameResolverMixin` (client) → ServerNameResolver.class

## 10. Зарегистрированные id

Нет или регистрируются не строковыми литералами.

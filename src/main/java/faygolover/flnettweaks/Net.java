package faygolover.flnettweaks;

import io.netty.channel.Channel;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.ICustomPacket;

import java.util.Locale;

/** Общие помощники: размеры, имена модов, описание пакетов и понятные причины ошибок. */
public final class Net {
    private Net() {}

    public static final String CONFIG = "config/flnettweaks-common.toml";

    /** Ошибка сети с понятным текстом: toString без имени класса — игрок видит «Internal Exception: <текст>». */
    public static final class Explained extends DecoderException {
        public Explained(String message, Throwable cause) { super(message, cause); }
        @Override public String toString() { return getMessage(); }
    }

    public static String size(long b) {
        if (b < 1024) return b + " Б";
        if (b < 1024 * 1024) return String.format(Locale.ROOT, "%.1f КБ", b / 1024.0);
        return String.format(Locale.ROOT, "%.1f МБ", b / 1048576.0);
    }

    public static String mod(String namespace) {
        if (namespace.equals("fml") || namespace.equals("forge")) return "Forge";
        if (namespace.equals("minecraft")) return "Minecraft";
        try {
            return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
        } catch (RuntimeException e) {
            return namespace;
        }
    }

    public static String channel(ResourceLocation id) {
        return id == null ? "?" : id + " (мод " + mod(id.getNamespace()) + ")";
    }

    private static final ResourceLocation LOGIN_WRAPPER = new ResourceLocation("fml", "loginwrapper");

    /** Ключ для статистики: канал мода (для входа Forge — канал внутри fml:loginwrapper) или имя класса пакета. */
    public static String key(Packet<?> p) {
        if (p instanceof ICustomPacket<?> cp) {
            try {
                ResourceLocation n = cp.getName();
                if (LOGIN_WRAPPER.equals(n)) {
                    ResourceLocation inner = wrapped(cp.getInternalData());
                    if (inner != null) return inner.toString();
                }
                if (n != null) return n.toString();
            } catch (RuntimeException ignored) {}
        }
        return p.getClass().getSimpleName();
    }

    /** Канал, завёрнутый в fml:loginwrapper (не сдвигая чтение). */
    public static ResourceLocation wrapped(FriendlyByteBuf data) {
        if (data == null || !data.isReadable()) return null;
        try {
            return new FriendlyByteBuf(data.duplicate()).readResourceLocation();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String describe(Packet<?> p) {
        String k = key(p);
        return k.indexOf(':') > 0 ? "канал " + channel(new ResourceLocation(k)) : k;
    }

    public static String dir(PacketFlow receiving) {
        return receiving == PacketFlow.SERVERBOUND ? "игрок → сервер" : "сервер → игрок";
    }

    /** Кто на том конце канала: имя игрока (на сервере) или адрес. */
    public static String who(Channel ch) {
        try {
            if (ch.pipeline().get("packet_handler") instanceof Connection c) {
                if (c.getPacketListener() instanceof ServerGamePacketListenerImpl g) return g.player.getGameProfile().getName();
                if (c.getPacketListener() instanceof ServerLoginPacketListenerImpl l) return l.getUserName();
                if (c.getReceiving() == PacketFlow.CLIENTBOUND) return "сервер";
            }
        } catch (RuntimeException ignored) {}
        return String.valueOf(ch.remoteAddress());
    }

    // ---------- понятные причины ----------

    /** Пакет не разобрался при приёме: дописываем, что это за пакет, какой мод, размер, какой лимит поднять. */
    public static RuntimeException decodeFailure(RuntimeException e, ConnectionProtocol proto, PacketFlow flow, int id, FriendlyByteBuf buf,
                                                 int start, int frameSize) {
        if (e instanceof Explained) return e;
        String msg = String.valueOf(e.getMessage());
        String dir = dir(flow);
        if (msg.contains("Payload may not be larger")) {
            ResourceLocation ch = peekChannel(proto, flow, buf, start);
            String key; int limit;
            if (proto == ConnectionProtocol.LOGIN) { key = "loginPayloadKB"; limit = FlnConfig.loginPayload(); }
            else if (flow == PacketFlow.SERVERBOUND) { key = "serverboundPayloadKB"; limit = FlnConfig.serverboundPayload(); }
            else { key = "clientboundPayloadKB"; limit = FlnConfig.clientboundPayload(); }
            return new Explained(String.format("flnettweaks: пакет мода %s (%s) — %s, больше лимита %s. Поднимите limits.%s в %s на сервере и у игроков",
                    ch == null ? "?" : channel(ch), dir, size(frameSize), size(limit), key, CONFIG), e);
        }
        if (msg.contains("NBT tag that was too big")) {
            return new Explained(String.format("flnettweaks: NBT в пакете %s/%d (%s, %s) больше лимита %s. Поднимите limits.nbtKB в %s",
                    proto.name(), id, dir, size(frameSize), size(FlnConfig.nbt()), CONFIG), e);
        }
        ResourceLocation ch = peekChannel(proto, flow, buf, start);
        return new Explained(String.format("flnettweaks: не удалось прочитать пакет %s/%d%s (%s, %s): %s",
                proto.name(), id, ch == null ? "" : " канала " + channel(ch), dir, size(frameSize), msg), e);
    }

    /** Для пакетов модов — имя канала в начале данных (не сдвигая чтение для остальных). */
    private static ResourceLocation peekChannel(ConnectionProtocol proto, PacketFlow flow, FriendlyByteBuf buf, int start) {
        int save = buf.readerIndex();
        try {
            buf.readerIndex(start);
            if (proto == ConnectionProtocol.LOGIN) {
                if (flow != PacketFlow.CLIENTBOUND) return null;
                buf.readVarInt(); // transaction id
            }
            String s = buf.readUtf(32767);
            return ResourceLocation.tryParse(s);
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            buf.readerIndex(save);
        }
    }

    /** Ошибка соединения перед отключением: понятный текст для известных случаев. */
    public static Throwable explain(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof Explained) return c;
            String m = String.valueOf(c.getMessage());
            if (m.startsWith("Badly compressed packet") && m.contains("larger than protocol maximum"))
                return new Explained("flnettweaks: пакет после распаковки больше лимита " + size(FlnConfig.packet())
                        + " (" + m + "). Поднимите limits.packetKB в " + CONFIG + " на сервере и у игроков", t);
            if (m.startsWith("Packet too big"))
                return new Explained("flnettweaks: отправляемый пакет больше лимита " + size(FlnConfig.packet())
                        + " (" + m + "). Поднимите limits.packetKB в " + CONFIG, t);
            if (m.contains("Tried to read NBT tag that was too big"))
                return new Explained("flnettweaks: NBT больше лимита " + size(FlnConfig.nbt()) + ". Поднимите limits.nbtKB в " + CONFIG, t);
        }
        return t;
    }
}

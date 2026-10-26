package faygolover.flnettweaks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import faygolover.flnettweaks.mixin.ClientboundPayloadAccess;
import faygolover.flnettweaks.mixin.ServerboundPayloadAccess;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Разбивка статистики внутри одного ключа: сообщения SimpleChannel по классу (первый байт данных — номер сообщения),
 * атрибуты и их модификаторы, тип BlockEntity, звук.
 */
public final class MsgTypes {
    private MsgTypes() {}

    /** Канал → имена классов по номеру сообщения (0..255). */
    private static final Map<ResourceLocation, String[]> TYPES = new ConcurrentHashMap<>();

    public static void put(ResourceLocation channel, int index, Class<?> type) {
        if (channel == null || type == null) return;
        String n = type.getName();
        n = n.substring(n.lastIndexOf('.') + 1);
        TYPES.computeIfAbsent(channel, k -> new String[256])[index & 0xff] = n;
    }

    /** Куда отнести часть пакета: имя и доля размера. */
    public interface Sink { void add(String sub, int bytes); }

    public static void split(Packet<?> p, int size, Sink out) {
        try {
            if (p instanceof ClientboundCustomPayloadPacket c) payload(c.getIdentifier(), ((ClientboundPayloadAccess) c).fln$data(), size, out);
            else if (p instanceof ServerboundCustomPayloadPacket c) payload(c.getIdentifier(), ((ServerboundPayloadAccess) c).fln$data(), size, out);
            else if (p instanceof ClientboundUpdateAttributesPacket a) attributes(a, size, out);
            else if (p instanceof ClientboundBlockEntityDataPacket b) out.add(String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(b.getType())), size);
            else if (p instanceof ClientboundSoundPacket s) out.add(s.getSound().unwrapKey().map(k -> k.location().toString()).orElseGet(() -> s.getSound().value().getLocation().toString()), size);
            else if (p instanceof ClientboundSoundEntityPacket s) out.add(s.getSound().unwrapKey().map(k -> k.location().toString()).orElseGet(() -> s.getSound().value().getLocation().toString()), size);
            else if (p instanceof ClientboundStopSoundPacket s) out.add(s.getName() == null ? "(все)" : s.getName().toString(), size);
        } catch (RuntimeException ignored) {}
    }

    private static void payload(ResourceLocation ch, FriendlyByteBuf data, int size, Sink out) {
        if (data == null || !data.isReadable()) return;
        String[] names = TYPES.get(ch);
        if (names == null) {   // свой канал мода (CraftedCore, Architectury…): часто в начале — id сообщения
            String id = leadingId(data);
            if (id != null) out.add(id, size);
            return;
        }
        int i = data.getUnsignedByte(data.readerIndex());
        String n = names[i];
        out.add(n != null ? n : "#" + i, size);
    }

    /** Строка «namespace:path» в начале данных (VarInt-длина + UTF-8), не сдвигая чтение; нет — null. */
    private static String leadingId(FriendlyByteBuf data) {
        int i = data.readerIndex(), end = data.writerIndex(), len = 0;
        for (int k = 0; k < 2; k++) {
            if (i >= end) return null;
            int b = data.getUnsignedByte(i++);
            len |= (b & 0x7F) << (7 * k);
            if ((b & 0x80) == 0) break;
            if (k == 1) return null;
        }
        if (len < 3 || len > 128 || i + len > end) return null;
        String s = data.toString(i, len, java.nio.charset.StandardCharsets.UTF_8);
        return s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? s : null;
    }

    private static void attributes(ClientboundUpdateAttributesPacket a, int size, Sink out) {
        var vals = a.getValues();
        if (vals.isEmpty()) return;
        int share = size / vals.size();
        int mods = 0;
        for (var v : vals) mods += v.getModifiers().size();
        int modShare = mods == 0 ? 0 : size / mods;
        for (var v : vals) {
            out.add("атрибут " + BuiltInRegistries.ATTRIBUTE.getKey(v.getAttribute()), share);
            for (AttributeModifier m : v.getModifiers())
                out.add("модификатор «" + m.getName() + "» " + m.getId().toString().substring(0, 8) + " (" + BuiltInRegistries.ATTRIBUTE.getKey(v.getAttribute()).getPath() + ")", modShare);
        }
    }
}

package faygolover.flnettweaks;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Счётчики трафика: по соединению, по каналам/пакетам в целом и журнал больших пакетов. */
public final class NetStats {
    private NetStats() {}

    public static final AttributeKey<Conn> KEY = AttributeKey.valueOf("flnettweaks:stats");
    public static volatile long since = System.currentTimeMillis();

    /** Счётчики одного соединения (пишет поток netty этого соединения). */
    public static final class Conn {
        public final long created = System.currentTimeMillis();
        public volatile long inPackets, inBytes, outPackets, outBytes;
        public volatile String lastIn = "—", lastOut = "—";
        public volatile int lastInSize, lastOutSize;
        public volatile long lastInAt, lastOutAt;
        /** Кадр, который сейчас принимается по частям: полный размер и сколько уже пришло. */
        public volatile int pendingFrame, pendingHave;
        public final Map<String, long[]> inBy = new ConcurrentHashMap<>(), outBy = new ConcurrentHashMap<>();
    }

    public static final class Counter {
        public final LongAdder n = new LongAdder(), bytes = new LongAdder();
        public final AtomicLong max = new AtomicLong();
        void add(int size) { n.increment(); bytes.add(size); max.accumulateAndGet(size, Math::max); }
    }

    public record Big(long at, String who, boolean in, String what, int size) {}

    public static final Map<String, Counter> IN = new ConcurrentHashMap<>(), OUT = new ConcurrentHashMap<>();
    private static final ArrayDeque<Big> BIG = new ArrayDeque<>();
    private static final int BIG_KEEP = 64;

    public static Conn of(Channel ch) {
        Attribute<Conn> a = ch.attr(KEY);
        Conn c = a.get();
        if (c == null) {
            Conn n = new Conn();
            c = a.setIfAbsent(n);
            if (c == null) c = n;
        }
        return c;
    }

    public static void in(Channel ch, Packet<?> p, int size) {
        String key = Net.key(p);
        Conn c = of(ch);
        c.inPackets++; c.inBytes += size;
        c.lastIn = key; c.lastInSize = size; c.lastInAt = System.currentTimeMillis();
        long[] v = c.inBy.computeIfAbsent(key, k -> new long[2]); v[0]++; v[1] += size;
        IN.computeIfAbsent(key, k -> new Counter()).add(size);
        if (size >= FlnConfig.bigPacket()) big(ch, true, p, size);
    }

    public static void out(Channel ch, Packet<?> p, int size) {
        String key = Net.key(p);
        Conn c = of(ch);
        c.outPackets++; c.outBytes += size;
        c.lastOut = key; c.lastOutSize = size; c.lastOutAt = System.currentTimeMillis();
        long[] v = c.outBy.computeIfAbsent(key, k -> new long[2]); v[0]++; v[1] += size;
        OUT.computeIfAbsent(key, k -> new Counter()).add(size);
        if (size >= FlnConfig.bigPacket()) big(ch, false, p, size);
    }

    private static void big(Channel ch, boolean in, Packet<?> p, int size) {
        String who = Net.who(ch);
        String what = Net.describe(p);
        Big b = new Big(System.currentTimeMillis(), who, in, what, size);
        synchronized (BIG) {
            BIG.addLast(b);
            while (BIG.size() > BIG_KEEP) BIG.removeFirst();
        }
        FlnTweaks.LOG.info("flnettweaks: большой пакет {} {}: {}, {}", in ? "от" : "для", who.equals("сервер") ? "сервера" : who, what, Net.size(size));
    }

    public static List<Big> big() {
        synchronized (BIG) { return new ArrayList<>(BIG); }
    }

    public static void reset() {
        IN.clear(); OUT.clear();
        synchronized (BIG) { BIG.clear(); }
        since = System.currentTimeMillis();
    }
}

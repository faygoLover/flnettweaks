package faygolover.flnettweaks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Проверка связи на порту сервера (кнопка в окне настроек соединения). Клиент открывает обычное TCP-соединение (с TLS,
 * если он входит через TLS — тот же путь, что и в игру) и шлёт {@code FLNPROBE}, дальше команды: {@code P} — ответ одним
 * байтом (пинг), {@code D}+int — столько байт случайных данных как можно быстрее (до 8 МБ). Minecraft так не начинается:
 * у него первый байт — длина пакета, второй — 0x00. Пробная загрузка — не чаще раза в 10 с с адреса, не больше 3 сразу.
 */
public final class Probe {
    private Probe() {}

    public static final byte[] MAGIC = "FLNPROBE".getBytes(StandardCharsets.US_ASCII);
    public static final int MAX_DOWNLOAD = 8 << 20;
    private static final int CHUNK = 16384;
    private static final byte[] NOISE = new byte[CHUNK];
    static { new Random(85).nextBytes(NOISE); }
    private static final Map<InetAddress, Long> LAST = new ConcurrentHashMap<>();
    private static final AtomicInteger RUNNING = new AtomicInteger();

    /** ServerConnectionListener: в цепочку перед разбором Minecraft (после TLS, если он будет). */
    public static void serverChannel(Channel ch) {
        if (FlnConfig.probeServer()) ch.pipeline().addFirst("fln_probe", new Handler());
    }

    private static final class Handler extends ByteToMessageDecoder {
        private boolean active, downloading;

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (!active) {
                int i = in.readerIndex();
                if (in.getByte(i) != MAGIC[0]) { ctx.pipeline().remove(this); return; }
                if (in.readableBytes() < MAGIC.length) return;
                for (int k = 1; k < MAGIC.length; k++)
                    if (in.getByte(i + k) != MAGIC[k]) { ctx.pipeline().remove(this); return; }
                in.skipBytes(MAGIC.length);
                active = true;
                if (ctx.pipeline().get("timeout") != null) ctx.pipeline().remove("timeout");
                ctx.executor().schedule(() -> { ctx.close(); }, 30, TimeUnit.SECONDS);
            }
            while (in.isReadable() && !downloading) {
                byte cmd = in.getByte(in.readerIndex());
                if (cmd == 'P') {
                    in.skipBytes(1);
                    ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{'p'}));
                } else if (cmd == 'D') {
                    if (in.readableBytes() < 5) return;
                    in.skipBytes(1);
                    int size = Math.max(0, Math.min(MAX_DOWNLOAD, in.readInt()));
                    downloading = true;
                    if (!allowed(ctx.channel())) {
                        ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{'b'})).addListener(f -> ctx.close());
                        return;
                    }
                    ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{'d'}));
                    FlnTweaks.LOG.info("flnettweaks: проверка связи — пробная загрузка {} для {}", Net.size(size), ctx.channel().remoteAddress());
                    send(ctx, size);
                } else {
                    ctx.close();
                    return;
                }
            }
            if (downloading) in.skipBytes(in.readableBytes());
        }

        private static boolean allowed(Channel ch) {
            if (!(ch.remoteAddress() instanceof InetSocketAddress a)) return false;
            long now = System.currentTimeMillis();
            Long last = LAST.get(a.getAddress());
            if (last != null && now - last < 10_000) return false;
            if (RUNNING.incrementAndGet() > 3) { RUNNING.decrementAndGet(); return false; }
            LAST.put(a.getAddress(), now);
            ch.closeFuture().addListener(f -> RUNNING.decrementAndGet());
            if (LAST.size() > 1000) LAST.entrySet().removeIf(e -> now - e.getValue() > 60_000);
            return true;
        }

        private static void send(ChannelHandlerContext ctx, int left) {
            if (left <= 0 || !ctx.channel().isActive()) { ctx.close(); return; }
            int n = Math.min(CHUNK, left);
            ctx.writeAndFlush(Unpooled.wrappedBuffer(NOISE, 0, n)).addListener(f -> {
                if (f.isSuccess()) send(ctx, left - n);
                else ctx.close();
            });
        }
    }
}

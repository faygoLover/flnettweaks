package faygolover.flnettweaks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.AttributeKey;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.net.ssl.KeyManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.TimeZone;

/**
 * TLS-обёртка соединения. Клиент с tls.client заворачивает соединение в TLS с первого байта — снаружи это обычный
 * HTTPS, содержимое не разобрать. Сервер на том же порту смотрит на первые байты: TLS (0x16 0x03) — снимает обёртку,
 * иначе — обычный вход. Сертификат сервера самоподписанный, создаётся сам (config/flnettweaks-tls.p12): цель —
 * спрятать трафик от фильтров, а не доказать подлинность сервера, поэтому клиент сертификат не проверяет.
 */
public final class Tls {
    private Tls() {}

    public static final AttributeKey<Boolean> ON = AttributeKey.valueOf("flnettweaks:tls");
    private static final char[] PASS = "flnettweaks".toCharArray();
    private static final int HANDSHAKE_MS = 30_000;
    private static SslContext server, client;
    private static boolean serverFailed;

    // ---------- сервер ----------

    /** ServerConnectionListener: первым в цепочку — определитель TLS. */
    public static void serverChannel(Channel ch) {
        if (FlnConfig.tlsServer() && serverContext() != null) ch.pipeline().addFirst("fln_tls_detect", new Detector());
    }

    private static final class Detector extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            int i = in.readerIndex();
            if (in.getUnsignedByte(i) == 0x16) {   // TLS handshake record; у Minecraft так начинается только пакет длиной 22, дальше id 0x00
                if (in.readableBytes() < 2) return;
                if (in.getUnsignedByte(i + 1) == 0x03) {
                    SslHandler h = serverContext().newHandler(ctx.alloc());
                    h.setHandshakeTimeoutMillis(HANDSHAKE_MS);
                    ctx.channel().attr(ON).set(true);
                    ctx.pipeline().addAfter(ctx.name(), "fln_tls", h);
                }
            }
            ctx.pipeline().remove(this);   // накопленные байты уходят дальше — в TLS или в обычный разбор
        }
    }

    /** Готовим сертификат заранее (запуск сервера), чтобы первое подключение не ждало. */
    public static void prepareServer() {
        if (FlnConfig.tlsServer()) serverContext();
    }

    private static synchronized SslContext serverContext() {
        if (server != null || serverFailed) return server;
        try {
            Path p = FMLPaths.CONFIGDIR.get().resolve("flnettweaks-tls.p12");
            KeyStore ks = KeyStore.getInstance("PKCS12");
            if (Files.exists(p)) {
                try (InputStream in = Files.newInputStream(p)) { ks.load(in, PASS); }
            } else {
                KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
                g.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair kp = g.generateKeyPair();
                X509Certificate cert = selfSigned(kp, "localhost");
                ks.load(null, PASS);
                ks.setKeyEntry("flnettweaks", kp.getPrivate(), PASS, new Certificate[]{cert});
                try (OutputStream o = Files.newOutputStream(p)) { ks.store(o, PASS); }
                FlnTweaks.LOG.info("flnettweaks: TLS — создан сертификат сервера {}", p);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, PASS);
            server = SslContextBuilder.forServer(kmf).sslProvider(SslProvider.JDK).build();
            Certificate c = ks.getCertificate("flnettweaks");
            FlnTweaks.LOG.info("flnettweaks: TLS-вход включён на порту сервера, сертификат SHA-256 {}",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded())));
        } catch (Exception e) {
            serverFailed = true;
            FlnTweaks.LOG.error("flnettweaks: TLS на сервере не включился — принимаем только обычный вход", e);
        }
        return server;
    }

    // ---------- клиент ----------

    /** Connection.connect: в момент подключения знаем адрес — по нему решаем, нужен ли TLS. */
    public static void clientChannel(Channel ch) {
        if (FlnConfig.tlsClient()) ch.pipeline().addFirst("fln_tls_connect", new ConnectHook());
    }

    private static final class ConnectHook extends ChannelOutboundHandlerAdapter {
        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remote, SocketAddress local, ChannelPromise promise) throws Exception {
            ctx.connect(remote, local, promise);
            if (remote instanceof InetSocketAddress a && FlnConfig.tlsFor(a.getHostString())) {
                String sni = FlnConfig.tlsSni();
                SslHandler h = clientContext().newHandler(ctx.alloc(), sni.isEmpty() ? a.getHostString() : sni, a.getPort());
                h.setHandshakeTimeoutMillis(HANDSHAKE_MS);
                ctx.channel().attr(ON).set(true);
                ctx.pipeline().addAfter(ctx.name(), "fln_tls", h);
                FlnTweaks.LOG.info("flnettweaks: подключение к {}:{} через TLS{}", a.getHostString(), a.getPort(), sni.isEmpty() ? "" : " (SNI " + sni + ")");
            }
            ctx.pipeline().remove(this);
        }
    }

    private static synchronized SslContext clientContext() throws Exception {
        if (client == null) client = SslContextBuilder.forClient().sslProvider(SslProvider.JDK).trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        return client;
    }

    // ---------- самоподписанный сертификат (DER вручную: в Java нет открытого API для этого) ----------

    static X509Certificate selfSigned(KeyPair kp, String cn) throws Exception {
        byte[] algo = seq(oid(1, 2, 840, 10045, 4, 3, 2));   // ecdsa-with-SHA256
        byte[] name = seq(set(seq(oid(2, 5, 4, 3), tlv(0x0C, cn.getBytes(StandardCharsets.UTF_8)))));
        long now = System.currentTimeMillis();
        byte[] validity = seq(utc(now - 86_400_000L), utc(now + 10 * 365 * 86_400_000L));
        byte[] serial = tlv(0x02, new BigInteger(63, new SecureRandom()).add(BigInteger.ONE).toByteArray());
        byte[] tbs = seq(tlv(0xA0, tlv(0x02, new byte[]{2})), serial, algo, name, validity, name, kp.getPublic().getEncoded());
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bits = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bits, 1, sig.length);
        byte[] der = seq(tbs, algo, tlv(0x03, bits));
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    private static byte[] tlv(int tag, byte[] v) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        int n = v.length;
        if (n < 128) o.write(n);
        else if (n < 256) { o.write(0x81); o.write(n); }
        else { o.write(0x82); o.write(n >> 8); o.write(n & 0xFF); }
        o.writeBytes(v);
        return o.toByteArray();
    }

    private static byte[] seq(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return tlv(0x30, o.toByteArray());
    }

    private static byte[] set(byte[] p) {
        return tlv(0x31, p);
    }

    private static byte[] oid(int... arcs) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(arcs[0] * 40 + arcs[1]);
        for (int i = 2; i < arcs.length; i++) {
            int a = arcs[i];
            byte[] tmp = new byte[5];
            int k = tmp.length;
            tmp[--k] = (byte) (a & 0x7F);
            while ((a >>>= 7) != 0) tmp[--k] = (byte) (0x80 | (a & 0x7F));
            o.write(tmp, k, tmp.length - k);
        }
        return tlv(0x06, o.toByteArray());
    }

    private static byte[] utc(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyMMddHHmmss'Z'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(0x17, f.format(new Date(ms)).getBytes(StandardCharsets.US_ASCII));
    }
}

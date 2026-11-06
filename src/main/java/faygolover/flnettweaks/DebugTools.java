package faygolover.flnettweaks;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerNegotiationEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;

import java.util.concurrent.ThreadLocalRandom;

/** Только для проверки мода (-Dflnettweaks.debug=true): большие пакеты и медленный вход. В jar для игроков не идёт. */
public final class DebugTools {
    private DebugTools() {}

    public static final boolean ON = FlnTweaks.DEBUG;
    public static final ResourceLocation TEST = new ResourceLocation(FlnTweaks.MODID, "test");

    public static void init() {
        if (!ON) return;
        // Тестовый канал: сервер пишет, сколько получил, и отвечает пакетом размера из первых 4 байт.
        var ch = NetworkRegistry.ChannelBuilder.named(TEST).networkProtocolVersion(() -> "1")
                .clientAcceptedVersions(v -> true).serverAcceptedVersions(v -> true).eventNetworkChannel();
        ch.addListener((NetworkEvent e) -> {
            if (e.getPayload() == null) return;
            NetworkEvent.Context ctx = e.getSource().get();
            int size = e.getPayload().readableBytes();
            if (ctx.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                int reply = e.getPayload().readInt();
                FlnTweaks.LOG.info("flnettweaks test: сервер получил {} от {}, ответ {} КБ", Net.size(size), ctx.getSender() == null ? "?" : ctx.getSender().getGameProfile().getName(), reply);
                ServerPlayer p = ctx.getSender();
                if (reply > 0 && p != null) p.connection.send(new ClientboundCustomPayloadPacket(TEST, noise(reply, 0)));
            } else {
                FlnTweaks.LOG.info("flnettweaks test: клиент получил {}", Net.size(size));
            }
            ctx.setPacketHandled(true);
        });
        int delay = Integer.getInteger("flnettweaks.debug.joinDelay", 0);
        if (delay > 0) MinecraftForge.EVENT_BUS.addListener((PlayerNegotiationEvent e) -> e.enqueueWork(() -> {
            try { Thread.sleep(delay * 1000L); } catch (InterruptedException ignored) {}
        }));
    }

    /** Несжимаемые данные заданного размера. */
    public static FriendlyByteBuf noise(int kb) {
        return noise(kb, 0);
    }

    /** Первые 4 байта — размер ответа сервера в КБ. */
    public static FriendlyByteBuf noise(int kb, int replyKb) {
        byte[] b = new byte[kb * 1024];
        ThreadLocalRandom.current().nextBytes(b);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(b));
        buf.setInt(0, replyKb);
        return buf;
    }

    /** /flnet test <КБ> — сервер шлёт игроку пакет мода такого размера. */
    public static void command(LiteralArgumentBuilder<CommandSourceStack> root) {
        if (!ON) return;
        root.then(Commands.literal("test").then(Commands.argument("kb", IntegerArgumentType.integer(1, 262144)).executes(c -> {
            int kb = IntegerArgumentType.getInteger(c, "kb");
            ServerPlayer p = c.getSource().getPlayerOrException();
            p.connection.send(new ClientboundCustomPayloadPacket(TEST, noise(kb)));
            c.getSource().sendSuccess(() -> Component.literal("flnettweaks: отправлено " + kb + " КБ"), false);
            return 1;
        })));
    }
}

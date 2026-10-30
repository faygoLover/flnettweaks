package faygolover.flnettweaks;

import com.mojang.logging.LogUtils;
import faygolover.flnettweaks.client.ClientEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import org.slf4j.Logger;

@Mod(FlnTweaks.MODID)
public final class FlnTweaks {
    public static final String MODID = "flnettweaks";
    public static final Logger LOG = LogUtils.getLogger();
    private static final String NET_VERSION = "1";

    public FlnTweaks() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FlnConfig.SPEC, MODID + "-common.toml");
        // Канал нужен только как метка: мод обязателен и на сервере, и у игрока — лимиты больше ванильных
        // работают, только если обе стороны их понимают. Без мода с одной стороны Forge скажет об этом при входе.
        NetworkRegistry.newSimpleChannel(new ResourceLocation(MODID, "main"), () -> NET_VERSION, NET_VERSION::equals, NET_VERSION::equals);
        MinecraftForge.EVENT_BUS.addListener(this::onCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        DebugTools.init();
        if (FMLEnvironment.dist.isClient()) ClientEvents.init();
    }

    private void onCommands(RegisterCommandsEvent e) {
        FlnCommands.register(e.getDispatcher());
    }

    private void onLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.connection != null) {
            JoinLog log = JoinLog.of(sp.connection.connection);
            if (log != null) log.finish("в мире", false);
            ConnLog.serverLogin(sp);
        }
    }

    private void onServerTick(TickEvent.ServerTickEvent e) {
        ServerStall.onTick(e);
        if (e.phase == TickEvent.Phase.END) ConnLog.serverTick(e.getServer());
    }

    private void onServerStarting(ServerAboutToStartEvent e) {
        Tls.prepareServer();
    }
}

package faygolover.flnettweaks.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.FlnTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ForgeHooksClient;
import org.joml.Matrix4f;

/**
 * Плавный вход. При входе на сервер игра обрабатывает тяжёлые пакеты в основном потоке (теги сервера запускают JEI —
 * секунды), окно в это время не рисуется и не отвечает — Windows пишет «Не отвечает». Из мест, которые эта работа
 * проходит часто (шаги JEI, построение поиска, вкладки предметов, подсказки, ингредиенты рецептов), раз в FRAME_MS
 * рисуем кадр текущего экрана загрузки и обрабатываем события окна. Включено только на время обработки пакета при
 * входе (JoinTracker.handling).
 */
public final class Heartbeat {
    private Heartbeat() {}

    private static final long FRAME_NS = 50_000_000L;
    private static long last;
    private static boolean inFrame, broken;
    /** Что сейчас делает игра (шаг JEI) — для строки «Ждём» на экране входа. */
    public static volatile String step;

    /** Шаг JEI (LoggedTimer.start / PluginCallerTimer.begin). */
    public static void step(String s) {
        if (!JoinTracker.handling) return;
        step = s;
        beat();
    }

    public static void beat() {
        if (!JoinTracker.handling || inFrame || broken) return;
        long now = System.nanoTime();
        if (now - last < FRAME_NS) return;
        if (!FlnConfig.smoothJoin() || !RenderSystem.isOnRenderThread()) return;
        last = now;
        inFrame = true;
        try {
            frame();
        } catch (Throwable t) {
            broken = true;   // до перезапуска — без пульса, вход как в ванили
            FlnTweaks.LOG.warn("flnettweaks: кадр во время загрузки не удался, плавный вход выключен до перезапуска", t);
        } finally {
            inFrame = false;
        }
    }

    /** Новая обработка пакета — первый кадр не раньше чем через FRAME_NS. */
    public static void reset() {
        last = System.nanoTime();
        step = null;
    }

    /** Как Minecraft.runTick + GameRenderer.render, только экран (без мира). */
    private static void frame() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return;
        Window w = mc.getWindow();
        RenderTarget target = mc.getMainRenderTarget();
        RenderSystem.clear(16640, Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.clear(256, Minecraft.ON_OSX);
        Matrix4f proj = new Matrix4f().setOrtho(0.0F, (float) (w.getWidth() / w.getGuiScale()), (float) (w.getHeight() / w.getGuiScale()), 0.0F,
                1000.0F, ForgeHooksClient.getGuiFarPlane());
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(proj, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        mv.translate(0.0D, 0.0D, 1000F - ForgeHooksClient.getGuiFarPlane());
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        try {
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            int mx = (int) (mc.mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth());
            int my = (int) (mc.mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight());
            ForgeHooksClient.drawScreen(screen, g, mx, my, mc.getDeltaFrameTime());
            g.flush();
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            target.unbindWrite();
        }
        target.blitToScreen(w.getWidth(), w.getHeight());
        w.updateDisplay();   // смена кадра + события окна: Windows видит, что игра жива
    }
}

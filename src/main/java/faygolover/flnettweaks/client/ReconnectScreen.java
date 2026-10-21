package faygolover.flnettweaks.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Связь оборвалась: причина, отсчёт до переподключения, кнопки «Сейчас» и «Отмена». */
public final class ReconnectScreen extends Screen {
    private final Component reason;
    private final long until;
    private final int attempt, of;
    private List<FormattedCharSequence> lines = List.of();
    private boolean started;

    ReconnectScreen(Component title, Component reason, int delaySeconds, int attempt, int of) {
        super(title);
        this.reason = reason;
        this.until = System.currentTimeMillis() + delaySeconds * 1000L;
        this.attempt = attempt;
        this.of = of;
    }

    @Override
    protected void init() {
        lines = font.split(reason, width - 50);
        int y = Math.min(height / 2 + lines.size() * 5 + 30, height - 50);
        addRenderableWidget(Button.builder(Component.literal("Подключиться сейчас"), b -> go()).bounds(width / 2 - 100, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена — к списку серверов"), b -> ClientNet.cancel()).bounds(width / 2 - 100, y + 24, 200, 20).build());
    }

    private void go() {
        if (started) return;
        started = true;
        ClientNet.reconnect();
    }

    @Override
    public void tick() {
        if (System.currentTimeMillis() >= until) go();
    }

    @Override
    public void onClose() {
        ClientNet.cancel();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int y = height / 2 - lines.size() * 5 - 40;
        g.drawCenteredString(font, title, width / 2, y, 0xFFFFFF);
        y += 16;
        for (FormattedCharSequence l : lines) {
            g.drawCenteredString(font, l, width / 2, y, 0xA8A8A8);
            y += 10;
        }
        long left = Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
        g.drawCenteredString(font, "Переподключение через " + left + " с (попытка " + attempt + " из " + of + ")", width / 2, y + 10, 0xFFE060);
        super.render(g, mouseX, mouseY, partialTick);
    }
}

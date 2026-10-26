package faygolover.flnettweaks.client;

import faygolover.flnettweaks.FlnConfig;
import faygolover.flnettweaks.NetPrefs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * «Настройки соединения» без меню-терминала FancyMenu (с ним то же окно — custom GUI dept_net из Menu Concepts/fm/gen.py,
 * на плейсхолдерах flnet_opt / flnet_test и действиях flnet_opt / flnet_test / flnet_test_apply).
 */
public final class NetOptionsScreen extends Screen {
    private final Screen parent;
    private Button rate, patience, compress, apply;

    public NetOptionsScreen(Screen parent) {
        super(Component.literal("Настройки соединения"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 155, y = height / 6 + 10;
        rate = addRenderableWidget(Button.builder(Component.empty(), b -> { NetOpts.cycle("rate"); refresh(); })
                .bounds(x, y, 310, 20).tooltip(Tooltip.create(Component.literal(NetOpts.RATE_HELP))).build());
        patience = addRenderableWidget(Button.builder(Component.empty(), b -> { NetOpts.cycle("patience"); refresh(); })
                .bounds(x, y + 24, 310, 20).tooltip(Tooltip.create(Component.literal(NetOpts.PATIENCE_HELP))).build());
        compress = addRenderableWidget(Button.builder(Component.empty(), b -> { NetOpts.cycle("compress"); refresh(); })
                .bounds(x, y + 48, 310, 20).tooltip(Tooltip.create(Component.literal(NetOpts.COMPRESS_HELP))).build());
        addRenderableWidget(Button.builder(Component.literal("Проверить связь"), b -> NetTest.start()).bounds(x, y + 84, 150, 20).build());
        apply = addRenderableWidget(Button.builder(Component.literal("Применить совет"), b -> { NetTest.applyAdvice(); refresh(); })
                .bounds(x + 160, y + 84, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose()).bounds(width / 2 - 100, height - 28, 200, 20).build());
        refresh();
    }

    private void refresh() {
        NetPrefs p = NetPrefs.mine();
        rate.setMessage(Component.literal("Ограничение скорости: " + NetPrefs.rateName(p.rateKbit)));
        patience.setMessage(Component.literal("Терпение при входе: " + NetPrefs.patienceName(p.longPatience)));
        compress.setMessage(Component.literal("Сжатие: " + NetPrefs.compressionName(p.strongCompression)));
    }

    @Override
    public void tick() {
        apply.active = NetTest.state == NetTest.State.DONE && NetTest.advice != null && !NetTest.advice.isDefault();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        int y = height / 6 + 120;
        for (int i = 0; i < 4; i++) {
            String l = NetTest.line(i);
            if (!l.isEmpty()) g.drawCenteredString(font, l, width / 2, y, i == 3 ? 0xFFE060 : 0xC0C0C0);
            y += 12;
        }
        g.drawCenteredString(font, "Действует со следующего подключения" + (FlnConfig.tlsClient() ? " · TLS включён" : ""), width / 2, height - 42, 0x808080);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

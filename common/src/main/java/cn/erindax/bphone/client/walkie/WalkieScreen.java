package cn.erindax.bphone.client.walkie;

import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.item.WalkieRadioItem;
import cn.erindax.bphone.walkie.WalkieBand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class WalkieScreen extends Screen {
    private static final int PANEL_WIDTH = 188;
    private static final int PANEL_HEIGHT = 142;
    private static final int COLOR_PANEL = 0xF0181A22;
    private static final int COLOR_PANEL_EDGE = 0xFF4C566A;
    private static final int COLOR_LCD = 0xFF1E3A32;
    private static final int COLOR_LCD_EDGE = 0xFF65A87B;
    private static final int COLOR_SIGNAL = 0xFF9FE870;
    private static final int COLOR_BUTTON = 0xFF265A62;
    private static final int COLOR_BUTTON_HOVER = 0xFF347982;
    private static final int COLOR_BUTTON_EDGE = 0xFF8AE3C1;
    private static final int COLOR_BUTTON_TEXT = 0xFFEAFDF4;
    private final InteractionHand hand;
    private WalkieBand band;
    private int frequency;

    public WalkieScreen(InteractionHand hand) {
        super(Component.translatable("screen.bphone.walkie.title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        ItemStack stack = getWalkieStack();
        band = WalkieRadioItem.getBand(stack);
        frequency = WalkieRadioItem.getFrequency(stack);
        rebuildButtons();
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null || !getWalkieStack().is(ModItems.WALKIE.get())) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOR_PANEL_EDGE);
        graphics.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, COLOR_PANEL);
        graphics.fill(left + 14, top + 22, left + PANEL_WIDTH - 14, top + 62, COLOR_LCD_EDGE);
        graphics.fill(left + 17, top + 25, left + PANEL_WIDTH - 17, top + 59, COLOR_LCD);
        drawSignalBars(graphics, left + PANEL_WIDTH - 45, top + 34);

        graphics.drawCenteredString(font, title, left + PANEL_WIDTH / 2, top + 8, 0xFFE7EDF4);
        graphics.drawCenteredString(font, band.display(frequency), left + PANEL_WIDTH / 2, top + 36, COLOR_SIGNAL);
        graphics.drawCenteredString(font, Component.translatable("screen.bphone.walkie.status.hold_to_talk"),
                left + PANEL_WIDTH / 2, top + 65, 0xFFB8C0CC);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void rebuildButtons() {
        clearWidgets();
        setFocused(null);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int row = top + 82;

        addRenderableWidget(new WalkieButton(left + 14, row, 50, 20, Component.literal(band.name()), () -> {
            band = band.next();
            frequency = band.defaultFrequency();
            syncTuning();
            rebuildButtons();
        }));

        addRenderableWidget(new WalkieButton(left + 72, row, 42, 20, Component.literal("-"),
                () -> adjust(-band.step())));
        addRenderableWidget(new WalkieButton(left + 122, row, 42, 20, Component.literal("+"),
                () -> adjust(band.step())));

        addRenderableWidget(new WalkieButton(left + 14, row + 28, 76, 20,
                Component.translatable("screen.bphone.walkie.button.scan_down"),
                () -> adjust(-band.step() * 5)));
        addRenderableWidget(new WalkieButton(left + 98, row + 28, 76, 20,
                Component.translatable("screen.bphone.walkie.button.scan_up"),
                () -> adjust(band.step() * 5)));
    }

    private void adjust(int amount) {
        frequency = band.clamp(frequency + amount);
        syncTuning();
    }

    private void syncTuning() {
        WalkieNetworkingClient.requestTune(hand, band, frequency);
    }

    private ItemStack getWalkieStack() {
        if (minecraft == null || minecraft.player == null) {
            return ItemStack.EMPTY;
        }

        return minecraft.player.getItemInHand(hand);
    }

    private void drawSignalBars(GuiGraphics graphics, int x, int y) {
        for (int index = 0; index < 4; index++) {
            int height = 4 + index * 3;
            graphics.fill(x + index * 6, y + 16 - height, x + index * 6 + 4, y + 16, COLOR_SIGNAL);
        }
    }

    private final class WalkieButton extends AbstractWidget {
        private final Runnable onPress;

        private WalkieButton(int x, int y, int width, int height, Component message, Runnable onPress) {
            super(x, y, width, height, message);
            this.onPress = onPress;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int fill = isHoveredOrFocused() ? COLOR_BUTTON_HOVER : COLOR_BUTTON;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, COLOR_BUTTON_EDGE);
            graphics.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, fill);
            graphics.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + 5, 0x663FE6C0);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, COLOR_BUTTON_TEXT);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (!active) {
                return;
            }

            playDownSound(Minecraft.getInstance().getSoundManager());
            onPress.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }
}

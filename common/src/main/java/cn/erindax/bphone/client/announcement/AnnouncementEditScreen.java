package cn.erindax.bphone.client.announcement;

import cn.erindax.bphone.announcement.Announcement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class AnnouncementEditScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 226;
    private final Screen parent;
    private final Announcement editing;
    private String titleValue;
    private String bodyValue;
    private boolean starredValue;
    private Component error = Component.empty();
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int bodyTop;

    public AnnouncementEditScreen(Screen parent, Announcement editing) {
        super(Component.translatable(editing == null ? "announcement.bphone.editor.new" : "announcement.bphone.editor.edit"));
        this.parent = parent;
        this.editing = editing;
        this.titleValue = editing == null ? "" : editing.title();
        this.bodyValue = editing == null ? "" : editing.body();
        this.starredValue = editing != null && editing.starred();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_WIDTH, this.width - 20);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = Math.max(8, (this.height - PANEL_HEIGHT) / 2);

        EditBox titleBox = new EditBox(font, panelLeft, panelTop + 30, panelWidth, 18,
                Component.translatable("announcement.bphone.editor.title"));
        titleBox.setMaxLength(Announcement.MAX_TITLE_LENGTH);
        titleBox.setValue(titleValue);
        titleBox.setHint(Component.translatable("announcement.bphone.editor.title_hint"));
        titleBox.setResponder(value -> {
            titleValue = value;
            error = Component.empty();
        });
        addRenderableWidget(titleBox);

        bodyTop = panelTop + 66;
        int bodyHeight = PANEL_HEIGHT - 66 - 58;
        MultiLineEditBox bodyBox = new MultiLineEditBox(font, panelLeft, bodyTop, panelWidth, bodyHeight,
                Component.translatable("announcement.bphone.editor.body_hint"),
                Component.translatable("announcement.bphone.editor.body"));
        bodyBox.setCharacterLimit(Announcement.MAX_BODY_LENGTH);
        bodyBox.setValue(bodyValue);
        bodyBox.setValueListener(value -> bodyValue = value);
        addRenderableWidget(bodyBox);

        addRenderableWidget(Checkbox.builder(Component.translatable("announcement.bphone.editor.starred"), font)
                .pos(panelLeft, bodyTop + bodyHeight + 6)
                .selected(starredValue)
                .onValueChange((checkbox, value) -> starredValue = value)
                .build());

        int buttonTop = bodyTop + bodyHeight + 32;
        int buttonWidth = (panelWidth - 6) / 2;
        addRenderableWidget(Button.builder(Component.translatable(editing == null
                        ? "announcement.bphone.editor.publish" : "announcement.bphone.editor.save"), button -> publish())
                .bounds(panelLeft, buttonTop, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(panelLeft + buttonWidth + 6, buttonTop, buttonWidth, 20).build());
        setInitialFocus(titleBox);
    }

    private void publish() {
        if (titleValue.trim().isEmpty()) {
            error = Component.translatable("announcement.bphone.error.title");
            return;
        }
        AnnouncementNetworkingClient.publish(editing == null ? null : editing.id(), titleValue.trim(), bodyValue, starredValue);
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, this.width / 2, panelTop, 0xFFFFFFFF);
        guiGraphics.drawString(font, Component.translatable("announcement.bphone.editor.title"), panelLeft, panelTop + 18,
                0xFFA0A0A0);
        guiGraphics.drawString(font, Component.translatable("announcement.bphone.editor.body_count",
                bodyValue.length(), Announcement.MAX_BODY_LENGTH), panelLeft, bodyTop - 12, 0xFFA0A0A0);
        if (!error.getString().isEmpty()) {
            guiGraphics.drawString(font, error, panelLeft + panelWidth - font.width(error), panelTop + 18, 0xFFFF5555);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}

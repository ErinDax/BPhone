package cn.erindax.bphone.client.phone;

final class PhoneBankLayout {
    static final int QR_MODULES = 21;
    static final int QR_CELLS = QR_MODULES + 4;

    final PhoneScreen screen;
    final PhoneChatLayout ui;
    final UiRect header;
    final int left;
    final int width;
    final int top;
    final float heroScale;
    final int heroHeight;

    PhoneBankLayout(PhoneScreen screen) {
        this.screen = screen;
        this.ui = new PhoneChatLayout(screen);
        this.header = ui.header();
        this.left = ui.surface.left + ui.pad;
        this.width = ui.surface.width - 2 * ui.pad;
        this.top = header.bottom() + ui.sectionGap;
        this.heroScale = Math.max(ui.titleScale, Math.round(1.5F * ui.scale * ui.guiScale) / (float) ui.guiScale);
        this.heroHeight = Math.max(1, Math.round(8.0F * heroScale));
    }

    UiRect back() {
        return ui.backButton(header);
    }

    UiRect card() {
        int height = 2 * ui.u(10) + ui.avatarSmall + ui.u(8) + ui.captionHeight + ui.textGap + heroHeight
                + ui.u(6) + ui.captionHeight;
        return new UiRect(left, top, width, height);
    }

    int tileIcon() {
        return ui.u(16);
    }

    UiRect tile(int index) {
        int tileWidth = (width - 2 * ui.gap) / 3;
        int tileLeft = left + index * (tileWidth + ui.gap);
        int tileRight = index == 2 ? left + width : tileLeft + tileWidth;
        int height = ui.u(10) + tileIcon() + ui.u(6) + ui.bodyHeight + ui.u(9);
        return new UiRect(tileLeft, card().bottom() + ui.u(10), tileRight - tileLeft, height);
    }

    int homeStatusTop() {
        return tile(0).bottom() + ui.u(12);
    }

    UiRect payee() {
        return new UiRect(left, top, width, ui.rowHeight);
    }

    int numberLabelTop() {
        return top + ui.u(2);
    }

    UiRect numberField() {
        return new UiRect(left, numberLabelTop() + ui.captionHeight + ui.u(4), width, ui.fieldHeight);
    }

    int amountLabelTop() {
        int above = screen.bankPage == PhoneScreen.BANK_PAGE_PAYMENT ? payee().bottom() : numberField().bottom();
        return above + ui.u(10);
    }

    UiRect amountField() {
        return new UiRect(left, amountLabelTop() + ui.captionHeight + ui.u(4), width, ui.fieldHeight);
    }

    int availableTop() {
        return amountField().bottom() + ui.u(4);
    }

    private int formButtonsTop() {
        return availableTop() + ui.captionHeight + ui.u(12);
    }

    private int bottomButtonsTop() {
        return ui.surface.bottom() - ui.u(10) - ui.buttonHeight;
    }

    private boolean formPage() {
        return screen.bankPage == PhoneScreen.BANK_PAGE_TRANSFER || screen.bankPage == PhoneScreen.BANK_PAGE_PAYMENT;
    }

    UiRect cancel() {
        if (formPage()) {
            return new UiRect(left, formButtonsTop(), (width - ui.gap) / 2, ui.buttonHeight);
        }
        if (screen.bankPage == PhoneScreen.BANK_PAGE_RECEIVE) {
            return new UiRect(left, bottomButtonsTop(), width - ui.buttonHeight - ui.gap, ui.buttonHeight);
        }
        return new UiRect(left, bottomButtonsTop(), width, ui.buttonHeight);
    }

    UiRect confirm() {
        int half = (width - ui.gap) / 2;
        return new UiRect(left + half + ui.gap, formButtonsTop(), width - half - ui.gap, ui.buttonHeight);
    }

    int formStatusTop() {
        return confirm().bottom() + ui.u(10);
    }

    UiRect receiveEye() {
        return new UiRect(left + width - ui.buttonHeight, bottomButtonsTop(), ui.buttonHeight, ui.buttonHeight);
    }

    private int receiveAbove() {
        return ui.u(10) + ui.bodyHeight + ui.u(8);
    }

    private int receiveBelow() {
        return ui.u(8) + ui.titleHeight + ui.u(6) + 2 * ui.captionLine + ui.u(10);
    }

    private int receiveQrSize() {
        int maxHeight = bottomButtonsTop() - ui.u(10) - top - receiveAbove() - receiveBelow();
        return Math.max(QR_CELLS, Math.min(width - 2 * ui.u(8), maxHeight));
    }

    UiRect receiveCard() {
        return new UiRect(left, top, width, receiveAbove() + receiveQrSize() + receiveBelow());
    }

    UiRect receiveQr() {
        int size = receiveQrSize();
        return new UiRect(left + (width - size) / 2, top + receiveAbove(), size, size);
    }

    int slipCheck() {
        return ui.u(22);
    }

    UiRect slipCard() {
        int height = ui.u(12) + slipCheck() + ui.u(8) + ui.bodyHeight + ui.u(6) + heroHeight + ui.u(10) + 1
                + ui.u(8) + 4 * (ui.bodyHeight + ui.u(8)) + ui.u(4);
        return new UiRect(left, top, width, height);
    }

    int scanHeaderHeight(UiRect camera) {
        return Math.max(ui.headerHeight + ui.u(10), Math.round(camera.height * 0.15F));
    }

    int scanFooterTop(UiRect camera) {
        int needed = ui.u(8) + 2 * ui.captionLine + ui.u(8) + ui.buttonHeight + ui.u(10);
        return camera.bottom() - Math.max(needed, Math.round(camera.height * 0.26F));
    }

    private int scanButtonsTop(UiRect camera) {
        return camera.bottom() - ui.u(10) - ui.buttonHeight;
    }

    UiRect scanReceive(UiRect camera) {
        return new UiRect(camera.left + ui.pad, scanButtonsTop(camera),
                camera.width - 2 * ui.pad - ui.buttonHeight - ui.gap, ui.buttonHeight);
    }

    UiRect scanEye(UiRect camera) {
        return new UiRect(camera.right() - ui.pad - ui.buttonHeight, scanButtonsTop(camera),
                ui.buttonHeight, ui.buttonHeight);
    }
}

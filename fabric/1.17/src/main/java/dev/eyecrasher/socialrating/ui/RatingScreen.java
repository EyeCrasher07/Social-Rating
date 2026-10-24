package dev.eyecrasher.socialrating.ui;

import dev.eyecrasher.socialrating.SocialRatingClient;
import dev.eyecrasher.socialrating.RatingException;
import java.util.function.Consumer;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import dev.eyecrasher.socialrating.ui.RatingGraphics;
import dev.eyecrasher.socialrating.ui.RatingButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import static dev.eyecrasher.socialrating.SocialRatingClient.tr;

public abstract class RatingScreen extends Screen {
    protected static final int GOLD = 0xffffd575, TEXT = 0xffeeeeee, MUTED = 0xffbbbbbb;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    protected final Screen parent;
    protected final SocialRatingClient client;
    protected int left, top, panelWidth, bottom;
    protected Component notice = new net.minecraft.network.chat.TextComponent("");
    private boolean error, tooSmall;
    private int mouseX, mouseY;

    RatingScreen(Screen parent, SocialRatingClient client, Component title) {
        super(title);
        this.parent = parent;
        this.client = client;
    }

    @Override protected final void init() {
        panelWidth = Math.min(preferredWidth(), width - 16);
        left = (width - panelWidth) / 2;
        int panelHeight = Math.min(preferredHeight(), height - 16);
        top = (height - panelHeight) / 2;
        bottom = top + panelHeight;
        tooSmall = panelWidth < minimumWidth() || panelHeight < minimumHeight();
        button(new net.minecraft.network.chat.TextComponent("×"), left + panelWidth - 28, top + 8, 20, b -> onClose());
        if (tooSmall) button(tr("back"), left + 12, bottom - 42, panelWidth - 24, b -> onClose());
        else build();
    }

    protected void rebuildWidgets() { clearWidgets(); init(); }
    protected abstract void build();
    public final void refreshTranslations() { rebuildWidgets(); }
    public void reportsChanged() {}
    protected abstract void drawContent(RatingGraphics graphics);
    protected int preferredHeight() { return 208; }
    protected int preferredWidth() { return 280; }
    protected int minimumHeight() { return preferredHeight(); }
    protected int minimumWidth() { return preferredWidth(); }

    protected RatingButton button(Component label, int x, int y, int w, Consumer<RatingButton> action) {
        return addRenderableWidget(new RatingButton(x, y, w, 20, label, b -> action.accept((RatingButton) b)));
    }

    protected void closeButton() { button(tr("back"), left + 12, bottom - 42, 80, b -> onClose()); }

    protected void label(RatingGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        String value = text.getString();
        if (font.width(value) > maxWidth) value = font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width("…"))) + "…";
        graphics.drawString(font, value, x, y, color);
    }

    protected void drawCategories(RatingGraphics graphics, String[] values, int y) {
        String[] names = {"ingenuity", "skill", "friendliness"};
        int column = (panelWidth - 24) / 3;
        for (int i = 0; i < 3; i++) {
            int x = left + 12 + i * column;
            inset(graphics, x, y, column - 4, 38);
            label(graphics, tr("category." + names[i]), x + 6, y + 6, column - 16, MUTED);
            graphics.drawString(font, values[i], x + 6, y + 22, GOLD);
        }
    }

    // Match the vanilla popup frame used by 26.3 before GUI sprites existed.
    private static final int[] PANEL_COLORS = {
            0xff000000, 0xff222222, 0xff303030, 0xff555555,
            0xff808080, 0xff8b8b8b, 0xffc6c6c6
    };
    private static final String[][] PANEL_CORNERS = {
            {"  0000", " 06666", "066633", "066333", "063333", "063330"},
            {"000   ", "6660  ", "33340 ", "333340", "333310", "533310"},
            {"063334", "063333", "063333", " 04333", "  0411", "   000"},
            {"633310", "333310", "333110", "331110", "11110 ", "0000  "}
    };

    private void drawPanel(RatingGraphics graphics) {
        graphics.fill(left + 6, top + 6, left + panelWidth - 6, bottom - 6, 0xff303030);
        int[] light = {0, 6, 3, 3, 3, 0}, shade = {0, 1, 3, 3, 3, 6};
        for (int i = 0; i < 6; i++) {
            graphics.fill(left + 6, top + i, left + panelWidth - 6, top + i + 1, PANEL_COLORS[light[i]]);
            graphics.fill(left + i, top + 6, left + i + 1, bottom - 6, PANEL_COLORS[light[i]]);
            graphics.fill(left + 6, bottom - i - 1, left + panelWidth - 6, bottom - i, PANEL_COLORS[shade[i]]);
            graphics.fill(left + panelWidth - i - 1, top + 6, left + panelWidth - i, bottom - 6, PANEL_COLORS[shade[i]]);
        }
        for (int corner = 0; corner < 4; corner++) {
            int x = (corner & 1) == 0 ? left : left + panelWidth - 6;
            int y = corner < 2 ? top : bottom - 6;
            for (int row = 0; row < 6; row++) {
                String pixels = PANEL_CORNERS[corner][row];
                for (int start = 0; start < 6;) {
                    int end = start + 1;
                    while (end < 6 && pixels.charAt(end) == pixels.charAt(start)) end++;
                    if (pixels.charAt(start) != ' ')
                        graphics.fill(x + start, y + row, x + end, y + row + 1, PANEL_COLORS[pixels.charAt(start) - '0']);
                    start = end;
                }
            }
        }
    }

    protected void inset(RatingGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xffa0a0a0);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xff000000);
    }

    /** A caption always stays separate from its value; clipped values are available on hover. */
    protected void field(RatingGraphics graphics, String key, Component value, int x, int y, int w) {
        label(graphics, tr(key), x, y, w, MUTED);
        inset(graphics, x, y + 11, w, 16);
        label(graphics, value, x + 4, y + 15, w - 8, TEXT);
        boolean extra = font.width(value) > w - 8 || font.width(tr(key)) > w || key.endsWith("_time");
        if (extra && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 27) {
            var tooltip = tr(key).append(": ").append(value);
            if (key.endsWith("_time")) tooltip.append("\n").append(tr("field.time_zone", ZoneId.systemDefault().getId()));
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    protected int textRows(int y) { return Math.max(1, (bottom - 76 - (y + 12) - 8) / font.lineHeight); }

    protected void textBox(RatingGraphics graphics, String key, List<FormattedCharSequence> lines, int page, int perPage, int y) {
        label(graphics, tr(key), left + 12, y, panelWidth - 24, MUTED);
        inset(graphics, left + 12, y + 12, panelWidth - 24, bottom - 76 - (y + 12));
        for (int i = page * perPage; i < Math.min(lines.size(), (page + 1) * perPage); i++)
            graphics.drawString(font, lines.get(i), left + 17, y + 17 + (i % perPage) * font.lineHeight, TEXT);
    }

    protected static String date(String timestamp) { return DATE.format(Instant.parse(timestamp)); }
    protected static String time(String timestamp) { return TIME.format(Instant.parse(timestamp)); }

    protected void failure(Throwable cause) {
        String code = RatingException.errorCode(cause);
        notice = code.equals("invalid_score") ? tr("error.score_range", dev.eyecrasher.socialrating.RatingData.MAX_SCORE)
                : code.equals("not_nearby") ? tr("rules.distance", client.rules().ratingDistance)
                : code.equals("invalid_text") ? tr("rules.text_limit") : tr("error." + code);
        error = true;
    }

    protected void message(Component text) { notice = text; error = false; }

    @Override public void renderBackground(com.mojang.blaze3d.vertex.PoseStack pose) {
        net.minecraft.client.gui.GuiComponent.fill(pose, 0, 0, width, height, 0x99000000);
    }

    @Override public final void render(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float delta) {
        RatingGraphics graphics = new RatingGraphics(pose, this);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        renderBackground(pose);
        drawPanel(graphics);
        // Keep the panel behind all text in Minecraft's extracted GUI render state.
        label(graphics, title, left + 12, top + 14, panelWidth - 48, TEXT);
        if (tooSmall) graphics.drawWordWrap(font, tr("small_screen"), left + 12, top + 52, panelWidth - 24, TEXT);
        else drawContent(graphics);
        super.render(pose, mouseX, mouseY, delta);
        if (!notice.getString().isEmpty()) {
            label(graphics, notice, left + 12, bottom - 15, panelWidth - 24, error ? 0xffff9494 : MUTED);
            if (mouseX >= left && mouseX < left + panelWidth && mouseY >= bottom - 20 && mouseY < bottom) {
                graphics.renderTooltip(font, notice, mouseX, mouseY);
            }
        }
    }

    public void showTooltip(com.mojang.blaze3d.vertex.PoseStack pose, Component text, int x, int y) { renderTooltip(pose, text, x, y); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

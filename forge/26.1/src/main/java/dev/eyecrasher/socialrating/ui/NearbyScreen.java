package dev.eyecrasher.socialrating.ui;

import dev.eyecrasher.socialrating.SocialRatingClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Locale;

import static dev.eyecrasher.socialrating.RatingData.*;
import static dev.eyecrasher.socialrating.SocialRatingClient.tr;

public final class NearbyScreen extends RatingScreen {
    private static final int PER_PAGE = 4;
    private List<Identity> players;
    private int page;

    public NearbyScreen(Screen parent, SocialRatingClient client) {
        super(parent, client, tr("nearby_title"));
        players = client.nearby();
    }

    @Override protected int preferredHeight() { return 224; }

    @Override protected void build() {
        page = Math.clamp(page, 0, Math.max(0, (players.size() - 1) / PER_PAGE));
        for (int i = page * PER_PAGE; i < Math.min(players.size(), (page + 1) * PER_PAGE); i++) {
            var player = players.get(i);
            int y = top + 48 + (i % PER_PAGE) * 24;
            var name = Component.literal(player.name()).withColor(TEXT & 0xffffff);
            var identity = addRenderableWidget(new StringWidget(left + 18, y + 6, Math.min(118, font.width(name)), font.lineHeight, name, font)
                    .setMaxWidth(118, StringWidget.TextOverflow.CLAMPED));
            if (font.width(name) > 118) identity.setTooltip(Tooltip.create(name));
            button(tr("nearby.profile"), left + panelWidth - 74, y, 56, b -> client.openProfile(this, player));
        }
        button(tr("previous"), width / 2 - 50, bottom - 68, 24, b -> { page--; rebuildWidgets(); }).active = page > 0;
        button(tr("next"), width / 2 + 26, bottom - 68, 24, b -> { page++; rebuildWidgets(); }).active = (page + 1) * PER_PAGE < players.size();
        int buttonWidth = (panelWidth - 28) / 2;
        button(tr("back"), left + 12, bottom - 42, buttonWidth, b -> onClose());
        button(tr("refresh"), left + 16 + buttonWidth, bottom - 42, buttonWidth, b -> { players = client.nearby(); rebuildWidgets(); });
    }

    @Override protected void drawContent(GuiGraphicsExtractor graphics) {
        inset(graphics, left + 12, top + 46, panelWidth - 24, 102);
        for (int i = page * PER_PAGE; i < Math.min(players.size(), (page + 1) * PER_PAGE); i++) {
            int y = top + 48 + (i % PER_PAGE) * 24;
            graphics.fill(left + 14, y, left + panelWidth - 14, y + 22, i % 2 == 0 ? 0xff171717 : 0xff222222);
        }
        graphics.nextStratum();
        label(graphics, tr("nearby.player_header"), left + 18, top + 34, 118, MUTED);
        label(graphics, tr("nearby.distance_header"), left + 146, top + 34, 52, MUTED);
        if (players.isEmpty()) graphics.centeredText(font, tr("nearby_empty"), width / 2, top + 92, MUTED);
        for (int i = page * PER_PAGE; i < Math.min(players.size(), (page + 1) * PER_PAGE); i++) {
            var player = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(players.get(i).id());
            String distance = player == null || minecraft.player == null ? "—"
                    : String.format(Locale.ROOT, "%.1f", player.distanceTo(minecraft.player));
            graphics.text(font, distance, left + 146, top + 54 + (i % PER_PAGE) * 24, TEXT);
        }
        graphics.centeredText(font, (players.isEmpty() ? 0 : page + 1) + " / " + ((players.size() + PER_PAGE - 1) / PER_PAGE),
                width / 2, bottom - 62, TEXT);
    }
}

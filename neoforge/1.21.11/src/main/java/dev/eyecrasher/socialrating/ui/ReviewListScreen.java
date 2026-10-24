package dev.eyecrasher.socialrating.ui;

import dev.eyecrasher.socialrating.SocialRatingClient;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import static dev.eyecrasher.socialrating.RatingData.*;
import static dev.eyecrasher.socialrating.SocialRatingClient.*;

/** A fixed-size list: identity, average and an action; review details live in ReviewScreen. */
final class ReviewListScreen extends RatingScreen {
    private static final int PER_PAGE = 4;
    private final List<Review> reviews;
    private final boolean sent;
    private int page;

    ReviewListScreen(ProfileScreen parent, SocialRatingClient client, Identity target, Profile profile, boolean sent) {
        super(parent, client, tr(sent ? "sent_title" : "reviews_title", target.name()));
        this.sent = sent;
        reviews = sent ? profile.sent() : profile.reviews();
        if ((sent ? profile.sentCount() : profile.reviewCount()) > reviews.size()) message(tr("latest_reviews", reviews.size()));
    }

    @Override protected int preferredHeight() { return 224; }

    @Override protected void build() {
        page = Math.clamp(page, 0, Math.max(0, (reviews.size() - 1) / PER_PAGE));
        for (int i = page * PER_PAGE; i < Math.min(reviews.size(), (page + 1) * PER_PAGE); i++) {
            var review = reviews.get(i);
            int y = top + 48 + (i % PER_PAGE) * 24;
            var name = Component.literal(sent ? review.targetName() : review.authorName()).withColor(TEXT & 0xffffff);
            var identity = addRenderableWidget(new StringWidget(left + 18, y + 6, Math.min(118, font.width(name)), font.lineHeight, name, font)
                    .setMaxWidth(118, StringWidget.TextOverflow.CLAMPED));
            if (font.width(name) > 118) identity.setTooltip(Tooltip.create(name));
            button(tr("review.open"), left + panelWidth - 74, y, 56,
                    b -> minecraft.setScreen(new ReviewScreen(this, client, review)))
                    .setTooltip(Tooltip.create(tr("review.read_full")));
        }
        button(tr("previous"), width / 2 - 50, bottom - 68, 24, b -> { page--; rebuildWidgets(); }).active = page > 0;
        button(tr("next"), width / 2 + 26, bottom - 68, 24, b -> { page++; rebuildWidgets(); }).active = (page + 1) * PER_PAGE < reviews.size();
        button(tr("back"), left + 12, bottom - 42, panelWidth - 24, b -> onClose());
    }

    @Override public void onClose() {
        super.onClose();
        ((ProfileScreen) parent).refresh();
    }

    @Override protected void drawContent(GuiGraphics graphics) {
        inset(graphics, left + 12, top + 46, panelWidth - 24, 102);
        for (int i = page * PER_PAGE; i < Math.min(reviews.size(), (page + 1) * PER_PAGE); i++) {
            int y = top + 48 + (i % PER_PAGE) * 24;
            graphics.fill(left + 14, y, left + panelWidth - 14, y + 22, i % 2 == 0 ? 0xff171717 : 0xff222222);
        }
        graphics.nextStratum();
        label(graphics, tr(sent ? "field.target" : "field.author"), left + 18, top + 34, 118, MUTED);
        label(graphics, tr("review.score_header"), left + 146, top + 34, 52, MUTED);
        if (reviews.isEmpty()) graphics.drawCenteredString(font, tr("no_reviews"), width / 2, top + 92, MUTED);
        for (int i = page * PER_PAGE; i < Math.min(reviews.size(), (page + 1) * PER_PAGE); i++) {
            var review = reviews.get(i);
            int y = top + 48 + (i % PER_PAGE) * 24;
            int sum = 0, count = 0;
            for (Integer score : new Integer[] {review.ingenuity(), review.skill(), review.friendliness()})
                if (score != null) { sum += score; count++; }
            graphics.drawString(font, average(count == 0 ? null : (double) sum / count) + "/" + MAX_SCORE, left + 146, y + 6, TEXT);
        }
        graphics.drawCenteredString(font, (reviews.isEmpty() ? 0 : page + 1) + " / " + ((reviews.size() + PER_PAGE - 1) / PER_PAGE),
                width / 2, bottom - 62, TEXT);
    }
}

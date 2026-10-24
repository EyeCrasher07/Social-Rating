package dev.eyecrasher.socialrating.ui;

import dev.eyecrasher.socialrating.SocialRatingClient;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import static dev.eyecrasher.socialrating.RatingData.*;
import static dev.eyecrasher.socialrating.SocialRatingClient.*;

final class ReviewScreen extends RatingScreen {
    private final Review review;
    private List<FormattedCharSequence> lines = List.of();
    private boolean commentTab;
    private int page, perPage;

    ReviewScreen(Screen parent, SocialRatingClient client, Review review) {
        super(parent, client, tr("review.card_title"));
        this.review = review;
    }

    @Override protected int preferredWidth() { return 304; }
    @Override protected int preferredHeight() { return 224; }

    @Override protected void build() {
        int half = (panelWidth - 28) / 2;
        button(tr("field.data_tab"), left + 12, top + 34, half, b -> { commentTab = false; rebuildWidgets(); }).active = commentTab;
        button(tr("field.comment_tab"), left + 16 + half, top + 34, half, b -> { commentTab = true; rebuildWidgets(); }).active = !commentTab;
        lines = font.split(review.comment().isEmpty() ? tr("no_comment") : Component.literal(review.comment()), panelWidth - 34);
        perPage = textRows(top + 62);
        page = dev.eyecrasher.socialrating.JavaCompat.clamp(page, 0, Math.max(0, (lines.size() - 1) / perPage));
        if (commentTab) {
            button(tr("previous"), width / 2 - 70, bottom - 68, 24, b -> { page--; rebuildWidgets(); }).active = page > 0;
            button(tr("next"), width / 2 + 46, bottom - 68, 24, b -> { page++; rebuildWidgets(); }).active = (page + 1) * perPage < lines.size();
        }
        closeButton();
        if (review.target().equals(client.self().id().toString())) {
            button(tr("report"), left + 96, bottom - 42, panelWidth - 108,
                    b -> minecraft.setScreen(new ReportScreen(this, client, review))).active = review.reportStatus().isEmpty() && client.rules().reportsEnabled;
            if (!review.reportStatus().isEmpty()) message(tr("report_status." + review.reportStatus()));
        }
        if (review.author().equals(client.self().id().toString()) && client.rules().allowWithdrawal)
            button(tr("withdraw"), left + 96, bottom - 42, panelWidth - 108, b -> minecraft.setScreen(
                    new net.minecraft.client.gui.screens.ConfirmScreen(confirmed -> {
                        minecraft.setScreen(this);
                        if (confirmed) withdraw();
                    }, tr("withdraw"), tr("withdraw.confirm"), tr("withdraw"), tr("back"))));
    }

    private void withdraw() {
        message(tr("saving"));
        client.api().withdraw(review.id()).whenComplete((value, error) -> minecraft.execute(() -> {
            if (error != null) { if (minecraft.screen == this) failure(error); }
            else { client.invalidate(); client.openProfile(null, client.self()); }
        }));
    }

    @Override protected void drawContent(GuiGraphics graphics) {
        if (commentTab) {
            textBox(graphics, "field.review_text", lines, page, perPage, top + 62);
            graphics.drawCenteredString(font, tr("field.page", page + 1, Math.max(1, (lines.size() + perPage - 1) / perPage)), width / 2, bottom - 62, MUTED);
        } else {
            int half = (panelWidth - 30) / 2;
            field(graphics, "field.author", Component.literal(review.authorName()), left + 12, top + 62, half);
            field(graphics, "field.target", Component.literal(review.targetName()), left + 18 + half, top + 62, half);
            field(graphics, "field.review_date", Component.literal(date(review.updatedAt())), left + 12, top + 94, half);
            field(graphics, "field.review_time", Component.literal(time(review.updatedAt())), left + 18 + half, top + 94, half);
            drawCategories(graphics, new String[] {score(review.ingenuity()), score(review.skill()), score(review.friendliness())}, top + 128);
        }
    }

    private static String score(Integer score) { return score == null ? "—" : score + "/" + MAX_SCORE; }
}

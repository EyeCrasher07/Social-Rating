package dev.eyecrasher.socialrating.ui;

import dev.eyecrasher.socialrating.SocialRatingClient;
import dev.eyecrasher.socialrating.ui.RatingGraphics;
import dev.eyecrasher.socialrating.ui.RatingButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import static dev.eyecrasher.socialrating.RatingData.*;
import static dev.eyecrasher.socialrating.SocialRatingClient.tr;

final class ReportScreen extends RatingScreen {
    private static final String[] REASONS = {"unfounded", "abuse", "spam", "other"};
    private final Review review;
    private final Identity reporter;
    private String reason = "unfounded", draft = "";
    private boolean sending;

    ReportScreen(ReviewScreen parent, SocialRatingClient client, Review review) {
        super(parent, client, tr("report_title", review.authorName()));
        this.review = review;
        reporter = client.self();
    }

    @Override protected void build() {
        int column = (panelWidth - 28) / 2;
        for (int i = 0; i < REASONS.length; i++) {
            String choice = REASONS[i];
            button(tr("reason." + choice).withStyle(style -> style.withColor(reason.equals(choice) ? 0xffd575 : 0xedf2fa)),
                    left + 12 + (i % 2) * (column + 4), top + 80 + (i / 2) * 24, column,
                    b -> { reason = choice; rebuildWidgets(); }).active = !sending;
        }
        var details = addRenderableWidget(new EditBox(font, left + 12, top + 142, panelWidth - 24, 20, tr("report_details")));
        details.setMaxLength(client.rules().maxReportDetailsLength);
        details.setValue(draft);

        details.setResponder(value -> draft = value);
        closeButton();
        button(tr("send_report"), left + 96, bottom - 42, panelWidth - 108, b -> send()).active = !sending && client.rules().reportsEnabled;
        if (!sending) message(tr("report_hint"));
    }

    private void send() {
        if (sending) return;
        sending = true;
        message(tr("saving"));
        rebuildWidgets();
        client.api().report(reporter.id(), review.id(), reason, draft).whenComplete((result, error) -> minecraft.execute(() -> {
            if (minecraft.screen != this) return;
            sending = false;
            if (error != null) { rebuildWidgets(); failure(error); }
            else client.openProfile(null, client.self());
        }));
    }

    @Override protected void drawContent(RatingGraphics graphics) {
        int half = (panelWidth - 30) / 2;
        field(graphics, "field.author", new net.minecraft.network.chat.TextComponent(review.authorName()), left + 12, top + 34, half);
        field(graphics, "field.target", new net.minecraft.network.chat.TextComponent(review.targetName()), left + 18 + half, top + 34, half);
        label(graphics, tr("field.reason"), left + 12, top + 68, panelWidth - 24, MUTED);
        label(graphics, tr("rules.details_count", draft.length(), client.rules().maxReportDetailsLength), left + 12, top + 130, panelWidth - 24, MUTED);
    }
}

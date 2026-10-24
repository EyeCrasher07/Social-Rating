package dev.eyecrasher.socialrating.ui;

import dev.eyecrasher.socialrating.SocialRatingClient;
import java.util.ArrayList;
import java.util.List;
import dev.eyecrasher.socialrating.ui.RatingGraphics;
import dev.eyecrasher.socialrating.ui.RatingTooltip;
import dev.eyecrasher.socialrating.ui.RatingLabel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import static dev.eyecrasher.socialrating.RatingData.*;
import static dev.eyecrasher.socialrating.SocialRatingClient.*;

final class AdminReportScreen extends RatingScreen {
    private static final class Field  {
        private final String key;
        private final Component value;
        public Field(String key, Component value) {
            this.key = key;
            this.value = value;
        }
        public String key() { return key; }
        public Component value() { return value; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Field that = (Field) other;
            return java.util.Objects.equals(key, that.key) && java.util.Objects.equals(value, that.value);
        }
        @Override public int hashCode() { return java.util.Objects.hash(key, value); }
}
    private final String id;
    private ModerationReport report;
    private List<FormattedCharSequence> lines = dev.eyecrasher.socialrating.JavaCompat.list();
    private List<Field> fields = dev.eyecrasher.socialrating.JavaCompat.list();
    private boolean requested, loading, sending;
    private int tab, page, perPage = 1, pages = 1;
    private long request;

    AdminReportScreen(AdminReportsScreen parent, SocialRatingClient client, String id) {
        super(parent, client, tr("admin.report_title"));
        this.id = id;
    }

    @Override protected int preferredWidth() { return 304; }
    @Override protected int preferredHeight() { return 268; }
    @Override protected int minimumHeight() { return 224; }
    @Override public void tick() { if (!requested) refresh(); }
    @Override public void reportsChanged() { if (!sending) refresh(); }

    private void refresh() {
        requested = true;
        loading = true;
        long current = ++request;
        rebuildWidgets();
        client.api().reportDetails(((AdminReportsScreen) parent).admin, id).whenComplete((value, error) -> minecraft.execute(() -> {
            if (current != request) return;
            loading = false;
            if (error != null) { report = null; failure(error); }
            else report = value;
            if (minecraft.screen == this) rebuildWidgets();
        }));
    }

    @Override protected void build() {
        boolean decided = report != null && !report.status().equals("pending");
        String[] tabs = decided ? new String[] {"field.data_tab", "admin.review_tab", "admin.complaint_tab", "decision.tab"}
                : new String[] {"field.data_tab", "admin.review_tab", "admin.complaint_tab"};
        int column = (panelWidth - 24 - (tabs.length - 1) * 4) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int selected = i;
            button(tr(tabs[i]), left + 12 + i * (column + 4), top + 34, column,
                    b -> { tab = selected; page = 0; rebuildWidgets(); }).active = tab != i;
        }
        if (report != null) {
            fields = metadata();
            if (tab == 0) perPage = Math.max(1, (bottom - top - 140) / 30) * 2;
            else {
                Component text = tab == 1 ? (report.snapshot().comment().isEmpty() ? tr("no_comment") : new net.minecraft.network.chat.TextComponent(report.snapshot().comment()))
                        : tab == 2 ? (report.details().isEmpty() ? tr("admin.no_details") : new net.minecraft.network.chat.TextComponent(report.details()))
                        : (report.decisionReason().isEmpty() ? tr("decision.no_reason") : new net.minecraft.network.chat.TextComponent(report.decisionReason()));
                lines = font.split(text, panelWidth - 34);
                perPage = textRows(top + (tab == 1 ? 108 : tab == 2 ? 100 : 62));
            }
            int size = tab == 0 ? fields.size() : lines.size();
            pages = Math.max(1, (size + perPage - 1) / perPage);
            page = dev.eyecrasher.socialrating.JavaCompat.clamp(page, 0, pages - 1);
            if (pages > 1) {
                button(tr("previous"), width / 2 - 70, navigationY(), 24, b -> { page--; rebuildWidgets(); }).active = page > 0;
                button(tr("next"), width / 2 + 46, navigationY(), 24, b -> { page++; rebuildWidgets(); }).active = page + 1 < pages;
            }
            if (showsDecision()) {
                var text = tr(report.status().equals("withdrawn") ? "withdraw.author" : "admin.decision", report.decisionNote()).withStyle(style -> style.withColor(MUTED & 0xffffff));
                int maximum = panelWidth - 24;
                var reviewer = addRenderableWidget(new RatingLabel(left + 12, bottom - 58, Math.min(font.width(text), maximum),
                        font.lineHeight, text, font));
                if (font.width(text) > maximum) reviewer.setTooltip(RatingTooltip.create(text));
            }
        }
        int half = (panelWidth - 28) / 2;
        if (((AdminReportsScreen) parent).admin && report != null && report.status().equals("pending")) {
            button(tr("admin.keep_review"), left + 12, bottom - 42, half, b -> resolve(false)).active = !sending && !loading;
            var remove = button(tr("admin.remove_review"), left + 16 + half, bottom - 42, half, b -> resolve(true));
            remove.active = !sending && !loading && !report.outdated();
            if (report.outdated()) { remove.setTooltip(RatingTooltip.create(tr("admin.outdated"))); if (!sending) message(tr("admin.outdated")); }
        } else {
            closeButton();
            button(tr("refresh"), left + 96, bottom - 42, panelWidth - 108, b -> refresh()).active = !loading;
        }
    }

    private List<Field> metadata() {
        var review = report.snapshot();
        var values = new ArrayList<Field>();
        values.add(new Field("field.author", new net.minecraft.network.chat.TextComponent(review.authorName())));
        values.add(new Field("field.target", new net.minecraft.network.chat.TextComponent(review.targetName())));
        values.add(new Field("field.reporter", new net.minecraft.network.chat.TextComponent(report.reporterName())));
        values.add(new Field("field.status", tr("admin.status." + report.status())));
        values.add(new Field("field.report_date", new net.minecraft.network.chat.TextComponent(date(report.createdAt()))));
        values.add(new Field("field.report_time", new net.minecraft.network.chat.TextComponent(time(report.createdAt()))));
        values.add(new Field("field.review_date", new net.minecraft.network.chat.TextComponent(date(review.updatedAt()))));
        values.add(new Field("field.review_time", new net.minecraft.network.chat.TextComponent(time(review.updatedAt()))));
        return values;
    }

    private boolean showsDecision() { return tab == 0 && report != null && !report.decisionNote().isEmpty(); }
    private int navigationY() { return bottom - (showsDecision() ? 84 : 68); }

    private void resolve(boolean remove) {
        if (!loading && report != null) minecraft.setScreen(new DecisionScreen(this, client, id, remove));
    }

    @Override public void onClose() {
        super.onClose();
        ((AdminReportsScreen) parent).refresh();
    }

    @Override protected void drawContent(RatingGraphics graphics) {
        if (report == null) { label(graphics, tr(loading ? "loading" : "admin.unavailable"), left + 12, top + 74, panelWidth - 24, MUTED); return; }
        if (tab == 0) {
            int half = (panelWidth - 30) / 2;
            for (int i = page * perPage; i < Math.min(fields.size(), (page + 1) * perPage); i++) {
                var value = fields.get(i);
                int slot = i % perPage;
                field(graphics, value.key(), value.value(), left + 12 + (slot % 2) * (half + 6), top + 62 + (slot / 2) * 30, half);
            }
        } else if (tab == 1) {
            var review = report.snapshot();
            drawCategories(graphics, new String[] {score(review.ingenuity()), score(review.skill()), score(review.friendliness())}, top + 62);
            textBox(graphics, "field.review_text", lines, page, perPage, top + 108);
        } else if (tab == 2) {
            field(graphics, "field.reason", tr("reason." + report.reason()), left + 12, top + 62, panelWidth - 24);
            textBox(graphics, "field.report_details", lines, page, perPage, top + 100);
        } else textBox(graphics, "decision.reason", lines, page, perPage, top + 62);
        if (pages > 1) graphics.drawCenteredString(font, tr("field.page", page + 1, pages), width / 2, navigationY() + 6, MUTED);
    }

    private static String score(Integer score) { return score == null ? "—" : score + "/" + MAX_SCORE; }
}

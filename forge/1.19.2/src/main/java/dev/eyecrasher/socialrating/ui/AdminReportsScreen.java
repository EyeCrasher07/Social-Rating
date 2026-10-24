package dev.eyecrasher.socialrating.ui;

import dev.eyecrasher.socialrating.SocialRatingClient;
import dev.eyecrasher.socialrating.ui.RatingGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static dev.eyecrasher.socialrating.RatingData.*;
import static dev.eyecrasher.socialrating.SocialRatingClient.tr;

/** The server returns only one small page of reports, after checking operator permissions. */
public final class AdminReportsScreen extends RatingScreen {
    private ReportPage result;
    private boolean pending = true, requested, loading;
    private int page;
    private long request;
    final boolean admin;

    public AdminReportsScreen(Screen parent, SocialRatingClient client) {
        this(parent, client, true);
    }
    public AdminReportsScreen(Screen parent, SocialRatingClient client, boolean admin) {
        super(parent, client, tr(admin ? "admin.title" : "my_reports"));
        this.admin = admin;
    }

    @Override protected int preferredHeight() { return 224; }

    @Override public void tick() { if (!requested) refresh(); }
    @Override public void reportsChanged() { refresh(); }

    public void refresh() {
        requested = true;
        loading = true;
        message(Component.empty());
        long current = ++request;
        rebuildWidgets();
        client.api().reports(admin, pending, page).whenComplete((value, error) -> minecraft.execute(() -> {
            if (current != request) return;
            loading = false;
            if (error != null) { result = null; failure(error); }
            else { result = value; page = value.page(); }
            if (minecraft.screen == this) rebuildWidgets();
        }));
    }

    @Override protected void build() {
        int half = (panelWidth - 28) / 2;
        button(tr("admin.waiting", result == null ? "—" : result.pending()), left + 12, top + 34, half,
                b -> { pending = true; page = 0; refresh(); }).active = !pending && !loading;
        button(tr("admin.archive", result == null ? "—" : result.resolved()), left + 16 + half, top + 34, half,
                b -> { pending = false; page = 0; refresh(); }).active = pending && !loading;
        if (result != null) for (int i = 0; i < result.reports().size(); i++) {
            var report = result.reports().get(i);
            button(tr("admin.open"), left + panelWidth - 74, top + 74 + i * 46, 56,
                    b -> minecraft.setScreen(new AdminReportScreen(this, client, report.id()))).active = !loading;
        }
        button(tr("previous"), width / 2 - 50, bottom - 68, 24, b -> { page--; refresh(); })
                .active = !loading && result != null && page > 0;
        button(tr("next"), width / 2 + 26, bottom - 68, 24, b -> { page++; refresh(); })
                .active = !loading && result != null && page + 1 < result.pages();
        closeButton();
        button(tr("refresh"), left + 96, bottom - 42, panelWidth - 108, b -> refresh()).active = !loading;
    }

    @Override protected void drawContent(RatingGraphics graphics) {
        if (result == null || result.reports().isEmpty())
            label(graphics, tr(loading ? "loading" : pending ? "admin.empty" : "admin.archive_empty"), left + 12, top + 74, panelWidth - 24, MUTED);
        if (result == null) return;
        for (int i = 0; i < result.reports().size(); i++) {
            var report = result.reports().get(i);
            int y = top + 62 + i * 46;
            inset(graphics, left + 12, y, panelWidth - 24, 42);
            label(graphics, tr("field.entry_target", report.snapshot().targetName()), left + 18, y + 5, panelWidth - 100, TEXT);
            label(graphics, tr("field.entry_author", report.snapshot().authorName()), left + 18, y + 17, panelWidth - 100, TEXT);
            label(graphics, tr(pending ? "field.entry_reason" : "field.entry_status",
                    tr(pending ? "reason." + report.reason() : "admin.status." + report.status())), left + 18, y + 29, panelWidth - 100, MUTED);
        }
        graphics.drawCenteredString(font, (page + 1) + " / " + result.pages(), width / 2, bottom - 62, TEXT);
    }
}

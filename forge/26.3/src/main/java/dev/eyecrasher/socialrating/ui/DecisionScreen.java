package dev.eyecrasher.socialrating.ui;

import dev.eyecrasher.socialrating.SocialRatingClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

import static dev.eyecrasher.socialrating.SocialRatingClient.tr;

/** Review the action and optionally explain it before committing a moderation decision. */
final class DecisionScreen extends RatingScreen {
    private final String report;
    private final boolean remove;
    private String draft = "";
    private boolean sending;

    DecisionScreen(AdminReportScreen parent, SocialRatingClient client, String report, boolean remove) {
        super(parent, client, tr(remove ? "admin.remove_review" : "admin.keep_review"));
        this.report = report; this.remove = remove;
    }

    @Override protected int preferredWidth() { return 304; }
    @Override protected int preferredHeight() { return 168; }

    @Override protected void build() {
        var note = addRenderableWidget(new EditBox(font, left + 12, top + 66, panelWidth - 24, 20, tr("decision.reason")));
        note.setMaxLength(client.rules().maxDecisionReasonLength);
        note.setValue(draft); note.setResponder(value -> draft = value); note.setEditable(!sending);
        closeButton();
        button(tr("confirm"), left + 96, bottom - 42, panelWidth - 108, b -> submit()).active = !sending;
    }

    private void submit() {
        if (sending) return;
        sending = true; message(tr("saving")); rebuildWidgets();
        client.api().resolve(report, remove, draft).whenComplete((value, error) -> minecraft.execute(() -> {
            sending = false;
            if (minecraft.gui.screen() != this) return;
            if (error != null) { rebuildWidgets(); failure(error); }
            else { client.invalidate(); parent.onClose(); }
        }));
    }

    @Override protected void drawContent(GuiGraphicsExtractor graphics) {
        label(graphics, tr("decision.reason_optional", client.rules().maxDecisionReasonLength), left + 12, top + 48, panelWidth - 24, MUTED);
        label(graphics, tr("decision.visible_to_participants"), left + 12, top + 94, panelWidth - 24, MUTED);
    }
}

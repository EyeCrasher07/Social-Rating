package dev.eyecrasher.socialrating.ui;

import dev.eyecrasher.socialrating.SocialRatingClient;
import java.util.Arrays;
import dev.eyecrasher.socialrating.ui.RatingGraphics;
import dev.eyecrasher.socialrating.ui.RatingButton;
import net.minecraft.client.gui.widget.TextFieldWidget;
import dev.eyecrasher.socialrating.ui.RatingTooltip;
import dev.eyecrasher.socialrating.ui.RatingLabel;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

import static dev.eyecrasher.socialrating.RatingData.*;
import static dev.eyecrasher.socialrating.SocialRatingClient.*;

/** A profile and its editable rating share one small card. */
public final class ProfileScreen extends RatingScreen {
    private static final String[] CATEGORIES = {"ingenuity", "skill", "friendliness"};
    private final Identity target, author;
    private final boolean own;
    private final Integer[] scores = new Integer[3];
    private final RatingButton[][] stars = new RatingButton[3][MAX_SCORE];
    private Profile profile;
    private RatingButton submit;
    private TextFieldWidget comment;
    private String draft = "";
    private boolean requested, loading, sending, dirty, building;
    private long request;

    public ProfileScreen(Screen parent, SocialRatingClient client, Identity target) {
        super(parent, client, new net.minecraft.util.text.StringTextComponent(target.name()));
        this.target = target;
        author = client.self();
        own = author.id().equals(target.id());
    }

    @Override protected int preferredHeight() { return own ? 168 : 208; }

    @Override protected void build() {
        building = true;
        try { buildCard(); } finally { building = false; }
    }

    private void buildCard() {
        submit = null;
        comment = null;
        for (var row : stars) Arrays.fill(row, null);
        if (!requested) { requested = true; load(); }
        if (own) {
            button(new net.minecraft.util.text.StringTextComponent("…"), left + panelWidth - 52, top + 8, 20,
                    b -> minecraft.setScreen(new SettingsScreen(this, client)))
                    .setTooltip(RatingTooltip.create(tr("settings")));
            int w = (panelWidth - 28) / 2;
            button(tr("reviews_short", profile == null ? 0 : profile.reviewCount()), left + 12, bottom - 68, w,
                    b -> minecraft.setScreen(new ReviewListScreen(this, client, target, profile, false))).active = profile != null && !loading;
            button(tr("sent_short"), left + 16 + w, bottom - 68, w,
                    b -> minecraft.setScreen(new ReviewListScreen(this, client, target, profile, true))).active = profile != null && !loading;
            button(tr("nearby"), left + 12, bottom - 42, w, b -> client.openNearby(this));
            button(tr("my_reports"), left + 16 + w, bottom - 42, w, b -> minecraft.setScreen(new AdminReportsScreen(this, client, false)));
        } else if (profile != null) {
            for (int row = 0; row < 3; row++) {
                final int category = row;
                for (int value = 1; value <= MAX_SCORE; value++) {
                    final int choice = value;
                    stars[row][value - 1] = button(new net.minecraft.util.text.StringTextComponent(""), left + panelWidth - 130 + (value - 1) * 24,
                            top + 48 + row * 24, 22, b -> {
                                scores[category] = scores[category] != null && scores[category] == choice ? null : choice;
                                dirty = true;
                                message(new net.minecraft.util.text.StringTextComponent(""));
                                updateButtons();
                            });
                    stars[row][value - 1].setTooltip(RatingTooltip.create(tr("score5." + CATEGORIES[row] + "." + value)));
                }
            }
            comment = addButton(new TextFieldWidget(font, left + 12, top + 132, panelWidth - 24, 20, tr("comment")));
            comment.setMaxLength(client.rules().maxCommentLength);
            comment.setValue(draft);

            comment.setResponder(value -> { draft = value; dirty = true; updateButtons(); });
            int w = (panelWidth - 28) / 2;
            button(tr("reviews_short", profile == null ? 0 : profile.reviewCount()), left + 12, bottom - 42, w,
                    b -> minecraft.setScreen(new ReviewListScreen(this, client, target, profile, false))).active = profile != null && !loading && !sending;
            submit = button(tr("save_short"), left + 16 + w, bottom - 42, w, b -> save());
            updateButtons();
        }
        if (profile != null && !own) {
            for (int row = 0; row < 3; row++) {
                ITextComponent text = tr("category." + CATEGORIES[row]);
                text = text.copy().withStyle(style -> style.withColor(net.minecraft.util.text.Color.fromRgb(TEXT & 0xffffff)));
                int maximum = panelWidth - 148;
                var category = addButton(new RatingLabel(left + 12, top + 54 + row * 24,
                        Math.min(maximum, font.width(text)), font.lineHeight, text, font));
                category.setTooltip(RatingTooltip.create(tr("category_help." + CATEGORIES[row])));
            }
        }
        if (!loading && profile == null) button(tr("retry"), left + 12, top + 76, panelWidth - 24, b -> refresh());
    }

    public void refresh() {
        load();
        rebuildWidgets();
    }

    private void load() {
        loading = true;
        message(new net.minecraft.util.text.StringTextComponent(""));
        long current = ++request;
        client.api().profile(author.id(), target.id()).whenComplete((result, error) -> minecraft.execute(() -> {
            if (request != current || !client.self().id().equals(author.id())) return;
            loading = false;
            if (error != null) failure(error);
            else {
                profile = result;
                if (!dirty && result.myReview() != null) {
                    var review = result.myReview();
                    scores[0] = review.ingenuity(); scores[1] = review.skill(); scores[2] = review.friendliness();
                    draft = review.comment();
                }
            }
            if (minecraft.screen == this && !building) rebuildWidgets();
        }));
    }

    private void updateButtons() {
        boolean allowed = profile != null && !loading && !sending && author.id().equals(client.self().id()) && client.canRate(target.id())
                && client.rules().ratingsEnabled && (profile.myReview() == null || client.rules().allowEditing);
        for (int row = 0; row < 3; row++) for (int value = 1; value <= MAX_SCORE; value++) {
            var button = stars[row][value - 1];
            if (button == null) continue;
            boolean selected = scores[row] != null && scores[row] >= value;
            button.setMessage(new net.minecraft.util.text.StringTextComponent(selected ? "★" : "☆").withStyle(style -> style.withColor(net.minecraft.util.text.Color.fromRgb(selected ? 0xffd575 : 0xa5b2c6))));
            button.active = allowed && client.rules().categoryEnabled(row);
        }
        if (comment != null) comment.setEditable(allowed && client.rules().commentsEnabled);
        if (submit != null) submit.active = allowed && dirty && Arrays.stream(scores).anyMatch(value -> value != null);
    }

    @Override public void tick() { if (!own) updateButtons(); }

    private void save() {
        updateButtons();
        if (submit == null || !submit.active) return;
        sending = true;
        updateButtons();
        message(tr("saving"));
        Integer[] selected = scores.clone();
        for (int i = 0; i < 3; i++) if (!client.rules().categoryEnabled(i)) selected[i] = null;
        client.api().save(author, target, selected, client.rules().commentsEnabled ? draft : "").whenComplete((result, error) -> minecraft.execute(() -> {
            sending = false;
            if (error == null) { client.invalidate(); dirty = false; }
            if (minecraft.screen != this) return;
            if (error != null) { failure(error); updateButtons(); }
            else {
                load();
                rebuildWidgets();
                message(tr("saved"));
            }
        }));
    }

    @Override protected void drawContent(RatingGraphics graphics) {
        if (profile == null) {
            label(graphics, tr(loading ? "loading" : "profile_unavailable"), left + 12, top + 36, panelWidth - 24, MUTED);
            return;
        }
        var summary = profile.summary();
        label(graphics, summary.count() == 0 ? tr("no_reviews_short") : tr("summary_score", average(summary.average()), MAX_SCORE, summary.count()),
                left + 12, top + 32, panelWidth - 24, TEXT);
        if (own) drawCategories(graphics, new String[] {average(summary.ingenuity()), average(summary.skill()), average(summary.friendliness())}, top + 52);
        else label(graphics, tr("field.review_input"), left + 12, top + 120, panelWidth - 24, MUTED);
        if (notice.getString().isEmpty() && !own) {
            ITextComponent hint = !client.rules().ratingsEnabled ? tr("error.ratings_disabled")
                    : profile.myReview() != null && !client.rules().allowEditing ? tr("error.editing_disabled")
                    : !client.canRate(target.id()) ? tr("rules.distance", client.rules().ratingDistance)
                    : !client.rules().commentsEnabled ? tr("error.comments_disabled") : new net.minecraft.util.text.StringTextComponent("");
            label(graphics, hint, left + 12, bottom - 15, panelWidth - 24, MUTED);
        }
    }
}

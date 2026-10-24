package dev.eyecrasher.socialrating.ui;

import dev.eyecrasher.socialrating.SocialRatingClient;
import dev.eyecrasher.socialrating.RatingConfig;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;

import static dev.eyecrasher.socialrating.SocialRatingClient.tr;

final class SettingsScreen extends RatingScreen {
    private boolean nameplates, hint;
    private int distance;
    private String mode;

    SettingsScreen(Screen parent, SocialRatingClient client) {
        super(parent, client, tr("settings"));
        nameplates = client.config().showNameplates;
        hint = client.config().showInteractionHint;
        distance = client.config().nameplateDistance;
        mode = client.config().nameplateMode;
    }

    @Override protected int preferredHeight() { return 192; }

    @Override protected void build() {
        button(tr("toggle_nameplates", tr(nameplates ? "on" : "off")), left + 12, top + 36, panelWidth - 24,
                b -> { nameplates = !nameplates; rebuildWidgets(); });
        button(tr("nameplate_mode", tr("nameplate_mode." + mode)), left + 12, top + 60, panelWidth - 24,
                b -> { mode = mode.equals("number") ? "stars" : "number"; rebuildWidgets(); })
                .setTooltip(Tooltip.create(tr("nameplate_mode.help")));
        button(tr("toggle_hint", tr(hint ? "on" : "off")), left + 12, top + 84, panelWidth - 24,
                b -> { hint = !hint; rebuildWidgets(); });
        button(tr("distance", distance), left + 12, top + 108, panelWidth - 24,
                b -> { distance = distance < 32 ? 32 : distance < 64 ? 64 : 16; rebuildWidgets(); });
        closeButton();
        button(tr("save_settings"), left + 96, bottom - 42, panelWidth - 108, b -> {
            var next = new RatingConfig();
            next.language = client.config().language;
            next.nameplateMode = mode;
            next.showNameplates = nameplates; next.showInteractionHint = hint; next.nameplateDistance = distance;
            try { next.save(); client.applyConfig(next); onClose(); }
            catch (IOException error) { message(tr("config_error")); }
        });
    }

    @Override protected void drawContent(GuiGraphicsExtractor graphics) {}
}

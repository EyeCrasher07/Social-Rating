package dev.eyecrasher.socialrating.ui;
import net.minecraft.network.chat.Component;
public final class RatingTooltip {
    public final Component text;
    private RatingTooltip(Component text) { this.text = text; }
    public static RatingTooltip create(Component text) { return new RatingTooltip(text); }
}

package dev.eyecrasher.socialrating.ui;
import net.minecraft.util.text.ITextComponent;
public final class RatingTooltip {
    public final ITextComponent text;
    private RatingTooltip(ITextComponent text) { this.text = text; }
    public static RatingTooltip create(ITextComponent text) { return new RatingTooltip(text); }
}

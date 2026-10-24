package dev.eyecrasher.socialrating.ui;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.IReorderingProcessor;

/** Draw the same compact cards through Minecraft's native MatrixStack GUI API. */
public final class RatingGraphics {
    public final MatrixStack pose;
    private final RatingScreen owner;
    public RatingGraphics(MatrixStack pose, RatingScreen owner) { this.pose = pose; this.owner = owner; }
    public int guiWidth() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
    public int guiHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
    public void fill(int x0, int y0, int x1, int y1, int color) { AbstractGui.fill(pose, x0, y0, x1, y1, color); }
    public void drawString(FontRenderer font, String text, int x, int y, int color) { font.drawShadow(pose, text, x, y, color); }
    public void drawString(FontRenderer font, ITextComponent text, int x, int y, int color) { font.drawShadow(pose, text, x, y, color); }
    public void drawString(FontRenderer font, IReorderingProcessor text, int x, int y, int color) { font.drawShadow(pose, text, x, y, color); }
    public void drawCenteredString(FontRenderer font, String text, int x, int y, int color) { drawString(font, text, x - font.width(text) / 2, y, color); }
    public void drawCenteredString(FontRenderer font, ITextComponent text, int x, int y, int color) { drawString(font, text, x - font.width(text) / 2, y, color); }
    public void drawWordWrap(FontRenderer font, ITextProperties text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) { drawString(font, line, x, y, color); y += font.lineHeight; }
    }
    public void renderTooltip(FontRenderer font, ITextComponent text, int x, int y) { if (owner != null) owner.showTooltip(pose, text, x, y); }
}

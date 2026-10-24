package dev.eyecrasher.socialrating.ui;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Draw the same compact cards through Minecraft's native PoseStack GUI API. */
public final class RatingGraphics {
    public final PoseStack pose;
    private final RatingScreen owner;
    public RatingGraphics(PoseStack pose, RatingScreen owner) { this.pose = pose; this.owner = owner; }
    public int guiWidth() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
    public int guiHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
    public void fill(int x0, int y0, int x1, int y1, int color) { GuiComponent.fill(pose, x0, y0, x1, y1, color); }
    public void drawString(Font font, String text, int x, int y, int color) { font.drawShadow(pose, text, x, y, color); }
    public void drawString(Font font, Component text, int x, int y, int color) { font.drawShadow(pose, text, x, y, color); }
    public void drawString(Font font, FormattedCharSequence text, int x, int y, int color) { font.drawShadow(pose, text, x, y, color); }
    public void drawCenteredString(Font font, String text, int x, int y, int color) { drawString(font, text, x - font.width(text) / 2, y, color); }
    public void drawCenteredString(Font font, Component text, int x, int y, int color) { drawString(font, text, x - font.width(text) / 2, y, color); }
    public void drawWordWrap(Font font, FormattedText text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) { drawString(font, line, x, y, color); y += font.lineHeight; }
    }
    public void renderTooltip(Font font, Component text, int x, int y) { if (owner != null) owner.showTooltip(pose, text, x, y); }
}

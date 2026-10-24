package dev.eyecrasher.socialrating.ui;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.narration.NarrationElementOutput;

public final class RatingLabel extends AbstractWidget {
    private final Font font;
    private RatingTooltip tooltip;
    public void setTooltip(RatingTooltip value) { tooltip = value; }
    public RatingLabel(int x, int y, int width, int height, Component text, Font font) {
        super(x, y, width, height, text); this.font = font;
    }
    public RatingLabel setMaxWidth(int maximum) { setWidth(maximum); return this; }
    public void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
        String text = getMessage().getString();
        if (font.width(text) > getWidth()) text = font.plainSubstrByWidth(text, Math.max(1, getWidth() - font.width("…"))) + "…";
        font.drawShadow(pose, text, this.x, this.y, 0xffeeeeee);
        if (tooltip != null && isHovered && Minecraft.getInstance().screen instanceof RatingScreen)
            ((RatingScreen) Minecraft.getInstance().screen).showTooltip(pose, tooltip.text, mouseX, mouseY);
    }
    public void updateNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}

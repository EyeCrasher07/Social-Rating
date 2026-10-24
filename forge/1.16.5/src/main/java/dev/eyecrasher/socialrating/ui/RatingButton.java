package dev.eyecrasher.socialrating.ui;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;

public final class RatingButton extends net.minecraft.client.gui.widget.button.Button {
    private RatingTooltip tooltip;
    public RatingButton(int x, int y, int width, int height, ITextComponent text, IPressable action) { super(x, y, width, height, text, action); }
    public void setTooltip(RatingTooltip value) { tooltip = value; }
    public int getX() { return x; }
    public int getY() { return y; }
    @Override public void renderButton(MatrixStack pose, int mouseX, int mouseY, float delta) {
        super.renderButton(pose, mouseX, mouseY, delta);
        if (tooltip != null && isHovered && Minecraft.getInstance().screen instanceof RatingScreen)
            ((RatingScreen) Minecraft.getInstance().screen).showTooltip(pose, tooltip.text, mouseX, mouseY);
    }
}

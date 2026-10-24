package dev.eyecrasher.socialrating.ui;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class RatingButton extends net.minecraft.client.gui.components.Button {
    private RatingTooltip tooltip;
    public RatingButton(int x, int y, int width, int height, Component text, OnPress action) { super(x, y, width, height, text, action); }
    public void setTooltip(RatingTooltip value) { tooltip = value; }
    public int getX() { return x; }
    public int getY() { return y; }
    @Override public void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
        super.renderButton(pose, mouseX, mouseY, delta);
        if (tooltip != null && isHovered && Minecraft.getInstance().screen instanceof RatingScreen)
            ((RatingScreen) Minecraft.getInstance().screen).showTooltip(pose, tooltip.text, mouseX, mouseY);
    }
}

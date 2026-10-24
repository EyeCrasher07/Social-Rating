package dev.eyecrasher.socialrating.mixin;

import dev.eyecrasher.socialrating.SocialRatingClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class HudHintMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void socialrating$hint(GuiGraphics graphics, float delta, CallbackInfo ci) {
        if (SocialRatingClient.INSTANCE != null) SocialRatingClient.INSTANCE.renderHint(graphics);
    }
}

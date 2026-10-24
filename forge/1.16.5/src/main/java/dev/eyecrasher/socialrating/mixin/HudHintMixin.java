package dev.eyecrasher.socialrating.mixin;

import dev.eyecrasher.socialrating.SocialRatingClient;
import dev.eyecrasher.socialrating.ui.RatingGraphics;
import net.minecraft.client.gui.IngameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IngameGui.class)
abstract class HudHintMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void socialrating$hint(com.mojang.blaze3d.matrix.MatrixStack pose, float delta, CallbackInfo ci) {
        if (SocialRatingClient.INSTANCE != null) SocialRatingClient.INSTANCE.renderHint(new RatingGraphics(pose, null));
    }
}

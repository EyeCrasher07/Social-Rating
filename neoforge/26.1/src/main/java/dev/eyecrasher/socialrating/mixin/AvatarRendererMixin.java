package dev.eyecrasher.socialrating.mixin;

import dev.eyecrasher.socialrating.SocialRatingClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void socialrating$extract(Avatar avatar, AvatarRenderState state, float delta, CallbackInfo ci) {
        var client = SocialRatingClient.INSTANCE;
        if (client == null || !(avatar instanceof Player player) || !client.config().showNameplates
                || state.nameTag == null || state.isInvisible || state.isDiscrete
                || state.distanceToCameraSq > client.nameplateDistance() * client.nameplateDistance()) return;
        var rating = client.nameplate(player.getUUID());
        if (rating != null) state.scoreText = rating;
    }
}

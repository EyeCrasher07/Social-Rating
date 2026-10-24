package dev.eyecrasher.socialrating.mixin;

import dev.eyecrasher.socialrating.SocialRatingClient;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("TAIL"))
    private void socialrating$extract(AbstractClientPlayer player, PlayerRenderState state, float delta, CallbackInfo ci) {
        var client = SocialRatingClient.INSTANCE;
        if (client == null || !client.config().showNameplates
                || state.nameTag == null || state.isInvisible || state.isDiscrete
                || state.distanceToCameraSq > client.nameplateDistance() * client.nameplateDistance()) return;
        var rating = client.nameplate(player.getUUID());
        if (rating != null) state.scoreText = rating;
    }
}

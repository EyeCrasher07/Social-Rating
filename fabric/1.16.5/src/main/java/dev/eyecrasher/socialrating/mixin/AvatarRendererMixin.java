package dev.eyecrasher.socialrating.mixin;

import dev.eyecrasher.socialrating.SocialRatingClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
abstract class AvatarRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    protected AvatarRendererMixin(EntityRenderDispatcher context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void socialrating$rating(AbstractClientPlayer player, Component name, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        var client = SocialRatingClient.INSTANCE;
        if (client == null || !client.config().showNameplates || player.isDiscrete() || player.isInvisible()
                || this.entityRenderDispatcher.distanceToSqr(player) > client.nameplateDistance() * client.nameplateDistance()) return;
        var rating = client.nameplate(player.getUUID());
        if (rating == null) return;

        // Match 26.3: the rating uses the native score line below the player's name.
        // Native rendering supplies the same camera attachment, scale, background and depth behavior.
        pose.pushPose();
        super.renderNameTag(player, rating, pose, buffers, light);
        pose.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
        super.renderNameTag(player, name, pose, buffers, light);
        pose.popPose();
        ci.cancel();
    }
}

package dev.eyecrasher.socialrating.mixin;

import dev.eyecrasher.socialrating.SocialRatingClient;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
abstract class AvatarRendererMixin extends LivingRenderer<AbstractClientPlayerEntity, PlayerModel<AbstractClientPlayerEntity>> {
    protected AvatarRendererMixin(EntityRendererManager context, PlayerModel<AbstractClientPlayerEntity> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/entity/player/AbstractClientPlayerEntity;Lnet/minecraft/util/text/ITextComponent;Lcom/mojang/blaze3d/matrix/MatrixStack;Lnet/minecraft/client/renderer/IRenderTypeBuffer;I)V", at = @At("HEAD"), cancellable = true)
    private void socialrating$rating(AbstractClientPlayerEntity player, ITextComponent name, MatrixStack pose, IRenderTypeBuffer buffers, int light, CallbackInfo ci) {
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

package draylar.inmis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

public class BackpackFeature extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final ContextKey<ItemStackRenderState> BACKPACK =
            new ContextKey<>(Identifier.fromNamespaceAndPath("inmis", "backpack"));

    public BackpackFeature(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        ItemStackRenderState backpack = state.getRenderData(BACKPACK);
        if (backpack == null || backpack.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        poseStack.translate(0, -0.2, -0.25);
        if (state.isCrouching) {
            poseStack.mulPose(Axis.XP.rotationDegrees(25));
            poseStack.translate(0, -0.2, 0);
        }
        backpack.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
package net.unfamily.repae2bridge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import net.unfamily.repae2bridge.block.entity.MatterAssemblerBlockEntity;

/**
 * Floating crafted item — Replicator-style progress interpolation (partialTicks) for smooth bob/scale.
 */
public class MatterAssemblerRenderer
        implements BlockEntityRenderer<MatterAssemblerBlockEntity, MatterAssemblerRenderState> {

    private static final float BOB_AMOUNT = 0.12f;

    private final ItemModelResolver itemModelResolver;

    public MatterAssemblerRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public MatterAssemblerRenderState createRenderState() {
        return new MatterAssemblerRenderState();
    }

    @Override
    public void extractRenderState(MatterAssemblerBlockEntity be, MatterAssemblerRenderState state,
            float partialTicks, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPos, crumblingOverlay);
        state.item.clear();
        state.hasItem = false;
        state.spin = 0;
        state.progress = 0;

        if (!be.isWorking() || be.getCraftingStack().isEmpty() || be.getLevel() == null) {
            return;
        }

        int max = Math.max(1, be.getEffectiveMaxProgress());
        // Same pattern as Replication ReplicatorRenderer: (progress + partialTicks) / max.
        state.progress = Mth.clamp((be.getProgress() + partialTicks) / max, 0f, 1f);
        state.hasItem = true;
        state.spin = (be.getLevel().getGameTime() + partialTicks) * be.getAnimationSpeed();
        this.itemModelResolver.updateForTopItem(
                state.item,
                be.getCraftingStack(),
                ItemDisplayContext.FIXED,
                be.getLevel(),
                null,
                (int) be.getBlockPos().asLong());
    }

    @Override
    public void submit(MatterAssemblerRenderState state, PoseStack poseStack, SubmitNodeCollector nodes,
            CameraRenderState cameraRenderState) {
        if (!state.hasItem) {
            return;
        }
        float p = state.progress;
        float scale = 0.35f + 0.25f * p;
        float bob = BOB_AMOUNT * p;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.38 + bob, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
        poseStack.scale(scale, scale, scale);
        state.item.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}

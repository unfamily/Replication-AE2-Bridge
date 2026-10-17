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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import net.unfamily.repae2bridge.block.entity.MatterAssemblerBlockEntity;

/**
 * Floating crafted item animation inspired by AE2 Molecular Assembler.
 */
public class MatterAssemblerRenderer
        implements BlockEntityRenderer<MatterAssemblerBlockEntity, MatterAssemblerRenderState> {

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

        if (!be.isWorking() || be.getCraftingStack().isEmpty() || be.getLevel() == null) {
            return;
        }

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
        poseStack.pushPose();
        poseStack.translate(0.5, 0.45, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
        poseStack.scale(0.5f, 0.5f, 0.5f);
        state.item.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}

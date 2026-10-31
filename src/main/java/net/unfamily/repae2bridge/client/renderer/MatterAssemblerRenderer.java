package net.unfamily.repae2bridge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.unfamily.repae2bridge.block.entity.MatterAssemblerBlockEntity;

/**
 * Floating crafted item — Replicator-style progress interpolation for smooth bob/scale.
 * Uses classic 1.21 BER API (no RenderState from 26+).
 */
public class MatterAssemblerRenderer implements BlockEntityRenderer<MatterAssemblerBlockEntity> {

    private static final float BOB_AMOUNT = 0.12f;

    public MatterAssemblerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MatterAssemblerBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!be.isWorking() || be.getCraftingStack().isEmpty() || be.getLevel() == null) {
            return;
        }

        ItemStack stack = be.getCraftingStack();
        int max = Math.max(1, be.getEffectiveMaxProgress());
        float progress = Mth.clamp((be.getProgress() + partialTick) / max, 0f, 1f);
        float spin = (be.getLevel().getGameTime() + partialTick) * be.getAnimationSpeed();
        float scale = 0.35f + 0.25f * progress;
        float bob = BOB_AMOUNT * progress;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.38 + bob, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.scale(scale, scale, scale);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                be.getLevel(),
                (int) be.getBlockPos().asLong());
        poseStack.popPose();
    }
}

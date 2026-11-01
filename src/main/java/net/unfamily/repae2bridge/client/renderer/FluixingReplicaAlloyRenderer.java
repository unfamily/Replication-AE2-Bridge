package net.unfamily.repae2bridge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.entity.FluixingReplicaAlloyBlockEntity;
import net.unfamily.repae2bridge.client.item.IrregularPulseProperty;

/**
 * Client-only pulsed cutout overlay. The opaque base is drawn by the chunk mesh;
 * light is sampled from neighbors so an opaque cube does not force light 0.
 */
public class FluixingReplicaAlloyRenderer implements BlockEntityRenderer<FluixingReplicaAlloyBlockEntity> {

    public static final ModelResourceLocation GREEN = standalone("block/fluixing_replica_alloy_block_overlay_green");
    public static final ModelResourceLocation PURPLE = standalone("block/fluixing_replica_alloy_block_overlay_purple");
    public static final ModelResourceLocation ORANGE = standalone("block/fluixing_replica_alloy_block_overlay_orange");

    public FluixingReplicaAlloyRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ModelResourceLocation standalone(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, path));
    }

    @Override
    public void render(FluixingReplicaAlloyBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) {
            return;
        }

        int phase = IrregularPulseProperty.overlayIndex(
                be.getLevel().getGameTime(), (int) be.getBlockPos().asLong());
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(switch (phase) {
            case 2 -> ORANGE;
            case 1 -> PURPLE;
            default -> GREEN;
        });

        int light = neighborLight(be.getLevel(), be.getBlockPos());
        BlockState state = be.getBlockState();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutout());
        ModelBlockRenderer renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        renderer.renderModel(
                poseStack.last(),
                buffer,
                state,
                model,
                1.0F,
                1.0F,
                1.0F,
                light,
                packedOverlay);
    }

    /** Max block/sky light across the six neighboring positions. */
    static int neighborLight(BlockAndTintGetter level, BlockPos pos) {
        int block = 0;
        int sky = 0;
        for (Direction direction : Direction.values()) {
            int packed = LevelRenderer.getLightColor(level, pos.relative(direction));
            block = Math.max(block, LightTexture.block(packed));
            sky = Math.max(sky, LightTexture.sky(packed));
        }
        return LightTexture.pack(block, sky);
    }
}

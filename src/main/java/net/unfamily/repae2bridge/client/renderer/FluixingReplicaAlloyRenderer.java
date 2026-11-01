package net.unfamily.repae2bridge.client.renderer;

import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.entity.FluixingReplicaAlloyBlockEntity;
import net.unfamily.repae2bridge.client.item.IrregularPulseProperty;

/**
 * Client-only pulsed cutout overlay. The opaque base is drawn by the chunk mesh;
 * light is sampled from neighbors so an opaque cube does not force light 0.
 */
public class FluixingReplicaAlloyRenderer
        implements BlockEntityRenderer<FluixingReplicaAlloyBlockEntity, FluixingReplicaAlloyRenderState> {

    public static final Identifier GREEN_ID =
            Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "block/fluixing_replica_alloy_block_overlay_green");
    public static final Identifier PURPLE_ID =
            Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "block/fluixing_replica_alloy_block_overlay_purple");
    public static final Identifier ORANGE_ID =
            Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "block/fluixing_replica_alloy_block_overlay_orange");

    public static final StandaloneModelKey<BlockStateModel> GREEN = new StandaloneModelKey<>(GREEN_ID::toString);
    public static final StandaloneModelKey<BlockStateModel> PURPLE = new StandaloneModelKey<>(PURPLE_ID::toString);
    public static final StandaloneModelKey<BlockStateModel> ORANGE = new StandaloneModelKey<>(ORANGE_ID::toString);

    private final BlockStateModel greenModel;
    private final BlockStateModel purpleModel;
    private final BlockStateModel orangeModel;

    public FluixingReplicaAlloyRenderer(BlockEntityRendererProvider.Context context) {
        var models = Minecraft.getInstance().getModelManager();
        this.greenModel = Objects.requireNonNull(models.getStandaloneModel(GREEN));
        this.purpleModel = Objects.requireNonNull(models.getStandaloneModel(PURPLE));
        this.orangeModel = Objects.requireNonNull(models.getStandaloneModel(ORANGE));
    }

    @Override
    public FluixingReplicaAlloyRenderState createRenderState() {
        return new FluixingReplicaAlloyRenderState();
    }

    @Override
    public void extractRenderState(FluixingReplicaAlloyBlockEntity be, FluixingReplicaAlloyRenderState state,
            float partialTicks, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPos, crumblingOverlay);

        if (be.getLevel() == null) {
            return;
        }

        state.lightCoords = neighborLight(be.getLevel(), be.getBlockPos());

        int phase = IrregularPulseProperty.overlayIndex(
                be.getLevel().getGameTime(), (int) be.getBlockPos().asLong());
        BlockStateModel model = switch (phase) {
            case 2 -> orangeModel;
            case 1 -> purpleModel;
            default -> greenModel;
        };

        var parts = state.modelRenderState.setupModel(
                new Matrix4f(),
                model.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT));
        model.collectParts(state.modelRenderState.scratchRandomSource(42L), parts);
    }

    @Override
    public void submit(FluixingReplicaAlloyRenderState state, PoseStack poseStack, SubmitNodeCollector nodes,
            CameraRenderState cameraRenderState) {
        state.modelRenderState.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
    }

    /** Max block/sky light across the six neighboring positions. */
    static int neighborLight(BlockAndLightGetter level, BlockPos pos) {
        int block = 0;
        int sky = 0;
        for (Direction direction : Direction.values()) {
            int packed = LevelRenderer.getLightCoords(level, pos.relative(direction));
            block = Math.max(block, LightCoordsUtil.block(packed));
            sky = Math.max(sky, LightCoordsUtil.sky(packed));
        }
        return LightCoordsUtil.pack(block, sky);
    }
}

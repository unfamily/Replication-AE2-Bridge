package net.unfamily.repae2bridge.client.renderer;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.client.render.shader.ReplicationRenderTypes;
import com.buuz135.replication.client.render.shader.ShaderTexture;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.chamber.ChamberAe2Display;
import net.unfamily.repae2bridge.block.chamber.ChamberStructure;
import net.unfamily.repae2bridge.block.custom.ReplicationUnitBlock;
import net.unfamily.repae2bridge.block.entity.ReplicationUnitBlockEntity;

/**
 * Leader-only: matter_pipe hull around the chamber unit volume (Sampler0 by MODE).
 * Mode glow lives on the baked block model as a face overlay.
 */
public class ReplicationUnitRenderer implements BlockEntityRenderer<ReplicationUnitBlockEntity> {

    private static final ResourceLocation SHADER_IDLE =
            ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "textures/block/chamber_shader_idle.png");
    private static final ResourceLocation SHADER_RUNNING =
            ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "textures/block/chamber_shader_running.png");
    private static final ResourceLocation SHADER_ANOMALY =
            ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "textures/block/chamber_shader_anomaly.png");

    private static final float OUTER_PAD = 0.2f;

    public ReplicationUnitRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(ReplicationUnitBlockEntity blockEntity) {
        ChamberStructure.FormedBox box = resolveBox(blockEntity);
        if (box != null) {
            return AABB.encapsulatingFullBlocks(box.min(), box.max()).inflate(0.5);
        }
        return BlockEntityRenderer.super.getRenderBoundingBox(blockEntity);
    }

    @Override
    public void render(ReplicationUnitBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        var bs = be.getBlockState();
        boolean formed = bs.hasProperty(ReplicationUnitBlock.FORMED) && bs.getValue(ReplicationUnitBlock.FORMED);
        boolean powered = bs.hasProperty(ReplicationUnitBlock.POWERED) && bs.getValue(ReplicationUnitBlock.POWERED);
        if (!formed || !powered || be.getLevel() == null) {
            return;
        }

        be.updateClientModeBlend(partialTick);
        float blend = be.getClientModeBlend();
        ResourceLocation texFrom = modeShader(be.getClientModeFrom());
        ResourceLocation texTo = modeShader(be.getClientModeTo());
        ResourceLocation tex;
        if (blend < 0.999f && texFrom != null && !texFrom.equals(texTo)) {
            tex = blend < 0.5f ? texFrom : texTo;
        } else {
            tex = texTo;
        }
        if (tex == null) {
            return;
        }

        ChamberStructure.FormedBox box = resolveBox(be);
        if (box == null) {
            return;
        }

        BlockPos leader = ChamberAe2Display.findLeaderUnit(be.getLevel(), box);
        if (leader != null && !leader.equals(be.getBlockPos())) {
            return;
        }

        BlockPos min = box.min().offset(1, 1, 1);
        BlockPos max = box.max().offset(-1, -1, -1);
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            min = box.min().offset(1, 1, 1);
            max = min;
        }

        BlockPos origin = be.getBlockPos();
        float relMinX = (min.getX() - origin.getX()) - OUTER_PAD;
        float relMinY = (min.getY() - origin.getY()) - OUTER_PAD;
        float relMinZ = (min.getZ() - origin.getZ()) - OUTER_PAD;
        float relMaxX = (max.getX() - origin.getX()) + 1f + OUTER_PAD;
        float relMaxY = (max.getY() - origin.getY()) + 1f + OUTER_PAD;
        float relMaxZ = (max.getZ() - origin.getZ()) + 1f + OUTER_PAD;

        AABB bounds = new AABB(relMinX, relMinY, relMinZ, relMaxX, relMaxY, relMaxZ);
        RenderType renderType = ReplicationRenderTypes.getRenderType("matter_pipe")
                .using(List.of(new ShaderTexture(tex)));
        VertexConsumer buffer = bufferSource.getBuffer(renderType);
        int color = 0xCCFFFFFF;
        poseStack.pushPose();
        texturedBox(poseStack.last(), buffer, bounds, color, packedLight);
        poseStack.popPose();
    }

    @Nullable
    private static ChamberStructure.FormedBox resolveBox(ReplicationUnitBlockEntity be) {
        ChamberStructure.FormedBox box = be.getLastBox();
        if (box == null && be.getLevel() != null) {
            box = ChamberStructure.findAround(be.getLevel(), be.getBlockPos());
        }
        return box;
    }

    private static ResourceLocation modeShader(int mode) {
        return switch (mode) {
            case 1 -> SHADER_RUNNING;
            case 2 -> SHADER_ANOMALY;
            default -> SHADER_IDLE;
        };
    }

    private static void texturedBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, int light) {
        float x1 = (float) box.minX;
        float x2 = (float) box.maxX;
        float y1 = (float) box.minY;
        float y2 = (float) box.maxY;
        float z1 = (float) box.minZ;
        float z2 = (float) box.maxZ;
        texturedQuad(pose, buffer, color, light, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, 0, 0, -1);
        texturedQuad(pose, buffer, color, light, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, 0, 0, 1);
        texturedQuad(pose, buffer, color, light, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0, -1, 0);
        texturedQuad(pose, buffer, color, light, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, 0, 1, 0);
        texturedQuad(pose, buffer, color, light, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1, 0, 0);
        texturedQuad(pose, buffer, color, light, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, 1, 0, 0);
    }

    private static void texturedQuad(PoseStack.Pose pose, VertexConsumer buffer, int color, int light,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float nx, float ny, float nz) {
        texturedVertex(pose, buffer, color, light, x1, y1, z1, 0, 1, nx, ny, nz);
        texturedVertex(pose, buffer, color, light, x2, y2, z2, 0, 0, nx, ny, nz);
        texturedVertex(pose, buffer, color, light, x3, y3, z3, 1, 0, nx, ny, nz);
        texturedVertex(pose, buffer, color, light, x4, y4, z4, 1, 1, nx, ny, nz);
    }

    private static void texturedVertex(PoseStack.Pose pose, VertexConsumer buffer, int color, int light,
            float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        buffer.addVertex(pose.pose(), x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(0)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}

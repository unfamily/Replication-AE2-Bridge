package net.unfamily.repae2bridge.client.renderer;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.client.render.shader.ReplicationRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.chamber.ChamberAe2Display;
import net.unfamily.repae2bridge.block.chamber.ChamberStructure;
import net.unfamily.repae2bridge.block.custom.ReplicationUnitBlock;
import net.unfamily.repae2bridge.block.entity.ReplicationUnitBlockEntity;

/**
 * Leader-only: matter_pipe hull around the chamber unit volume (Sampler0 by MODE).
 * Mode glow lives on the baked block model as a face overlay.
 */
public class ReplicationUnitRenderer
        implements BlockEntityRenderer<ReplicationUnitBlockEntity, ReplicationUnitRenderState> {

    private static final Identifier SHADER_IDLE =
            Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "textures/block/chamber_shader_idle.png");
    private static final Identifier SHADER_RUNNING =
            Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "textures/block/chamber_shader_running.png");
    private static final Identifier SHADER_ANOMALY =
            Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "textures/block/chamber_shader_anomaly.png");

    /** Push hull faces into the surrounding glass. */
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
    public ReplicationUnitRenderState createRenderState() {
        return new ReplicationUnitRenderState();
    }

    @Override
    public void extractRenderState(ReplicationUnitBlockEntity be, ReplicationUnitRenderState state,
            float partialTicks, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPos, crumblingOverlay);
        state.drawHull = false;

        var bs = be.getBlockState();
        boolean formed = bs.hasProperty(ReplicationUnitBlock.FORMED) && bs.getValue(ReplicationUnitBlock.FORMED);
        boolean powered = bs.hasProperty(ReplicationUnitBlock.POWERED) && bs.getValue(ReplicationUnitBlock.POWERED);
        if (!formed || !powered || be.getLevel() == null) {
            return;
        }

        be.updateClientModeBlend(partialTicks);
        float blend = be.getClientModeBlend();
        state.modeBlend = blend;
        state.hullTexFrom = modeShader(be.getClientModeFrom());
        state.hullTexTo = modeShader(be.getClientModeTo());

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
        state.relMinX = (min.getX() - origin.getX()) - OUTER_PAD;
        state.relMinY = (min.getY() - origin.getY()) - OUTER_PAD;
        state.relMinZ = (min.getZ() - origin.getZ()) - OUTER_PAD;
        state.relMaxX = (max.getX() - origin.getX()) + 1f + OUTER_PAD;
        state.relMaxY = (max.getY() - origin.getY()) + 1f + OUTER_PAD;
        state.relMaxZ = (max.getZ() - origin.getZ()) + 1f + OUTER_PAD;
        state.fillColor = 0xCCFFFFFF;
        state.drawHull = true;
    }

    @Nullable
    private static ChamberStructure.FormedBox resolveBox(ReplicationUnitBlockEntity be) {
        ChamberStructure.FormedBox box = be.getLastBox();
        if (box == null && be.getLevel() != null) {
            box = ChamberStructure.findAround(be.getLevel(), be.getBlockPos());
        }
        return box;
    }

    private static Identifier modeShader(int mode) {
        return switch (mode) {
            case 1 -> SHADER_RUNNING;
            case 2 -> SHADER_ANOMALY;
            default -> SHADER_IDLE;
        };
    }

    @Override
    public void submit(ReplicationUnitRenderState state, PoseStack poseStack, SubmitNodeCollector nodes,
            CameraRenderState cameraRenderState) {
        if (!state.drawHull) {
            return;
        }
        AABB bounds = new AABB(state.relMinX, state.relMinY, state.relMinZ,
                state.relMaxX, state.relMaxY, state.relMaxZ);
        float blend = Mth.clamp(state.modeBlend, 0f, 1f);
        Identifier tex;
        if (blend < 0.999f && state.hullTexFrom != null && !state.hullTexFrom.equals(state.hullTexTo)) {
            tex = blend < 0.5f ? state.hullTexFrom : state.hullTexTo;
        } else {
            tex = state.hullTexTo;
        }
        if (tex == null) {
            return;
        }
        nodes.order(1).submitCustomGeometry(poseStack, ReplicationRenderTypes.matter(tex, true),
                (pose, buffer) -> texturedBox(pose, buffer, bounds, state.fillColor, state.lightCoords));
    }

    private static void texturedBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, int light) {
        float x1 = (float) box.minX;
        float x2 = (float) box.maxX;
        float y1 = (float) box.minY;
        float y2 = (float) box.maxY;
        float z1 = (float) box.minZ;
        float z2 = (float) box.maxZ;
        float u0 = 0f;
        float v0 = 0f;
        float u1 = 1f;
        float v1 = 1f;
        texturedQuad(pose, buffer, color, light, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, u0, v0, u1, v1, 0, 0, -1);
        texturedQuad(pose, buffer, color, light, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, u0, v0, u1, v1, 0, 0, 1);
        texturedQuad(pose, buffer, color, light, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, u0, v0, u1, v1, 0, -1, 0);
        texturedQuad(pose, buffer, color, light, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, u0, v0, u1, v1, 0, 1, 0);
        texturedQuad(pose, buffer, color, light, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, u0, v0, u1, v1, -1, 0, 0);
        texturedQuad(pose, buffer, color, light, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, u0, v0, u1, v1, 1, 0, 0);
    }

    private static void texturedQuad(PoseStack.Pose pose, VertexConsumer buffer, int color, int light,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        texturedVertex(pose, buffer, color, light, x1, y1, z1, u0, v1, nx, ny, nz);
        texturedVertex(pose, buffer, color, light, x2, y2, z2, u0, v0, nx, ny, nz);
        texturedVertex(pose, buffer, color, light, x3, y3, z3, u1, v0, nx, ny, nz);
        texturedVertex(pose, buffer, color, light, x4, y4, z4, u1, v1, nx, ny, nz);
    }

    private static void texturedVertex(PoseStack.Pose pose, VertexConsumer buffer, int color, int light,
            float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        buffer.addVertex(pose.pose(), x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}

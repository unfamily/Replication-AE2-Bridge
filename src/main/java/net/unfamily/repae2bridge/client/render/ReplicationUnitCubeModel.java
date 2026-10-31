package net.unfamily.repae2bridge.client.render;

import java.util.EnumSet;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.model.data.ModelData;

import appeng.blockentity.crafting.CraftingCubeModelData;
import appeng.client.render.CubeBuilder;
import appeng.client.render.MaterialUtil;
import appeng.core.AppEng;
import appeng.util.Platform;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.custom.ReplicationUnitBlock;

/**
 * AE2 crafting-cube style model for formed replication units:
 * connected rings + inner base + fixed overlay + mode overlay (on the block faces).
 */
public final class ReplicationUnitCubeModel implements DynamicBlockStateModel, ModelDebugName {

    private static final Material RING_CORNER = ae2("ring_corner");
    private static final Material RING_SIDE_HOR = ae2("ring_side_hor");
    private static final Material RING_SIDE_VER = ae2("ring_side_ver");
    /** Layer 1: formed connected inner (same role as AE2 light_base). */
    private static final Material INNER_BASE = ae2("light_base");
    /** Layer 2: fixed overlay. */
    private static final Material FIXED_OVERLAY = ours("replication_unit_base");
    private static final Material IDLE_OVERLAY = ours("replication_unit_idle");
    private static final Material RUNNING_OVERLAY = ours("replication_unit_running");
    private static final Material ANOMALY_OVERLAY = ours("replication_unit_anomaly");

    private final Material.Baked ringCorner;
    private final Material.Baked ringHor;
    private final Material.Baked ringVer;
    private final Material.Baked innerBase;
    private final Material.Baked fixedOverlay;
    private final Material.Baked idleOverlay;
    private final Material.Baked runningOverlay;
    private final Material.Baked anomalyOverlay;
    private final int materialFlags;

    private ReplicationUnitCubeModel(MaterialBaker baker) {
        this.ringCorner = baker.get(RING_CORNER, this);
        this.ringHor = baker.get(RING_SIDE_HOR, this);
        this.ringVer = baker.get(RING_SIDE_VER, this);
        this.innerBase = baker.get(INNER_BASE, this);
        this.fixedOverlay = baker.get(FIXED_OVERLAY, this);
        this.idleOverlay = baker.get(IDLE_OVERLAY, this);
        this.runningOverlay = baker.get(RUNNING_OVERLAY, this);
        this.anomalyOverlay = baker.get(ANOMALY_OVERLAY, this);
        this.materialFlags = MaterialUtil.getMaterialFlags(ringCorner, ringHor, ringVer, innerBase, fixedOverlay,
                idleOverlay, runningOverlay, anomalyOverlay);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
            List<BlockStateModelPart> parts) {
        EnumSet<Direction> connections = getConnections(level.getModelData(pos));

        var quadCollection = new QuadCollection.Builder();
        for (var cullFace : Direction.values()) {
            CubeBuilder builder = new CubeBuilder(quad -> quadCollection.addCulledFace(cullFace, quad));
            builder.setDrawFaces(EnumSet.of(cullFace));

            addRing(builder, cullFace, connections);

            float x2 = connections.contains(Direction.EAST) ? 16 : 13.01f;
            float x1 = connections.contains(Direction.WEST) ? 0 : 2.99f;
            float y2 = connections.contains(Direction.UP) ? 16 : 13.01f;
            float y1 = connections.contains(Direction.DOWN) ? 0 : 2.99f;
            float z2 = connections.contains(Direction.SOUTH) ? 16 : 13.01f;
            float z1 = connections.contains(Direction.NORTH) ? 0 : 2.99f;

            switch (cullFace) {
                case DOWN, UP -> {
                    y1 = 0;
                    y2 = 16;
                }
                case NORTH, SOUTH -> {
                    z1 = 0;
                    z2 = 16;
                }
                case WEST, EAST -> {
                    x1 = 0;
                    x2 = 16;
                }
            }

            addInnerCube(state, builder, x1, y1, z1, x2, y2, z2);
        }

        parts.add(new SimpleModelWrapper(quadCollection.build(), false, ringCorner));
    }

    private void addInnerCube(BlockState state, CubeBuilder builder, float x1, float y1, float z1, float x2, float y2,
            float z2) {
        // 1) Connected formed unit base
        builder.setTexture(innerBase);
        builder.addCube(x1, y1, z1, x2, y2, z2);

        // 2) Fixed overlay
        builder.setTexture(fixedOverlay);
        builder.addCube(x1, y1, z1, x2, y2, z2);

        // 3) Mode overlay — same faces as the block (emissive), not a detached BER cube
        builder.setEmissiveMaterial(true);
        builder.setTexture(modeOverlay(state));
        builder.addCube(x1, y1, z1, x2, y2, z2);
        builder.setEmissiveMaterial(false);
    }

    private Material.Baked modeOverlay(BlockState state) {
        int mode = state.hasProperty(ReplicationUnitBlock.MODE) ? state.getValue(ReplicationUnitBlock.MODE) : 0;
        return switch (mode) {
            case 1 -> runningOverlay;
            case 2 -> anomalyOverlay;
            default -> idleOverlay;
        };
    }

    private void addRing(CubeBuilder builder, Direction side, EnumSet<Direction> connections) {
        builder.setTexture(ringCorner);
        addCornerCap(builder, connections, side, Direction.UP, Direction.EAST, Direction.NORTH);
        addCornerCap(builder, connections, side, Direction.UP, Direction.EAST, Direction.SOUTH);
        addCornerCap(builder, connections, side, Direction.UP, Direction.WEST, Direction.NORTH);
        addCornerCap(builder, connections, side, Direction.UP, Direction.WEST, Direction.SOUTH);
        addCornerCap(builder, connections, side, Direction.DOWN, Direction.EAST, Direction.NORTH);
        addCornerCap(builder, connections, side, Direction.DOWN, Direction.EAST, Direction.SOUTH);
        addCornerCap(builder, connections, side, Direction.DOWN, Direction.WEST, Direction.NORTH);
        addCornerCap(builder, connections, side, Direction.DOWN, Direction.WEST, Direction.SOUTH);

        for (Direction a : Direction.values()) {
            if (a == side || a == side.getOpposite()) {
                continue;
            }

            if (side.getAxis() != Axis.Y
                    && (a == Direction.NORTH || a == Direction.EAST || a == Direction.WEST || a == Direction.SOUTH)) {
                builder.setTexture(ringVer);
            } else if (side.getAxis() == Axis.Y && (a == Direction.EAST || a == Direction.WEST)) {
                builder.setTexture(ringVer);
            } else {
                builder.setTexture(ringHor);
            }

            if (!connections.contains(a)) {
                float x1 = 0, y1 = 0, z1 = 0, x2 = 16, y2 = 16, z2 = 16;
                switch (a) {
                    case DOWN -> {
                        y1 = 0;
                        y2 = 3;
                    }
                    case UP -> {
                        y1 = 13.0f;
                        y2 = 16;
                    }
                    case WEST -> {
                        x1 = 0;
                        x2 = 3;
                    }
                    case EAST -> {
                        x1 = 13;
                        x2 = 16;
                    }
                    case NORTH -> {
                        z1 = 0;
                        z2 = 3;
                    }
                    case SOUTH -> {
                        z1 = 13;
                        z2 = 16;
                    }
                }

                Direction perpendicular = Platform.rotateAround(a, side);
                for (Direction cornerCandidate : EnumSet.of(perpendicular, perpendicular.getOpposite())) {
                    if (!connections.contains(cornerCandidate)) {
                        switch (cornerCandidate) {
                            case DOWN -> y1 = 3;
                            case UP -> y2 = 13;
                            case NORTH -> z1 = 3;
                            case SOUTH -> z2 = 13;
                            case WEST -> x1 = 3;
                            case EAST -> x2 = 13;
                            default -> {
                            }
                        }
                    }
                }

                builder.addCube(x1, y1, z1, x2, y2, z2);
            }
        }
    }

    private static void addCornerCap(CubeBuilder builder, EnumSet<Direction> connections, Direction side,
            Direction down, Direction west, Direction north) {
        if (connections.contains(down) || connections.contains(west) || connections.contains(north)) {
            return;
        }
        if (side != down && side != west && side != north) {
            return;
        }

        float x1 = west == Direction.WEST ? 0 : 13;
        float y1 = down == Direction.DOWN ? 0 : 13;
        float z1 = north == Direction.NORTH ? 0 : 13;
        float x2 = west == Direction.WEST ? 3 : 16;
        float y2 = down == Direction.DOWN ? 3 : 16;
        float z2 = north == Direction.NORTH ? 3 : 16;
        builder.addCube(x1, y1, z1, x2, y2, z2);
    }

    private static EnumSet<Direction> getConnections(ModelData modelData) {
        if (modelData.has(CraftingCubeModelData.CONNECTIONS)) {
            return modelData.get(CraftingCubeModelData.CONNECTIONS);
        }
        return EnumSet.noneOf(Direction.class);
    }

    @Override
    public Material.Baked particleMaterial() {
        return ringCorner;
    }

    @Override
    public @BakedQuad.MaterialFlags int materialFlags() {
        return materialFlags;
    }

    @Override
    public String debugName() {
        return "rep_ae2_bridge:replication_unit_cube";
    }

    private static Material ae2(String name) {
        return new Material(AppEng.makeId("block/crafting/" + name));
    }

    private static Material ours(String name) {
        return new Material(Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "block/" + name));
    }

    public record Unbaked() implements CustomUnbakedBlockStateModel {
        public static final Identifier ID =
                Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "replication_unit_cube");
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            return new ReplicationUnitCubeModel(baker.materials());
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
        }

        @Override
        public MapCodec<Unbaked> codec() {
            return MAP_CODEC;
        }
    }
}

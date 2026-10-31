package net.unfamily.repae2bridge.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;

import appeng.blockentity.crafting.CraftingCubeModelData;
import appeng.client.render.cablebus.CubeBuilder;
import appeng.util.Platform;

import net.unfamily.repae2bridge.block.custom.ReplicationUnitBlock;

/**
 * AE2 crafting-cube style model for formed replication units:
 * connected rings + light_base + fixed overlay + mode overlay.
 */
public class ReplicationUnitFormedBakedModel implements IDynamicBakedModel {

    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.CUTOUT);

    private final TextureAtlasSprite ringCorner;
    private final TextureAtlasSprite ringHor;
    private final TextureAtlasSprite ringVer;
    private final TextureAtlasSprite innerBase;
    private final TextureAtlasSprite fixedOverlay;
    private final TextureAtlasSprite idleOverlay;
    private final TextureAtlasSprite runningOverlay;
    private final TextureAtlasSprite anomalyOverlay;

    public ReplicationUnitFormedBakedModel(
            TextureAtlasSprite ringCorner,
            TextureAtlasSprite ringHor,
            TextureAtlasSprite ringVer,
            TextureAtlasSprite innerBase,
            TextureAtlasSprite fixedOverlay,
            TextureAtlasSprite idleOverlay,
            TextureAtlasSprite runningOverlay,
            TextureAtlasSprite anomalyOverlay) {
        this.ringCorner = ringCorner;
        this.ringHor = ringHor;
        this.ringVer = ringVer;
        this.innerBase = innerBase;
        this.fixedOverlay = fixedOverlay;
        this.idleOverlay = idleOverlay;
        this.runningOverlay = runningOverlay;
        this.anomalyOverlay = anomalyOverlay;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData extraData, @Nullable RenderType renderType) {
        if (side == null) {
            return Collections.emptyList();
        }

        EnumSet<Direction> connections = getConnections(extraData);
        List<BakedQuad> quads = new ArrayList<>();
        CubeBuilder builder = new CubeBuilder(quads);
        builder.setDrawFaces(EnumSet.of(side));

        addRing(builder, side, connections);

        float x2 = connections.contains(Direction.EAST) ? 16 : 13.01f;
        float x1 = connections.contains(Direction.WEST) ? 0 : 2.99f;
        float y2 = connections.contains(Direction.UP) ? 16 : 13.01f;
        float y1 = connections.contains(Direction.DOWN) ? 0 : 2.99f;
        float z2 = connections.contains(Direction.SOUTH) ? 16 : 13.01f;
        float z1 = connections.contains(Direction.NORTH) ? 0 : 2.99f;

        switch (side) {
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
        return quads;
    }

    private void addInnerCube(@Nullable BlockState state, CubeBuilder builder, float x1, float y1, float z1, float x2,
            float y2, float z2) {
        builder.setTexture(innerBase);
        builder.addCube(x1, y1, z1, x2, y2, z2);

        builder.setTexture(fixedOverlay);
        builder.addCube(x1, y1, z1, x2, y2, z2);

        builder.setEmissiveMaterial(true);
        builder.setTexture(modeOverlay(state));
        builder.addCube(x1, y1, z1, x2, y2, z2);
        builder.setEmissiveMaterial(false);
    }

    private TextureAtlasSprite modeOverlay(@Nullable BlockState state) {
        int mode = 0;
        if (state != null && state.hasProperty(ReplicationUnitBlock.MODE)) {
            mode = state.getValue(ReplicationUnitBlock.MODE);
        }
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
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return ringCorner;
    }

    @Override
    public boolean usesBlockLight() {
        return false;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return RENDER_TYPES;
    }
}

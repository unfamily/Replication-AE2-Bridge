package net.unfamily.repae2bridge.block.chamber;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import appeng.core.definitions.AEBlocks;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.block.custom.ChamberFrameBlock;
import net.unfamily.repae2bridge.block.custom.ReplicationUnitBlock;
import net.unfamily.repae2bridge.block.entity.ReplicationUnitBlockEntity;

/**
 * Validates a Matter Assembling Chamber cuboid around a replication unit.
 * Borders: frames on edges/corners; faces may be chamber frames and/or AE2 quartz / vibrant glass.
 * AE2 cables connect to formed chamber frames (not to glass faces).
 */
public final class ChamberStructure {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ChamberStructure() {
    }

    public record FormedBox(BlockPos min, BlockPos max) {
        public int sizeX() {
            return max.getX() - min.getX() + 1;
        }

        public int sizeY() {
            return max.getY() - min.getY() + 1;
        }

        public int sizeZ() {
            return max.getZ() - min.getZ() + 1;
        }
    }

    /**
     * Walks outward from {@code origin} until shell blocks (frame, AE2 glass, or AE2 cable) are found
     * on each axis, then validates the cuboid.
     */
    @Nullable
    public static FormedBox findAround(Level level, BlockPos origin) {
        if (Config.chamberMaxSize < Config.chamberMinSize || Config.chamberMaxSize <= 0) {
            if (Config.enableDebugLogging) {
                LOGGER.debug("Chamber skip: invalid config min={} max={}", Config.chamberMinSize, Config.chamberMaxSize);
            }
            return null;
        }
        Integer minX = findShellOnAxis(level, origin, Direction.WEST);
        Integer maxX = findShellOnAxis(level, origin, Direction.EAST);
        Integer minY = findShellOnAxis(level, origin, Direction.DOWN);
        Integer maxY = findShellOnAxis(level, origin, Direction.UP);
        Integer minZ = findShellOnAxis(level, origin, Direction.NORTH);
        Integer maxZ = findShellOnAxis(level, origin, Direction.SOUTH);
        if (minX == null || maxX == null || minY == null || maxY == null || minZ == null || maxZ == null) {
            if (Config.enableDebugLogging) {
                LOGGER.debug("Chamber shell missing from {} (x={}..{} y={}..{} z={}..{})",
                        origin, minX, maxX, minY, maxY, minZ, maxZ);
            }
            return null;
        }
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);
        if (!isValid(level, min, max)) {
            return null;
        }
        return new FormedBox(min, max);
    }

    /**
     * Schedules nearby replication units to re-check formation soon (e.g. after frame/glass/cable place).
     */
    public static void notifyNearbyUnits(Level level, BlockPos origin) {
        if (level.isClientSide()) {
            return;
        }
        int r = Math.max(Config.chamberMaxSize > 0 ? Config.chamberMaxSize : 7, 3);
        BlockPos.betweenClosedStream(origin.offset(-r, -r, -r), origin.offset(r, r, r)).forEach(pos -> {
            if (level.getBlockEntity(pos) instanceof ReplicationUnitBlockEntity unit) {
                // requestRecheck itself coalesces already-queued units.
                unit.requestRecheck();
            }
        });
    }

    @Nullable
    private static Integer findShellOnAxis(Level level, BlockPos origin, Direction dir) {
        int maxDist = Math.max(Config.chamberMaxSize, 3) - 1;
        for (int d = 1; d <= maxDist; d++) {
            BlockPos pos = origin.relative(dir, d);
            BlockState state = level.getBlockState(pos);
            if (isShell(state)) {
                return switch (dir.getAxis()) {
                    case X -> pos.getX();
                    case Y -> pos.getY();
                    case Z -> pos.getZ();
                };
            }
            Block block = state.getBlock();
            if (!state.isAir() && !(block instanceof ReplicationUnitBlock)) {
                // Solid obstruction that is not part of the shell.
                if (Config.enableDebugLogging) {
                    LOGGER.debug("Chamber path blocked at {} by {}", pos, block);
                }
                return null;
            }
        }
        return null;
    }

    public static boolean isValid(Level level, BlockPos min, BlockPos max) {
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        if (sizeX < Config.chamberMinSize || sizeX > Config.chamberMaxSize
                || sizeY < Config.chamberMinSize || sizeY > Config.chamberMaxSize
                || sizeZ < Config.chamberMinSize || sizeZ > Config.chamberMaxSize) {
            if (Config.enableDebugLogging) {
                LOGGER.debug("Chamber invalid size {}x{}x{} at {}..{}", sizeX, sizeY, sizeZ, min, max);
            }
            return false;
        }

        boolean hasUnit = false;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    boolean onX = x == min.getX() || x == max.getX();
                    boolean onY = y == min.getY() || y == max.getY();
                    boolean onZ = z == min.getZ() || z == max.getZ();
                    int faces = (onX ? 1 : 0) + (onY ? 1 : 0) + (onZ ? 1 : 0);
                    if (faces >= 2) {
                        if (!(state.getBlock() instanceof ChamberFrameBlock)) {
                            if (Config.enableDebugLogging) {
                                LOGGER.debug("Chamber edge/corner missing frame at {} ({})", pos, state.getBlock());
                            }
                            return false;
                        }
                    } else if (faces == 1) {
                        if (!isFaceShell(state)) {
                            if (Config.enableDebugLogging) {
                                LOGGER.debug("Chamber face invalid at {} ({})", pos, state.getBlock());
                            }
                            return false;
                        }
                    } else {
                        Block block = state.getBlock();
                        if (block instanceof ReplicationUnitBlock) {
                            hasUnit = true;
                        } else if (!state.isAir()) {
                            if (Config.enableDebugLogging) {
                                LOGGER.debug("Chamber interior blocked at {} ({})", pos, block);
                            }
                            return false;
                        }
                    }
                }
            }
        }
        if (!hasUnit) {
            if (Config.enableDebugLogging) {
                LOGGER.debug("Chamber has no replication unit inside {}..{}", min, max);
            }
            return false;
        }
        return true;
    }

    private static boolean isShell(BlockState state) {
        return isFaceShell(state);
    }

    /** Face (or ray-hit shell): chamber frame and/or AE2 quartz glass. */
    private static boolean isFaceShell(BlockState state) {
        Block block = state.getBlock();
        // Full frame faces are valid (same role as glass panels).
        if (block instanceof ChamberFrameBlock) {
            return true;
        }
        return block == AEBlocks.QUARTZ_GLASS.block()
                || block == AEBlocks.QUARTZ_VIBRANT_GLASS.block();
    }
}

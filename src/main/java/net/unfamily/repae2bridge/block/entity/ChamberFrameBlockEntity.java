package net.unfamily.repae2bridge.block.entity;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;

import net.unfamily.repae2bridge.block.chamber.ChamberStructure;
import net.unfamily.repae2bridge.block.custom.ChamberFrameBlock;

/**
 * Chamber frame AE2 host: when part of a formed chamber, exposes an in-world grid node
 * so cables connect against the frame shell (no channel cost — conduit only).
 */
public class ChamberFrameBlockEntity extends BlockEntity implements IInWorldGridNodeHost {

    private IManagedGridNode mainNode = createMainNode();
    private boolean chamberOnline;

    /** Coalesce multi-unit unform in the same tick to one frame pass. */
    private static long lastBoxKey = Long.MIN_VALUE;
    private static long lastBoxTick = Long.MIN_VALUE;
    private static boolean lastBoxOnline;

    public ChamberFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHAMBER_FRAME_BE.get(), pos, state);
    }

    private IManagedGridNode createMainNode() {
        return GridHelper
                .createManagedNode(this, new IGridNodeListener<ChamberFrameBlockEntity>() {
                    @Override
                    public void onSaveChanges(ChamberFrameBlockEntity nodeOwner, IGridNode node) {
                        nodeOwner.setChanged();
                    }
                })
                // No visual: chamber appears once via the leader replication unit.
                .setVisualRepresentation((appeng.api.stacks.AEItemKey) null)
                .setIdlePowerUsage(0.0)
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setTagName("chamber_frame");
    }

    /**
     * Apply chamber online state without scanning for units (used when box is already known).
     */
    public void setChamberOnline(boolean online) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (online == chamberOnline && (!online || mainNode.isReady())) {
            return;
        }
        chamberOnline = online;
        if (chamberOnline) {
            ensureAe2Node();
        } else {
            destroyAe2Node();
        }
        setChanged();
    }

    /**
     * Re-evaluates whether this frame belongs to a formed chamber (place/load only).
     * Prefer {@link #setFramesInBox} when the chamber box is already known.
     */
    public void refreshChamberLink() {
        if (level == null || level.isClientSide()) {
            return;
        }
        setChamberOnline(isNearFormedUnit());
    }

    /** Cheap check: any formed unit within max chamber radius (no full findAround per frame). */
    private boolean isNearFormedUnit() {
        if (level == null) {
            return false;
        }
        int r = 7;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    if (level.getBlockEntity(cursor) instanceof ReplicationUnitBlockEntity unit
                            && unit.isFormed()) {
                        ChamberStructure.FormedBox box = unit.getLastBox();
                        if (box != null && contains(box, worldPosition)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean contains(ChamberStructure.FormedBox box, BlockPos pos) {
        return pos.getX() >= box.min().getX() && pos.getX() <= box.max().getX()
                && pos.getY() >= box.min().getY() && pos.getY() <= box.max().getY()
                && pos.getZ() >= box.min().getZ() && pos.getZ() <= box.max().getZ();
    }

    private void ensureAe2Node() {
        if (level == null || level.isClientSide() || mainNode.isReady()) {
            return;
        }
        try {
            mainNode.create(level, worldPosition);
        } catch (IllegalStateException alreadyUsed) {
            mainNode = createMainNode();
            mainNode.create(level, worldPosition);
        }
    }

    private void destroyAe2Node() {
        if (mainNode.getNode() != null) {
            mainNode.destroy();
        }
        mainNode = createMainNode();
    }

    public boolean isChamberOnline() {
        return chamberOnline;
    }

    /** AE2 network owner (security / preferences); set when the frame block is placed. */
    public void setOwningPlayer(Player player) {
        if (player != null) {
            mainNode.setOwningPlayer(player);
        }
    }

    @Nullable
    public IGridNode getMainGridNode() {
        return mainNode.getNode();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, ChamberFrameBlockEntity::onReady);
    }

    private void onReady() {
        refreshChamberLink();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (mainNode.getNode() != null) {
            mainNode.destroy();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (mainNode.getNode() != null) {
            mainNode.destroy();
        }
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return chamberOnline ? mainNode.getNode() : null;
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    /**
     * Sets AE2 online state for every frame in {@code box} without per-frame structure scans.
     * Coalesces identical box+online updates within the same game tick.
     */
    public static void setFramesInBox(Level level, ChamberStructure.FormedBox box, boolean online) {
        if (level.isClientSide()) {
            return;
        }
        long tick = level.getGameTime();
        long key = box.min().asLong() * 31L ^ box.max().asLong();
        if (tick == lastBoxTick && key == lastBoxKey && online == lastBoxOnline) {
            return;
        }
        lastBoxTick = tick;
        lastBoxKey = key;
        lastBoxOnline = online;

        BlockPos.betweenClosedStream(box.min(), box.max()).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChamberFrameBlock)) {
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof ChamberFrameBlockEntity)) {
                level.setBlockEntity(new ChamberFrameBlockEntity(pos, state));
            }
            if (level.getBlockEntity(pos) instanceof ChamberFrameBlockEntity frame) {
                frame.setChamberOnline(online);
            }
        });
    }

    /** @deprecated use {@link #setFramesInBox(Level, ChamberStructure.FormedBox, boolean)} */
    @Deprecated
    public static void refreshFramesInBox(Level level, ChamberStructure.FormedBox box) {
        setFramesInBox(level, box, true);
    }
}

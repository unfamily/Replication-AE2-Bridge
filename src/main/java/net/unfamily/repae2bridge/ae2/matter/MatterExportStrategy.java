package net.unfamily.repae2bridge.ae2.matter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.matter_fluid.IMatterHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;

import net.unfamily.repae2bridge.ae2.MatterKeyType;
import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;

/**
 * Export Bus strategy: push matter from the ME network into adjacent {@link IMatterHandler}.
 */
public final class MatterExportStrategy implements StackExportStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(MatterExportStrategy.class);

    private final BlockCapabilityCache<IMatterHandler, Direction> cache;

    public MatterExportStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        this.cache = BlockCapabilityCache.create(
                ReplicationRegistry.Capabilities.MATTER_HANDLER, level, fromPos, fromSide);
    }

    @Override
    public long transfer(StackTransferContext context, AEKey what, long amount) {
        if (!(what instanceof ReplicationMatterKey)) {
            return 0;
        }

        IMatterHandler handler = cache.getCapability();
        if (handler == null) {
            return 0;
        }
        MatterHandlerFacade adjacent = new MatterHandlerFacade(handler);

        try (MatterIoGuard.Scope ignored = MatterIoGuard.open()) {
            var inv = context.getInternalStorage();
            long extracted = StorageHelper.poweredExtraction(
                    context.getEnergySource(),
                    inv.getInventory(),
                    what,
                    amount,
                    context.getActionSource(),
                    Actionable.SIMULATE);

            long wasInserted = adjacent.insert(what, extracted, Actionable.SIMULATE);
            if (wasInserted <= 0) {
                return 0;
            }

            extracted = StorageHelper.poweredExtraction(
                    context.getEnergySource(),
                    inv.getInventory(),
                    what,
                    wasInserted,
                    context.getActionSource(),
                    Actionable.MODULATE);

            wasInserted = adjacent.insert(what, extracted, Actionable.MODULATE);
            if (wasInserted < extracted) {
                long leftover = extracted - wasInserted;
                leftover -= inv.getInventory().insert(what, leftover, Actionable.MODULATE, context.getActionSource());
                if (leftover > 0) {
                    LOG.error("Matter export: adjacent tank refused insert, voided {}x{}", leftover, what);
                }
            }
            return wasInserted;
        }
    }

    @Override
    public long push(AEKey what, long amount, Actionable mode) {
        if (!(what instanceof ReplicationMatterKey)) {
            return 0;
        }
        IMatterHandler handler = cache.getCapability();
        if (handler == null) {
            return 0;
        }
        return new MatterHandlerFacade(handler).insert(what, amount, mode);
    }
}

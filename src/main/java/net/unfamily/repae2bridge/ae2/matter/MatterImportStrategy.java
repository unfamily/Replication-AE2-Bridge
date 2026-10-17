package net.unfamily.repae2bridge.ae2.matter;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.matter_fluid.IMatterHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.core.AELog;

import net.unfamily.repae2bridge.ae2.MatterKeyType;

/**
 * Import Bus strategy: pull matter from adjacent {@link IMatterHandler} into the ME network.
 */
public final class MatterImportStrategy implements StackImportStrategy {

    private final BlockCapabilityCache<IMatterHandler, Direction> cache;

    public MatterImportStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        this.cache = BlockCapabilityCache.create(
                ReplicationRegistry.Capabilities.MATTER_HANDLER, level, fromPos, fromSide);
    }

    @Override
    public boolean transfer(StackTransferContext context) {
        if (!context.isKeyTypeEnabled(MatterKeyType.INSTANCE)) {
            return false;
        }

        IMatterHandler handler = cache.getCapability();
        if (handler == null) {
            return false;
        }

        MatterHandlerFacade adjacent = new MatterHandlerFacade(handler);
        long remaining = context.getOperationsRemaining()
                * (long) MatterKeyType.INSTANCE.getAmountPerOperation();

        var inv = context.getInternalStorage();

        for (int i = 0; i < adjacent.getSlots() && remaining > 0; i++) {
            var resource = adjacent.getStackInSlot(i);
            if (resource == null || context.isInFilter(resource.what()) == context.isInverted()) {
                continue;
            }

            long amountForThis = inv.getInventory().insert(
                    resource.what(), remaining, Actionable.SIMULATE, context.getActionSource());
            long amount = adjacent.extract(
                    resource.what(), amountForThis, Actionable.MODULATE, context.getActionSource());
            if (amount <= 0) {
                continue;
            }

            long inserted = inv.getInventory().insert(
                    resource.what(), amount, Actionable.MODULATE, context.getActionSource());
            if (inserted < amount) {
                long leftover = amount - inserted;
                leftover -= adjacent.insert(resource.what(), leftover, Actionable.MODULATE, context.getActionSource());
                if (leftover > 0) {
                    AELog.warn("Extracted %dx%s from matter tank and voided overflow", leftover, resource.what());
                }
            }

            long opsUsed = Math.max(1, inserted / MatterKeyType.INSTANCE.getAmountPerOperation());
            context.reduceOperationsRemaining(opsUsed);
            remaining -= inserted;
        }

        return false;
    }
}

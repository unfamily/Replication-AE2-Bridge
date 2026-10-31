package net.unfamily.repae2bridge.ae2.matter;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.matter_fluid.IMatterHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.storage.MEStorage;

/**
 * Storage Bus strategy: expose adjacent matter tanks as ME storage.
 */
public final class MatterExternalStorageStrategy implements ExternalStorageStrategy {

    private final BlockCapabilityCache<IMatterHandler, Direction> cache;

    public MatterExternalStorageStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        this.cache = BlockCapabilityCache.create(
                ReplicationRegistry.Capabilities.MATTER_HANDLER, level, fromPos, fromSide);
    }

    @Nullable
    @Override
    public MEStorage createWrapper(boolean extractableOnly, Runnable injectOrExtractCallback) {
        IMatterHandler handler = cache.getCapability();
        if (handler == null) {
            return null;
        }
        MatterHandlerFacade facade = new MatterHandlerFacade(handler);
        facade.setChangeListener(injectOrExtractCallback);
        facade.setExtractableOnly(extractableOnly);
        return facade;
    }
}

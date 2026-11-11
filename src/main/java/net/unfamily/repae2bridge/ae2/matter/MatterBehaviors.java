package net.unfamily.repae2bridge.ae2.matter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;

import net.unfamily.repae2bridge.ae2.MatterKeyType;

/**
 * Registers AE2 world behaviors so Import/Export/Storage buses can handle matter.
 */
public final class MatterBehaviors {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatterBehaviors.class);
    private static boolean registered;

    private MatterBehaviors() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        StackImportStrategy.register(MatterKeyType.INSTANCE, MatterImportStrategy::new);
        StackExportStrategy.register(MatterKeyType.INSTANCE, MatterExportStrategy::new);
        ExternalStorageStrategy.register(MatterKeyType.INSTANCE, MatterExternalStorageStrategy::new);
        GenericSlotCapacities.register(MatterKeyType.INSTANCE, 64_000L);
        LOGGER.info("RepAE2Bridge: Registered matter import/export/storage-bus strategies");
    }
}

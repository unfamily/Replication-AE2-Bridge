package net.unfamily.repae2bridge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for RepAE2Bridge (numbered keys + categories, iskandert style).
 */
@EventBusSubscriber(modid = RepAE2Bridge.MOD_ID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("AE2–Replication Bridge").push("bridge");
    }

    private static final ModConfigSpec.IntValue BRIDGE_ENERGY_CONSUMPTION = BUILDER
            .comment("Idle AE/t drain of the Bridge AE2 node")
            .defineInRange("000_bridgeEnergyConsumption", 500, 0, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
        BUILDER.comment("Matter storage cells").push("matter_cells");
    }

    private static final ModConfigSpec.IntValue MATTER_CELL_MAX_TYPES = BUILDER
            .comment("Maximum distinct matter types a matter storage cell can hold (AE2 clamps to 63).")
            .defineInRange("000_matterCellMaxTypes", 6, 1, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
        BUILDER.comment("Matter Assembler").push("matter_assembler");
    }

    private static final ModConfigSpec.IntValue MATTER_ASSEMBLER_MAX_PROGRESS = BUILDER
            .comment("Ticks required for a Matter Assembler to finish one craft unit (Replicator uses ~100).")
            .defineInRange("000_matterAssemblerMaxProgress", 5, 1, 1000);

    private static final ModConfigSpec.IntValue MATTER_ASSEMBLER_ENERGY_PER_TICK = BUILDER
            .comment("AE/t drained from the AE2 grid while a Matter Assembler is working.")
            .defineInRange("001_matterAssemblerEnergyPerTick", 80, 0, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
        BUILDER.comment("Logging / development").push("logging");
    }

    private static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING = BUILDER
            .comment("Enable aggressive debug logging for troubleshooting")
            .define("000_enableDebugLogging", false);

    static {
        BUILDER.pop();
        BUILDER.comment("Compatibility / safety fixes").push("fixes");
    }

    private static final ModConfigSpec.BooleanValue ENABLE_NETWORK_BLOCK_ENTITY_FIX = BUILDER
            .comment("Enable the Replication NetworkBlockEntity mixin that wraps addElement() in onLoad()",
                    "with a try-catch safety net. Requires game restart to take effect.")
            .define("000_enableNetworkBlockEntityFix", true);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int bridgeEnergyConsumption;
    public static int matterCellMaxTypes;
    public static int matterAssemblerMaxProgress;
    public static int matterAssemblerEnergyPerTick;
    public static boolean enableDebugLogging;
    public static volatile boolean enableNetworkBlockEntityFix = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        bridgeEnergyConsumption = BRIDGE_ENERGY_CONSUMPTION.get();
        matterCellMaxTypes = MATTER_CELL_MAX_TYPES.get();
        matterAssemblerMaxProgress = MATTER_ASSEMBLER_MAX_PROGRESS.get();
        matterAssemblerEnergyPerTick = MATTER_ASSEMBLER_ENERGY_PER_TICK.get();
        enableDebugLogging = ENABLE_DEBUG_LOGGING.get();
        enableNetworkBlockEntityFix = ENABLE_NETWORK_BLOCK_ENTITY_FIX.get();
    }
}

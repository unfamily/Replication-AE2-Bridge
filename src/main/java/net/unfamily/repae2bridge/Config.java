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

    private static final ModConfigSpec.BooleanValue FEDERATE_DEVICES_ACROSS_AE2 = BUILDER
            .comment("When multiple bridges share one AE2 grid, expose chip storages and replicators",
                    "across distinct Replication pipe networks (terminal + craft workers).")
            .define("001_federateDevicesAcrossAe2", true);

    static {
        BUILDER.pop();
        BUILDER.comment("Matter storage cells").push("matter_cells");
    }

    private static final ModConfigSpec.IntValue MATTER_CELL_MAX_TYPES = BUILDER
            .comment("Maximum distinct matter types a matter storage cell can hold.",
                    "0 = all registered matter types (excluding EMPTY).",
                    "1..Integer.MAX_VALUE = fixed cap (may exceed AE2's vanilla 63-type limit).")
            .defineInRange("000_matterCellMaxTypes", 6, 0, Integer.MAX_VALUE);

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
        BUILDER.comment("Matter Foundry").push("matter_foundry");
    }

    private static final ModConfigSpec.IntValue MATTER_FOUNDRY_ENERGY_PER_TICK = BUILDER
            .comment("FE/t drained from the internal FE buffer (or converted from AE via AE2 PowerUnit) while the foundry is working.")
            .defineInRange("000_matterFoundryEnergyPerTick", 40, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MATTER_FOUNDRY_FE_CAPACITY = BUILDER
            .comment("Internal FE buffer capacity of the Matter Foundry.")
            .defineInRange("001_matterFoundryFeCapacity", 100_000, 1000, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue MATTER_FOUNDRY_LOGGING = BUILDER
            .comment("Enable Matter Foundry debug logging.")
            .define("002_matterFoundryLogging", false);

    static {
        BUILDER.pop();
        BUILDER.comment("Matter Assembling Chamber").push("chamber");
    }

    private static final ModConfigSpec.IntValue CHAMBER_MIN_SIZE = BUILDER
            .comment("Minimum multiblock side length (including frame).")
            .defineInRange("000_chamberMinSize", 3, 3, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue CHAMBER_MAX_SIZE = BUILDER
            .comment("Maximum multiblock side length (including frame).",
                    "Very large values increase shell-scan cost; keep practical in survival.")
            .defineInRange("001_chamberMaxSize", 7, 3, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue CHAMBER_SPEED = BUILDER
            .comment("Chamber craft speed. Higher is faster: 100 = 1 item/tick base, 30 ≈ 3.3 ticks/item at 1 unit,",
                    "(replaces old 002_chamberMaxProgress where higher meant slower).")
            .defineInRange("002_chamberSpeed", 30, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue CHAMBER_SPEED_PER_UNIT = BUILDER
            .comment("Extra speed multiplier per additional replication unit:",
                    "points/tick *= (1 + factor * (units-1)). Default 55 ≈ 7×7×7 with headroom over a maxed AdvancedAE Quantum Computer.")
            .defineInRange("003_chamberSpeedPerUnit", 55.0, 0.0, Double.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue CHAMBER_IDLE_POWER_AE = BUILDER
            .comment("AE/t passive drain of a formed chamber (leader node) while online.")
            .defineInRange("004_chamberIdlePowerAe", 16.0, 0.0, Double.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue CHAMBER_ACTIVE_POWER_AE = BUILDER
            .comment("AE/t drained while the chamber is processing a replication job.")
            .defineInRange("005_chamberActivePowerAe", 160.0, 0.0, Double.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue CHAMBER_ANOMALY_VISUAL = BUILDER
            .comment("Enable occasional orange anomaly flash on formed chambers (cosmetic only).")
            .define("006_chamberAnomalyVisual", true);

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
    public static boolean federateDevicesAcrossAe2 = true;
    public static int matterCellMaxTypes;
    public static int matterAssemblerMaxProgress;
    public static int matterAssemblerEnergyPerTick;
    public static int matterFoundryEnergyPerTick;
    public static int matterFoundryFeCapacity;
    public static boolean matterFoundryLogging;
    public static int chamberMinSize;
    public static int chamberMaxSize;
    /** Craft progress points added per tick at 1 unit (item completes at 100). */
    public static int chamberSpeed;
    public static double chamberSpeedPerUnit;
    public static double chamberIdlePowerAe;
    public static double chamberActivePowerAe;
    public static boolean chamberAnomalyVisual;
    public static boolean enableDebugLogging;
    public static volatile boolean enableNetworkBlockEntityFix = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        bridgeEnergyConsumption = BRIDGE_ENERGY_CONSUMPTION.get();
        federateDevicesAcrossAe2 = FEDERATE_DEVICES_ACROSS_AE2.get();
        matterCellMaxTypes = MATTER_CELL_MAX_TYPES.get();
        matterAssemblerMaxProgress = MATTER_ASSEMBLER_MAX_PROGRESS.get();
        matterAssemblerEnergyPerTick = MATTER_ASSEMBLER_ENERGY_PER_TICK.get();
        matterFoundryEnergyPerTick = MATTER_FOUNDRY_ENERGY_PER_TICK.get();
        matterFoundryFeCapacity = MATTER_FOUNDRY_FE_CAPACITY.get();
        matterFoundryLogging = MATTER_FOUNDRY_LOGGING.get();
        chamberMinSize = CHAMBER_MIN_SIZE.get();
        chamberMaxSize = CHAMBER_MAX_SIZE.get();
        chamberSpeed = CHAMBER_SPEED.get();
        chamberSpeedPerUnit = CHAMBER_SPEED_PER_UNIT.get();
        chamberIdlePowerAe = CHAMBER_IDLE_POWER_AE.get();
        chamberActivePowerAe = CHAMBER_ACTIVE_POWER_AE.get();
        chamberAnomalyVisual = CHAMBER_ANOMALY_VISUAL.get();
        enableDebugLogging = ENABLE_DEBUG_LOGGING.get();
        enableNetworkBlockEntityFix = ENABLE_NETWORK_BLOCK_ENTITY_FIX.get();
    }
}

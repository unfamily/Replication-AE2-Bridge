package net.unfamily.repae2bridge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;


/**
 * Configuration class for RepAE2Bridge
 */
@EventBusSubscriber(modid = RepAE2Bridge.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue BRIDGE_ENERGY_CONSUMPTION = BUILDER
            .comment("Energy consumption rate (AE/t) for the RepAE2Bridge")
            .defineInRange("bridgeEnergyConsumption", 500, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING = BUILDER
            .comment("Enable aggressive debug logging for troubleshooting (includes network state dumps and reconnection logs)")
            .define("enableDebugLogging", false);

    private static final ModConfigSpec.ConfigValue<String> BRIDGE_CUSTOM_MATTER_CONFIG_PATH = BUILDER
            .comment("Path to directory containing replication bridge matter declaration files",
                    "Default: 'config'")
            .define("bridgeCustomMatterConfigPath", "config");

    private static final ModConfigSpec.BooleanValue ENABLE_NETWORK_MANAGER_FIX = BUILDER
            .comment("Enable the Titanium NetworkManager mixin that prevents 'Element network is null' crashes",
                    "during world loading. This fixes a race condition in Titanium's mergeNetworksIntoOne()",
                    "that affects ALL Replication network blocks (pipes, replicators, etc.).",
                    "Only disable this if another mod provides the same fix or it causes conflicts.",
                    "Requires game restart to take effect.")
            .define("enableNetworkManagerFix", true);

    private static final ModConfigSpec.BooleanValue ENABLE_NETWORK_BLOCK_ENTITY_FIX = BUILDER
            .comment("Enable the Replication NetworkBlockEntity mixin that wraps addElement() in onLoad()",
                    "with a try-catch safety net. This prevents ANY RuntimeException during network",
                    "element registration from crashing the server.",
                    "Only disable this if another mod provides the same fix or it causes conflicts.",
                    "Requires game restart to take effect.")
            .define("enableNetworkBlockEntityFix", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int bridgeEnergyConsumption;
    public static boolean enableDebugLogging;
    public static String bridgeCustomMatterConfigPath;
    public static volatile boolean enableNetworkManagerFix = true;
    public static volatile boolean enableNetworkBlockEntityFix = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        bridgeEnergyConsumption = BRIDGE_ENERGY_CONSUMPTION.get();
        enableDebugLogging = ENABLE_DEBUG_LOGGING.get();
        bridgeCustomMatterConfigPath = BRIDGE_CUSTOM_MATTER_CONFIG_PATH.get();
        enableNetworkManagerFix = ENABLE_NETWORK_MANAGER_FIX.get();
        enableNetworkBlockEntityFix = ENABLE_NETWORK_BLOCK_ENTITY_FIX.get();
    }
}

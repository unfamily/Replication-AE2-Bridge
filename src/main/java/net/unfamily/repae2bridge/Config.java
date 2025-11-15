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

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int bridgeEnergyConsumption;
    public static boolean enableDebugLogging;
    public static String bridgeCustomMatterConfigPath;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        bridgeEnergyConsumption = BRIDGE_ENERGY_CONSUMPTION.get();
        enableDebugLogging = ENABLE_DEBUG_LOGGING.get();
        bridgeCustomMatterConfigPath = BRIDGE_CUSTOM_MATTER_CONFIG_PATH.get();
    }
}

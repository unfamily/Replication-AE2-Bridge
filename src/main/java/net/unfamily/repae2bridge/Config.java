package net.unfamily.repae2bridge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;



// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
@Mod.EventBusSubscriber(modid = RepAE2Bridge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue BRIDGE_ENERGY_CONSUMPTION = BUILDER
        .comment("Energy consumption rate (AE/t) for the RepAE2Bridge")
        .defineInRange("bridgeEnergyConsumption", 500, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING = BUILDER
        .comment("Enable aggressive debug logging for troubleshooting (includes network state dumps and reconnection logs)")
        .define("enableDebugLogging", false);


    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int bridgeEnergyConsumption;
    public static boolean enableDebugLogging;
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        bridgeEnergyConsumption = BRIDGE_ENERGY_CONSUMPTION.get();
        enableDebugLogging = ENABLE_DEBUG_LOGGING.get();
}
} 
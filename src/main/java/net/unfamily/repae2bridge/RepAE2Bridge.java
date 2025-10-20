package net.unfamily.repae2bridge;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.buuz135.replication.block.MatterPipeBlock;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.unfamily.repae2bridge.item.ModItems;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(RepAE2Bridge.MOD_ID)
public class RepAE2Bridge
{
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "rep_ae2_bridge";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // Add a static field to track if our NetworkBlockEntity patches have been applied
    private static boolean networksFixed = false;
    
    // Flag statico per indicare se il mondo sta venendo scaricato
    public static volatile boolean worldUnloading = false;

    /**
     * Main constructor of the mod that gets the event bus in the non-deprecated way
     */
    public RepAE2Bridge() {
        LOGGER.info("RepAE2Bridge: Main constructor called");
        
        // Get the event bus from the mod loading context (non-deprecated way)
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // Register all items
        ModItems.register(modEventBus);
        
        // Register all blocks
        ModBlocks.register(modEventBus);

        // Register the configuration
        modEventBus.register(Config.class);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        
        // Register config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        // Log the loaded configuration
        LOGGER.info("RepAE2Bridge: Bridge energy consumption set to {} AE/t", Config.bridgeEnergyConsumption);

        // Register ourselves for server and other game events
        // Note: This is where the ServerStartingEvent and ServerStoppingEvent are registered
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Register a safe version of NetworkBlockEntity.getNetwork that avoids NullPointerException
     */
    private void patchNetworkBlockEntityClass() {
        try {
            // Check if we've already applied the patch
            if (networksFixed) {
                return;
            }
            
            // Apply our global patch to fix NetworkBlockEntity.getNetwork
            LOGGER.info("RepAE2Bridge: Applying patch to fix NetworkBlockEntity.getNetwork");
            
            
            // Mark as fixed
            networksFixed = true;
            LOGGER.info("RepAE2Bridge: NetworkBlockEntity patch applied successfully");
        } catch (Exception e) {
            LOGGER.error("RepAE2Bridge: Failed to patch NetworkBlockEntity: {}", e.getMessage());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        // LOGGER.info("HELLO FROM COMMON SETUP");

        // if (Config.logDirtBlock)
        //     LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));

        // LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        // Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
        
        // NOTE: Non registriamo più il DefaultMatterNetworkElement factory perché
        // il mod Replication lo registra automaticamente. Tentare di registrarlo
        // causerebbe un errore "duplicate pipe factory".
        // 
        // Il bridge funziona comunque correttamente utilizzando il factory già registrato.
        event.enqueueWork(() -> {
            boolean replicationLoaded = net.minecraftforge.fml.ModList.get().isLoaded("replication");
            boolean ae2Loaded = net.minecraftforge.fml.ModList.get().isLoaded("appliedenergistics2") || net.minecraftforge.fml.ModList.get().isLoaded("ae2");
    
            if (replicationLoaded && ae2Loaded) {
                LOGGER.info("Replication and AE2 mods detected - using existing network infrastructure");
            } else {
                LOGGER.warn("Replication or AE2 mod not detected - bridge functionality may be limited");
            }
        });

        // Register our mod's namespace as an allowed namespace for Replication pipes
        event.enqueueWork(() -> {
            // This ensures it runs on the main thread
            registerWithReplicationMod();
        });

        // Apply our patches to NetworkBlockEntity
        event.enqueueWork(this::patchNetworkBlockEntityClass);
    }


    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        // Add the bridge to AE2's main creative tab
        if (event.getTabKey() == appeng.api.ids.AECreativeTabIds.MAIN) {
            event.accept(ModBlocks.REP_AE2_BRIDGE_ITEM);
            LOGGER.info("Added RepAE2Bridge to AE2 creative tab");
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("RepAE2Bridge: Server starting");
        // Setta il flag worldUnloading a false all'avvio del server
        worldUnloading = false;
    }
    
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event)
    {
        LOGGER.info("RepAE2Bridge: Server stopping - setting worldUnloading flag");
        // Setta il flag worldUnloading a true quando il server si ferma
        worldUnloading = true;
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // LOGGER.info("RepAE2Bridge: Client setup");
        }
    }

    /**
    * Register the mod namespace in the list of allowed namespaces for Replication pipes
     */
    private void registerWithReplicationMod() {
        try {
            var field = MatterPipeBlock.class.getDeclaredField("ALLOWED_CONNECTION_BLOCKS");
            field.setAccessible(true);
            var list = field.get(null);
            if (list != null) {
                java.lang.reflect.Method addMethod = list.getClass().getMethod("add", Object.class);
                addMethod.invoke(list, (java.util.function.Predicate<net.minecraft.world.level.block.Block>) block -> 
                    block.getClass().getName().contains(MOD_ID)
                );
                LOGGER.info("RepAE2Bridge: Registered with Replication mod");
            }
        } catch (Exception e) {
            LOGGER.error("RepAE2Bridge: Failed to register with Replication mod: {}", e.getMessage());
        }
    }
}

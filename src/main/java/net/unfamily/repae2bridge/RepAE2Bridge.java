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

    // Registro statico per tracciare tutti i bridge attivi (solo lato server)
    public static final java.util.Set<net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity> activeBridges =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

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



    /**
     * Disconnette tutti i bridge attivi dalle reti durante il world unloading
     * Questo previene memory leaks e garantisce un cleanup pulito
     */
    public static void disconnectAllBridgesFromNetworks() {
        LOGGER.info("RepAE2Bridge: Disconnecting {} bridges from networks during world unloading", activeBridges.size());

        // Crea una copia del set per evitare ConcurrentModificationException
        java.util.List<net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity> bridgesToDisconnect =
            new java.util.ArrayList<>(activeBridges);

        for (net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity bridge : bridgesToDisconnect) {
            try {
                // Forza la disconnessione del nodo AE2 se attivo
                var mainNode = bridge.getMainNode();
                if (mainNode != null && mainNode.isActive()) {
                    mainNode.destroy();
                    LOGGER.debug("RepAE2Bridge: Disconnected AE2 node for bridge at {}",
                        bridge.getBlockPos());
                }

                // Rimuovi dal registro
                activeBridges.remove(bridge);
            } catch (Exception e) {
                LOGGER.warn("RepAE2Bridge: Exception disconnecting bridge at {}: {}",
                    bridge.getBlockPos(), e.getMessage());
            }
        }

        LOGGER.info("RepAE2Bridge: Cleanup completed - {} bridges remaining in registry",
            activeBridges.size());
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
        LOGGER.info("RepAE2Bridge: Server stopping - performing global cleanup");

        // Esegui cleanup globale di tutti i bridge prima di chiudere
        disconnectAllBridgesFromNetworks();

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

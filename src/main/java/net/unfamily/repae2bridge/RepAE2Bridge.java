package net.unfamily.repae2bridge;

import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.block.entity.ModBlockEntities;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.hrznstudio.titanium.block_network.element.NetworkElementRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.unfamily.repae2bridge.item.ModItems;
import net.unfamily.repae2bridge.item.ModCreativeModeTabs;
import net.unfamily.repae2bridge.menu.ModMenus;
import net.unfamily.repae2bridge.recipe.ModRecipes;
import net.unfamily.repae2bridge.block.entity.ChamberFrameBlockEntity;
import net.unfamily.repae2bridge.block.entity.MatterAssemblerBlockEntity;
import net.unfamily.repae2bridge.block.entity.MatterFoundryBlockEntity;
import net.unfamily.repae2bridge.block.entity.ReplicationUnitBlockEntity;
import net.unfamily.repae2bridge.ae2.cells.MatterCreativeCellHandler;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import appeng.api.AECapabilities;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageCells;
import com.buuz135.replication.block.MatterPipeBlock;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.unfamily.repae2bridge.ae2.MatterKeyType;
import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;
import net.unfamily.repae2bridge.ae2.matter.MatterBehaviors;
import net.unfamily.repae2bridge.ae2.matter.MatterContainerItemStrategy;
import net.unfamily.repae2bridge.client.MatterKeyRenderHandler;
import net.neoforged.fml.ModList;
import appeng.api.behaviors.ContainerItemStrategy;
import net.unfamily.iskalib.crafting.RecipeBundleSplitter;


// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(RepAE2Bridge.MOD_ID)
public class RepAE2Bridge
{
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "rep_ae2_bridge";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public RepAE2Bridge(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the event for capabilities
        modEventBus.addListener(this::registerCapabilities);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("RepAE2Bridge: Config registered");

        // Builtin items; custom matter items are auto-registered in CustomMatterAutoRegistrar
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);

        modEventBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey() == AEKeyType.REGISTRY_KEY) {
                AEKeyTypes.register(MatterKeyType.INSTANCE);
                LOGGER.info("RepAE2Bridge: Registered AE2 matter key type");
            }
        });
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(MatterKeyRenderHandler::register);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

        // Register the network element factory for the Replication mod
        // This is crucial for making the connection to the Replication network work
        event.enqueueWork(() -> {
            //verify if the replication mod is loaded
            boolean replicationLoaded = ModList.get().isLoaded("replication");
            boolean ae2Loaded = ModList.get().isLoaded("appliedenergistics2") || ModList.get().isLoaded("ae2");
            
            if (replicationLoaded && ae2Loaded) {
                LOGGER.info("Replication mod is loaded, skipping DefaultMatterNetworkElement registration to avoid conflicts");
            } else {
                try {
                    // Replication is not loaded, so we register the DefaultMatterNetworkElement factory
                    LOGGER.info("Replication mod not loaded, registering DefaultMatterNetworkElement factory");
                    NetworkElementRegistry.INSTANCE.addFactory(DefaultMatterNetworkElement.ID, new DefaultMatterNetworkElement.Factory());
                    LOGGER.info("Replication network integration complete");
                } catch (Exception e) {
                    // If the exception indicates a duplicate, we consider it a non-problematic case
                    if (e.getMessage() != null && e.getMessage().contains("duplicate")) {
                        LOGGER.info("DefaultMatterNetworkElement factory already registered, using existing registration");
                    } else {
                        // Other types of errors are still concerning
                        LOGGER.error("Failed to register with Replication network system", e);
                    }
                }
            }
        });

        // Register our mod's namespace as an allowed namespace for Replication pipes
        event.enqueueWork(() -> {
            // This ensures it runs on the main thread
            registerWithReplicationMod();
        });
        
        if (ModList.get().isLoaded("guideme")) {
            LOGGER.info("GuideME detected — standalone guide available (rep_ae2_bridge:guide)");
        }

        StorageCells.addCellHandler(MatterCreativeCellHandler.INSTANCE);

        event.enqueueWork(() -> {
            MatterBehaviors.register();
            ContainerItemStrategy.register(
                    MatterKeyType.INSTANCE, ReplicationMatterKey.class, new MatterContainerItemStrategy());
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.SPEED_CARD,
                    ModBlocks.MATTER_ASSEMBLER,
                    5);
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.SPEED_CARD,
                    ModBlocks.MATTER_FOUNDRY,
                    5);
            RecipeBundleSplitter.registerType("rep_ae2_bridge:matter_foundry");
        });

        LOGGER.info("RepAE2Bridge: Bridge energy consumption set to {} AE/t", Config.bridgeEnergyConsumption);
    }
    
    // Register bridge capabilities
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Register the IInWorldGridNodeHost capability for the bridge block
        event.registerBlock(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            (level, pos, state, be, context) -> {
                if (be instanceof RepAE2BridgeBlockEntity bridge) {
                    return bridge;
                }
                return null;
            },
            ModBlocks.REPAE2BRIDGE.get()
        );

        event.registerBlock(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            (level, pos, state, be, context) -> {
                if (be instanceof MatterAssemblerBlockEntity assembler) {
                    return assembler;
                }
                return null;
            },
            ModBlocks.MATTER_ASSEMBLER.get()
        );

        event.registerBlock(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            (level, pos, state, be, context) -> {
                if (be instanceof MatterFoundryBlockEntity foundry) {
                    return foundry;
                }
                return null;
            },
            ModBlocks.MATTER_FOUNDRY.get()
        );

        // Units are not cable targets (getGridNode is null); cables attach to frames only.

        // Formed chamber: cables connect to the frame shell.
        event.registerBlock(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            (level, pos, state, be, context) -> {
                if (be instanceof ChamberFrameBlockEntity frame && frame.isChamberOnline()) {
                    return frame;
                }
                return null;
            },
            ModBlocks.CHAMBER_FRAME.get()
        );

        // Bridge + Foundry item handler and energy capabilities
        RepAE2BridgeCapabilities.register(event);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        RepAE2BridgeBlockEntity.setWorldUnloading(false);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event)
    {
        LOGGER.info("RepAE2Bridge: Server stopping, notifying bridges to prepare for unload");

        // Set the static flag in the BlockEntity class to signal shutdown
        // Questo flag blocca nuove operazioni nei metodi tick delle entità
        RepAE2BridgeBlockEntity.setWorldUnloading(true);
        
        try {
            // Interruzione forzata di tutte le operazioni pendenti
            // Questo assicura una chiusura più pulita anche in caso di operazioni massive di autocrafting
            LOGGER.info("RepAE2Bridge: Cancelling all pending operations for rapid shutdown");
            RepAE2BridgeBlockEntity.cancelAllPendingOperations();
        } catch (Exception e) {
            // Non blocchiamo la chiusura del server anche in caso di errori
            LOGGER.warn("RepAE2Bridge: Exception during shutdown cleanup, continuing anyway", e);
        }

        LOGGER.info("RepAE2Bridge: All bridges notified of world unload");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // LOGGER.info("RepAE2Bridge: Client setup");
        }
    }

    /**
     * Register the mod namespace in the list of allowed namespaces for Replication cables
     */
    private void registerWithReplicationMod() {
        try {
            // Add the mod namespace to the list of allowed namespaces
            MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block ->
                block.getClass().getName().contains(MOD_ID)
            );
            LOGGER.debug("Successfully registered mod namespace with Replication");
        } catch (NoClassDefFoundError | NullPointerException e) {
            // Questo è normale durante l'inizializzazione, la registrazione avverrà tramite altre vie
            LOGGER.debug("Replication mod not fully initialized yet, skipping connection registration");
        } catch (Exception e) {
            // Log di altri errori critici
            LOGGER.error("Error registering with Replication mod: {}", e.getMessage());
        }
    }
}

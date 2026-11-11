package net.unfamily.repae2bridge;

import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.block.entity.ModBlockEntities;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.hrznstudio.titanium.block_network.element.NetworkElementRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.core.component.DataComponents;
import net.unfamily.repae2bridge.item.ModItems;
import net.unfamily.repae2bridge.item.ModCreativeModeTabs;
import net.unfamily.repae2bridge.item.MatterItem;
import net.unfamily.repae2bridge.menu.ModMenus;
import net.unfamily.repae2bridge.recipe.ModRecipes;
import net.unfamily.repae2bridge.block.entity.ChamberFrameBlockEntity;
import net.unfamily.repae2bridge.block.entity.MatterAssemblerBlockEntity;
import net.unfamily.repae2bridge.block.entity.MatterFoundryBlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.buuz135.replication.block.MatterPipeBlock;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.unfamily.repae2bridge.ae2.MatterKeyType;
import net.unfamily.repae2bridge.client.MatterKeyRenderHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.function.BiConsumer;
import net.neoforged.fml.ModList;
import net.unfamily.iskalib.crafting.RecipeBundleSplitter;


// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(RepAE2Bridge.MOD_ID)
public class RepAE2Bridge
{
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "rep_ae2_bridge";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    /** FMLCommonSetup can be observed twice in some KubeJS/dev reload paths; run once. */
    private static final AtomicBoolean COMMON_SETUP_DONE = new AtomicBoolean(false);
    private static final AtomicBoolean AE2_INTEGRATIONS_REGISTERED = new AtomicBoolean(false);

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public RepAE2Bridge(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the event for capabilities
        modEventBus.addListener(this::registerCapabilities);

        // Register ourselves for server and other game events we are interested in.
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
        net.unfamily.repae2bridge.particle.ModParticles.register(modEventBus);

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
        // Packdev runs (notably with KubeJS double resource reload) have been observed to
        // dispatch FMLCommonSetupEvent twice. AE2 StorageCells / StackImportStrategy /
        // ContainerItemStrategy reject duplicate registration — bail out early.
        if (!COMMON_SETUP_DONE.compareAndSet(false, true)) {
            LOGGER.warn("RepAE2Bridge: Skipping duplicate FMLCommonSetupEvent");
            return;
        }

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
            registerWithReplicationMod();
        });
        
        if (ModList.get().isLoaded("guideme")) {
            LOGGER.info("GuideME detected — standalone guide available (rep_ae2_bridge:guide)");
        }

        // AE2 registries reject duplicate keys/instances. In some packdev runs the deferred
        // common-setup queue can execute twice (also trips AE2's own GridLinkables) — register once.
        event.enqueueWork(RepAE2Bridge::registerAe2Integrations);

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

    /**
     * Mod event bus subscriber per modificare le proprietà degli items durante il caricamento
     */
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        /**
         * Evento che modifica il MAX_STACK_SIZE dei matter items virtuali.
         * Impostiamo uno stacksize molto alto SOLO per i matter items,
         * permettendo di storare grandi quantità.
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
            // Massimo stacksize assoluto (Integer.MAX_VALUE / 2 per evitare overflow)
            final int MATTER_MAX_STACK_SIZE = Integer.MAX_VALUE / 2;
            
            final java.util.concurrent.atomic.AtomicInteger modifiedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            
            // Filtra solo i matter items usando instanceof
            event.getAllItems()
                    .filter(item -> item instanceof MatterItem)
                    .forEach(item -> {
                        // Imposta il MAX_STACK_SIZE solo per i matter items
                        event.modify(item, builder -> 
                            builder.set(DataComponents.MAX_STACK_SIZE, MATTER_MAX_STACK_SIZE)
                        );
                        modifiedCount.incrementAndGet();
                        
                        if (Config.enableDebugLogging) {
                            LOGGER.debug("RepAE2Bridge: Set MAX_STACK_SIZE to {} for matter item: {}", 
                                        MATTER_MAX_STACK_SIZE, 
                                        BuiltInRegistries.ITEM.getKey(item));
                        }
                    });
            
            LOGGER.info("RepAE2Bridge: Modified MAX_STACK_SIZE for {} matter items to {}", 
                       modifiedCount.get(), MATTER_MAX_STACK_SIZE);
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // LOGGER.info("RepAE2Bridge: Client setup");
        }
    }

    private static void registerAe2Integrations() {
        if (!AE2_INTEGRATIONS_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        appeng.api.storage.StorageCells.addCellHandler(
                net.unfamily.repae2bridge.ae2.cells.MatterCellHandler.INSTANCE);
        appeng.api.storage.StorageCells.addCellHandler(
                net.unfamily.repae2bridge.ae2.cells.MatterCreativeCellHandler.INSTANCE);

        net.unfamily.repae2bridge.ae2.matter.MatterBehaviors.register();
        appeng.api.behaviors.ContainerItemStrategy.register(
                MatterKeyType.INSTANCE,
                net.unfamily.repae2bridge.ae2.ReplicationMatterKey.class,
                new net.unfamily.repae2bridge.ae2.matter.MatterContainerItemStrategy());
        appeng.api.upgrades.Upgrades.add(
                appeng.core.definitions.AEItems.SPEED_CARD,
                ModBlocks.MATTER_ASSEMBLER,
                5);
        appeng.api.upgrades.Upgrades.add(
                appeng.core.definitions.AEItems.SPEED_CARD,
                ModBlocks.MATTER_FOUNDRY,
                5);

        String storageCellGroup = appeng.core.localization.GuiText.StorageCells.getTranslationKey();
        var matterCells = java.util.List.of(
                ModItems.MATTER_CELL_1K,
                ModItems.MATTER_CELL_4K,
                ModItems.MATTER_CELL_16K,
                ModItems.MATTER_CELL_64K,
                ModItems.MATTER_CELL_256K,
                ModItems.MATTER_CELL_1M,
                ModItems.MATTER_CELL_4M,
                ModItems.MATTER_CELL_16M,
                ModItems.MATTER_CELL_64M,
                ModItems.MATTER_CELL_256M);
        for (var cell : matterCells) {
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.EQUAL_DISTRIBUTION_CARD, cell.get(), 1, storageCellGroup);
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.VOID_CARD, cell.get(), 1, storageCellGroup);
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.INVERTER_CARD, cell.get(), 1, storageCellGroup);
        }

        RecipeBundleSplitter.registerType("rep_ae2_bridge:matter_foundry");
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

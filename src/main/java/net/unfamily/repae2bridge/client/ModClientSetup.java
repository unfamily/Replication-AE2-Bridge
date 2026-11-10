package net.unfamily.repae2bridge.client;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;

import appeng.api.client.StorageCellModels;
import appeng.client.InitScreens;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.entity.ModBlockEntities;
import net.unfamily.repae2bridge.client.gui.BridgeScreen;
import net.unfamily.repae2bridge.client.gui.MatterAssemblerScreen;
import net.unfamily.repae2bridge.client.gui.MatterFoundryScreen;
import net.unfamily.repae2bridge.client.item.IrregularPulseProperty;
import net.unfamily.repae2bridge.client.item.MatterCrystalPulseProperty;
import net.unfamily.repae2bridge.client.particle.GreenFlameParticle;
import net.unfamily.repae2bridge.client.render.ReplicationUnitCubeModel;
import net.unfamily.repae2bridge.client.renderer.FluixingReplicaAlloyRenderer;
import net.unfamily.repae2bridge.client.renderer.MatterAssemblerRenderer;
import net.unfamily.repae2bridge.client.renderer.ReplicationUnitRenderer;
import net.unfamily.repae2bridge.item.ModItems;
import net.unfamily.repae2bridge.menu.BridgeMenu;
import net.unfamily.repae2bridge.menu.MatterAssemblerMenu;
import net.unfamily.repae2bridge.menu.MatterFoundryMenu;
import net.unfamily.repae2bridge.particle.ModParticles;

/**
 * Client setup: AE2 screen styles, BER registration.
 */
@EventBusSubscriber(modid = RepAE2Bridge.MOD_ID, value = Dist.CLIENT)
public class ModClientSetup {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(event, BridgeMenu.TYPE, BridgeScreen::new, "/screens/rep_ae2_bridge.json");
        InitScreens.register(event, MatterAssemblerMenu.TYPE, MatterAssemblerScreen::new,
                "/screens/matter_assembler.json");
        InitScreens.register(event, MatterFoundryMenu.TYPE, MatterFoundryScreen::new,
                "/screens/matter_foundry.json");
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MATTER_ASSEMBLER_BE.get(), MatterAssemblerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.REPLICATION_UNIT_BE.get(), ReplicationUnitRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FLUIXING_REPLICA_ALLOY_BLOCK_BE.get(),
                FluixingReplicaAlloyRenderer::new);
    }

    @SubscribeEvent
    public static void registerRangeSelectProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "irregular_pulse"),
                IrregularPulseProperty.MAP_CODEC);
        event.register(Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter_crystal_pulse"),
                MatterCrystalPulseProperty.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GREEN_FLAME.get(), GreenFlameParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(ReplicationUnitCubeModel.Unbaked.ID, ReplicationUnitCubeModel.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        event.register(FluixingReplicaAlloyRenderer.GREEN,
                SimpleUnbakedStandaloneModel.blockStateModel(FluixingReplicaAlloyRenderer.GREEN_ID));
        event.register(FluixingReplicaAlloyRenderer.PURPLE,
                SimpleUnbakedStandaloneModel.blockStateModel(FluixingReplicaAlloyRenderer.PURPLE_ID));
        event.register(FluixingReplicaAlloyRenderer.ORANGE,
                SimpleUnbakedStandaloneModel.blockStateModel(FluixingReplicaAlloyRenderer.ORANGE_ID));

        // Item-column UVs for base/creative matter cells in ME drives.
        registerDriveCell(event, ModItems.MATTER_CELL_1K.get(), "1k_matter_cell");
        registerDriveCell(event, ModItems.MATTER_CELL_4K.get(), "4k_matter_cell");
        registerDriveCell(event, ModItems.MATTER_CELL_16K.get(), "16k_matter_cell");
        registerDriveCell(event, ModItems.MATTER_CELL_64K.get(), "64k_matter_cell");
        registerDriveCell(event, ModItems.MATTER_CELL_256K.get(), "256k_matter_cell");
        registerDriveCell(event, ModItems.MATTER_CELL_CREATIVE.get(), "creative_matter_cell");
    }

    private static void registerDriveCell(ModelEvent.RegisterStandalone event, net.minecraft.world.item.Item item,
            String name) {
        Identifier modelId = Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "block/drive/cells/" + name);
        StorageCellModels.registerModel(item, modelId);
        var key = StorageCellModels.standaloneModel(item);
        if (key != null) {
            event.register(key, SimpleUnbakedStandaloneModel.blockStateModel(modelId));
        }
    }
}

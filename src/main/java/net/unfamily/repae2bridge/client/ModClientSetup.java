package net.unfamily.repae2bridge.client;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;

import appeng.init.client.InitScreens;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.entity.ModBlockEntities;
import net.unfamily.repae2bridge.client.gui.BridgeScreen;
import net.unfamily.repae2bridge.client.gui.MatterAssemblerScreen;
import net.unfamily.repae2bridge.client.gui.MatterFoundryScreen;
import net.unfamily.repae2bridge.client.item.IrregularPulseProperty;
import net.unfamily.repae2bridge.client.item.MatterCrystalPulseProperty;
import net.unfamily.repae2bridge.client.model.CustomMatterItemModel;
import net.unfamily.repae2bridge.client.particle.GreenFlameParticle;
import net.unfamily.repae2bridge.client.render.ReplicationUnitFormedModel;
import net.unfamily.repae2bridge.client.renderer.MatterAssemblerRenderer;
import net.unfamily.repae2bridge.client.renderer.ReplicationUnitRenderer;
import net.unfamily.repae2bridge.item.CustomMatterItem;
import net.unfamily.repae2bridge.item.MatterStorageCell;
import net.unfamily.repae2bridge.item.ModItems;
import net.unfamily.repae2bridge.menu.BridgeMenu;
import net.unfamily.repae2bridge.menu.MatterAssemblerMenu;
import net.unfamily.repae2bridge.menu.MatterFoundryMenu;
import net.unfamily.repae2bridge.particle.ModParticles;
import net.unfamily.repae2bridge.util.MatterTypeUtil;

/**
 * Client setup: AE2 screen styles, BER, custom matter models, item property pulses.
 */
@EventBusSubscriber(modid = RepAE2Bridge.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientSetup {

    private static final ResourceLocation IRREGULAR_PULSE =
            ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "irregular_pulse");
    private static final ResourceLocation MATTER_CRYSTAL_PULSE =
            ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter_crystal_pulse");
    private static final ResourceLocation CUSTOM_MATTER_LOADER =
            ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "custom_matter");
    private static final ResourceLocation REPLICATION_UNIT_FORMED_LOADER =
            ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "replication_unit_formed");

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
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GREEN_FLAME.get(), GreenFlameParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(CUSTOM_MATTER_LOADER, new CustomMatterItemModel.Loader());
        event.register(REPLICATION_UNIT_FORMED_LOADER, new ReplicationUnitFormedModel.Loader());
    }

    /**
     * Inject baked models for dynamically registered custom matter items (no JSON assets).
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        MatterTypeUtil.loadAllMatters();
        if (ModItems.CUSTOM_MATTER_ITEMS.isEmpty()) {
            return;
        }

        var context = StandaloneGeometryBakingContext.create(
                ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "custom_matter_bake"));

        for (var entry : ModItems.CUSTOM_MATTER_ITEMS.entrySet()) {
            String itemName = entry.getKey();
            Item item = entry.getValue();
            String matterKey = resolveMatterKey(item);
            CustomMatterItemModel geometry = new CustomMatterItemModel(matterKey);
            // ModelBaker is unused by CustomMatterItemModel.bake (sprite-only geometry).
            BakedModel baked = geometry.bake(
                    context,
                    null,
                    event.getTextureGetter(),
                    BlockModelRotation.X0_Y0,
                    net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY);

            ModelResourceLocation inventory = ModelResourceLocation.inventory(
                    ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, itemName));
            event.getModels().put(inventory, baked);
        }
    }

    private static String resolveMatterKey(Item item) {
        if (item instanceof CustomMatterItem custom) {
            if (custom.getMatterTypeId() != null) {
                var byId = MatterTypeUtil.getMatterInfoById(
                        ResourceLocation.tryParse(custom.getMatterTypeId()));
                if (byId != null && byId.name() != null) {
                    return byId.name();
                }
            }
            return custom.getItemId();
        }
        return "empty";
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.FLUIXING_REPLICA_ALLOY.get(), IRREGULAR_PULSE,
                    IrregularPulseProperty::get);
            ItemProperties.register(ModItems.REPLICA_PROCESSOR.get(), IRREGULAR_PULSE,
                    IrregularPulseProperty::get);
            ItemProperties.register(ModItems.MEGA_MATTER_CELL_HOUSING.get(), IRREGULAR_PULSE,
                    IrregularPulseProperty::get);
            ItemProperties.register(ModItems.MATTER_CELL_1M.get(), IRREGULAR_PULSE,
                    IrregularPulseProperty::get);
            ItemProperties.register(ModItems.MATTER_CELL_4M.get(), IRREGULAR_PULSE,
                    IrregularPulseProperty::get);
            ItemProperties.register(ModItems.MATTER_CELL_16M.get(), IRREGULAR_PULSE,
                    IrregularPulseProperty::get);
            ItemProperties.register(ModItems.MATTER_CELL_64M.get(), IRREGULAR_PULSE,
                    IrregularPulseProperty::get);
            ItemProperties.register(ModItems.MATTER_CELL_256M.get(), IRREGULAR_PULSE,
                    IrregularPulseProperty::get);
            ItemProperties.register(ModItems.MATTER_INFUSED_CRYSTAL.get(), MATTER_CRYSTAL_PULSE,
                    MatterCrystalPulseProperty::get);
        });
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // AE2 InitItemColors.makeOpaque: without alpha=255, layers render fully transparent.
        ItemColor cellLed = (stack, tintIndex) -> FastColor.ARGB32.opaque(
                MatterStorageCell.getLedColor(stack, tintIndex));
        event.register(cellLed,
                ModItems.MATTER_CELL_1K.get(),
                ModItems.MATTER_CELL_4K.get(),
                ModItems.MATTER_CELL_16K.get(),
                ModItems.MATTER_CELL_64K.get(),
                ModItems.MATTER_CELL_256K.get(),
                ModItems.MATTER_CELL_CREATIVE.get(),
                ModItems.MATTER_CELL_1M.get(),
                ModItems.MATTER_CELL_4M.get(),
                ModItems.MATTER_CELL_16M.get(),
                ModItems.MATTER_CELL_64M.get(),
                ModItems.MATTER_CELL_256M.get());
    }
}

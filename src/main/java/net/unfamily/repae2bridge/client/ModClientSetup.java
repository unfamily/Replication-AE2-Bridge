package net.unfamily.repae2bridge.client;

import com.buuz135.replication.api.IMatterType;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.client.model.CustomMatterItemModel;
import net.unfamily.repae2bridge.client.model.UniversalMatterItemModel;
import net.unfamily.repae2bridge.item.CustomMatterBindings;
import net.unfamily.repae2bridge.item.CustomMatterItem;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;


@EventBusSubscriber(modid = RepAE2Bridge.MOD_ID, value = Dist.CLIENT)
public class ModClientSetup {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "universal_matter"), new UniversalMatterItemModel.Loader());
        event.register(ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "custom_matter"), new CustomMatterItemModel.Loader());
    }

    /**
     * Bake dynamic models for auto-registered custom matter items (no per-item JSON required).
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        for (Map.Entry<Item, IMatterType> entry : CustomMatterBindings.itemToMatter().entrySet()) {
            Item item = entry.getKey();
            if (!(item instanceof CustomMatterItem)) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || BuiltInRegistries.ITEM.get(itemId) == net.minecraft.world.item.Items.AIR) {
                continue;
            }

            String matterName = entry.getValue().getName();
            try {
                CustomMatterItemModel unbaked = new CustomMatterItemModel(matterName);
                var context = StandaloneGeometryBakingContext.builder()
                        .withGui3d(false)
                        .withUseBlockLight(false)
                        .build(itemId);
                BakedModel baked = unbaked.bake(
                        context,
                        null,
                        event.getTextureGetter(),
                        BlockModelRotation.X0_Y0,
                        ItemOverrides.EMPTY
                );
                event.getModels().put(ModelResourceLocation.inventory(itemId), baked);
                if (Config.enableDebugLogging) {
                    LOGGER.debug("RepAE2Bridge: Baked dynamic model for custom matter item {}", itemId);
                }
            } catch (Exception e) {
                LOGGER.warn("RepAE2Bridge: Failed to bake model for custom matter item {}", itemId, e);
            }
        }
    }
}

package net.unfamily.repae2bridge.client;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.client.model.UniversalMatterItemModel;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;


@EventBusSubscriber(modid = RepAE2Bridge.MOD_ID, value = Dist.CLIENT)
public class ModClientSetup {

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "universal_matter"), new UniversalMatterItemModel.Loader());
    }

}
    
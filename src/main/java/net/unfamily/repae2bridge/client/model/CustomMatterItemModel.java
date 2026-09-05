package net.unfamily.repae2bridge.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.util.MatterTypeInfo;
import net.unfamily.repae2bridge.util.MatterTypeUtil;

import java.util.function.Function;

/**
 * Item model that applies the matter-type GUI texture (with atlas tint) for a fixed matter name.
 */
public record CustomMatterItemModel(String matterTypeName) implements IUnbakedGeometry<CustomMatterItemModel> {

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
        ResourceLocation textureLoc = resolveTexture(matterTypeName);
        TextureAtlasSprite sprite = spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, textureLoc));

        var itemContext = StandaloneGeometryBakingContext.builder(context)
                .withGui3d(false)
                .withUseBlockLight(false)
                .build(ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "custom_matter_" + matterTypeName));

        var renderTypes = new RenderTypeGroup(RenderType.translucent(),
                NeoForgeRenderTypes.ITEM_UNSORTED_TRANSLUCENT.get());

        var unbaked = UnbakedGeometryHelper.createUnbakedItemElements(0, sprite);
        var quads = UnbakedGeometryHelper.bakeElements(unbaked, material -> sprite, modelState);
        var model = IModelBuilder.of(itemContext.useAmbientOcclusion(), itemContext.useBlockLight(),
                itemContext.isGui3d(), context.getTransforms(),
                overrides,
                sprite, renderTypes);
        quads.forEach(model::addUnculledFace);
        return model.build();
    }

    private static ResourceLocation resolveTexture(String matterTypeName) {
        MatterTypeInfo info = MatterTypeUtil.getMatterInfo(matterTypeName);
        if (info != null) {
            return info.texture();
        }
        return ResourceLocation.fromNamespaceAndPath("replication", "gui/mattertypes/" + matterTypeName.toLowerCase());
    }

    public static class Loader implements IGeometryLoader<CustomMatterItemModel> {
        @Override
        public CustomMatterItemModel read(JsonObject json, JsonDeserializationContext context) {
            String matter = json.has("matter") ? json.get("matter").getAsString() : "empty";
            return new CustomMatterItemModel(matter);
        }
    }
}

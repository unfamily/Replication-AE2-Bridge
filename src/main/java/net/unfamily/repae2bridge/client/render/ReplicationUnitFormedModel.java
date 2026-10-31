package net.unfamily.repae2bridge.client.render;

import java.util.function.Function;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import appeng.core.AppEng;

import net.unfamily.repae2bridge.RepAE2Bridge;

/**
 * Geometry loader for formed replication units (AE2 crafting-cube style).
 */
public class ReplicationUnitFormedModel implements IUnbakedGeometry<ReplicationUnitFormedModel> {

    private static final Material RING_CORNER = ae2("ring_corner");
    private static final Material RING_HOR = ae2("ring_side_hor");
    private static final Material RING_VER = ae2("ring_side_ver");
    private static final Material INNER_BASE = ae2("light_base");
    private static final Material FIXED_OVERLAY = ours("replication_unit_base");
    private static final Material IDLE_OVERLAY = ours("replication_unit_idle");
    private static final Material RUNNING_OVERLAY = ours("replication_unit_running");
    private static final Material ANOMALY_OVERLAY = ours("replication_unit_anomaly");

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
        return new ReplicationUnitFormedBakedModel(
                spriteGetter.apply(RING_CORNER),
                spriteGetter.apply(RING_HOR),
                spriteGetter.apply(RING_VER),
                spriteGetter.apply(INNER_BASE),
                spriteGetter.apply(FIXED_OVERLAY),
                spriteGetter.apply(IDLE_OVERLAY),
                spriteGetter.apply(RUNNING_OVERLAY),
                spriteGetter.apply(ANOMALY_OVERLAY));
    }

    private static Material ae2(String name) {
        return new Material(TextureAtlas.LOCATION_BLOCKS, AppEng.makeId("block/crafting/" + name));
    }

    private static Material ours(String name) {
        return new Material(TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "block/" + name));
    }

    public static class Loader implements IGeometryLoader<ReplicationUnitFormedModel> {
        @Override
        public ReplicationUnitFormedModel read(JsonObject json, JsonDeserializationContext context) {
            return new ReplicationUnitFormedModel();
        }
    }
}

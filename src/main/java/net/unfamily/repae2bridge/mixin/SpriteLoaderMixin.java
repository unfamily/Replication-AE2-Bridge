package net.unfamily.repae2bridge.mixin;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.inventory.InventoryMenu;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.util.MatterTypeUtil;
import net.unfamily.repae2bridge.util.MatterTypeInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Mixin that dynamically adds sprites for matter items to the block atlas.
 * Dedicated PNG (as-is) or white GUI template + matter color tint.
 */
@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation EARTH_TEMPLATE =
            ResourceLocation.fromNamespaceAndPath("replication", "textures/gui/mattertypes/earth.png");

    @Shadow
    @Final
    private ResourceLocation location;

    @Shadow
    public abstract SpriteLoader.Preparations stitch(List<SpriteContents> contents, int mipLevel, Executor executor);

    @Inject(
            method = "loadAndStitch(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;ILjava/util/concurrent/Executor;Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void rep_ae2_bridge$loadMatterSprites(ResourceManager resourceManager, ResourceLocation atlasSource, int mipLevel, Executor executor, Collection<MetadataSectionSerializer<?>> serializers, CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> cir) {
        if (!this.location.equals(InventoryMenu.BLOCK_ATLAS)) {
            return;
        }

        SpriteResourceLoader spriteResourceLoader = SpriteResourceLoader.create(serializers);
        CompletableFuture<SpriteLoader.Preparations> future = CompletableFuture
                .supplyAsync(() -> {
                    List<Function<SpriteResourceLoader, SpriteContents>> factories = SpriteSourceList.load(resourceManager, atlasSource).list(resourceManager);
                    List<Function<SpriteResourceLoader, SpriteContents>> mutable = new ArrayList<>(factories);
                    rep_ae2_bridge$appendMatterFactories(mutable, resourceManager);
                    return mutable;
                }, executor)
                .thenCompose(factories -> SpriteLoader.runSpriteSuppliers(spriteResourceLoader, factories, executor))
                .thenApply(contents -> this.stitch(contents, mipLevel, executor));

        cir.setReturnValue(future);
    }

    @Unique
    private void rep_ae2_bridge$appendMatterFactories(List<Function<SpriteResourceLoader, SpriteContents>> factories, ResourceManager resourceManager) {
        MatterTypeUtil.loadAllMatters();
        Collection<MatterTypeInfo> matters = MatterTypeUtil.getAllMatterInfos();
        if (matters.isEmpty()) {
            LOGGER.warn("RepAE2Bridge: No matter types available while stitching block atlas — custom matter sprites skipped.");
            return;
        }

        int added = 0;
        for (MatterTypeInfo info : matters) {
            factories.add(loader -> rep_ae2_bridge$loadMatterSprite(loader, info, resourceManager));
            added++;
        }
        LOGGER.info("RepAE2Bridge: Queued {} matter sprite(s) for block atlas", added);
    }

    @Unique
    private SpriteContents rep_ae2_bridge$loadMatterSprite(SpriteResourceLoader loader, MatterTypeInfo info, ResourceManager resourceManager) {
        ResourceLocation spriteId = info.texture();

        // 1) Path matching unique sprite id (e.g. textures/gui/mattertypes/kubejs/plasma.png)
        ResourceLocation primaryPath = ResourceLocation.fromNamespaceAndPath(
                spriteId.getNamespace(),
                "textures/" + spriteId.getPath() + ".png"
        );
        Resource resource = resourceManager.getResource(primaryPath).orElse(null);
        boolean dedicatedPng = resource != null;
        String sourceLabel = "primary";

        // 2) Flat pack path used by Replication / KubeJS: textures/gui/mattertypes/{name}.png
        if (resource == null && info.name() != null) {
            ResourceLocation flatPath = ResourceLocation.fromNamespaceAndPath(
                    "replication",
                    "textures/gui/mattertypes/" + info.name().toLowerCase() + ".png"
            );
            resource = resourceManager.getResource(flatPath).orElse(null);
            if (resource != null) {
                dedicatedPng = true;
                sourceLabel = "flat:" + flatPath;
            }
        }

        // 3) White GUI template when no dedicated art
        if (resource == null) {
            resource = resourceManager.getResource(EARTH_TEMPLATE).orElse(null);
            if (resource == null) {
                LOGGER.warn("RepAE2Bridge: Matter texture missing for '{}' (tried {}, flat name, earth template)",
                        info.name(), primaryPath);
                return null;
            }
            dedicatedPng = false;
            sourceLabel = "template:earth";
        }

        if (Config.enableDebugLogging) {
            LOGGER.info("RepAE2Bridge: Matter sprite '{}' source={} (dedicatedPng={})", spriteId, sourceLabel, dedicatedPng);
        }

        SpriteContents contents = loader.loadSprite(spriteId, resource);
        if (contents == null) {
            LOGGER.warn("RepAE2Bridge: Failed to load matter sprite {} for '{}'", spriteId, info.name());
            return null;
        }

        // Match Replication GUI: multiply by matter color (identity when color is 1,1,1).
        rep_ae2_bridge$applyTint(contents.getOriginalImage(), info.color());
        return contents;
    }

    /**
     * Multiplies RGB by matter color. NativeImage pixels are ABGR (despite getPixelRGBA name).
     */
    @Unique
    private void rep_ae2_bridge$applyTint(NativeImage image, float[] color) {
        if (color == null || color.length < 3 || image == null) {
            return;
        }

        final float rf = clampColor(color[0]);
        final float gf = clampColor(color[1]);
        final float bf = clampColor(color[2]);

        int height = image.getHeight();
        int width = image.getWidth();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixelRGBA(x, y);
                int a = (pixel >>> 24) & 0xFF;
                int b = (pixel >>> 16) & 0xFF;
                int g = (pixel >>> 8) & 0xFF;
                int r = pixel & 0xFF;

                int nr = (int) Math.min(255, r * rf);
                int ng = (int) Math.min(255, g * gf);
                int nb = (int) Math.min(255, b * bf);

                image.setPixelRGBA(x, y, (a << 24) | (nb << 16) | (ng << 8) | nr);
            }
        }
    }

    @Unique
    private float clampColor(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}

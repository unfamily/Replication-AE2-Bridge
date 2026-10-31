package net.unfamily.repae2bridge.client.gui;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

import guideme.Guides;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import guideme.indices.ItemIndex;

import net.unfamily.repae2bridge.RepAE2Bridge;

/**
 * Opens the standalone GuideME guide for this addon (not AE2's guide).
 * AEBaseScreen only indexes AE2's guide, so addon screens need this after leaving ae2guide.
 */
final class AddonGuideHelp {

    static final ResourceLocation GUIDE_ID = ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "guide");

    private AddonGuideHelp() {
    }

    static boolean isAvailable() {
        return ModList.get().isLoaded("guideme");
    }

    @Nullable
    static PageAnchor topicForTarget(@Nullable Object target) {
        if (!isAvailable() || target == null) {
            return null;
        }
        var guide = Guides.getById(GUIDE_ID);
        if (guide == null) {
            return null;
        }
        var itemIndex = guide.getIndex(ItemIndex.class);
        if (target instanceof BlockEntity be) {
            return itemIndex.get(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()));
        }
        return null;
    }

    static void open(@Nullable Player player, @Nullable PageAnchor topic) {
        if (!isAvailable() || player == null || topic == null) {
            return;
        }
        GuidesCommon.openGuide(player, GUIDE_ID, topic);
    }
}

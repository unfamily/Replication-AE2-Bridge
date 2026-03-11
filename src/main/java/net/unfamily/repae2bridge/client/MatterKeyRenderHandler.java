package net.unfamily.repae2bridge.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import net.unfamily.repae2bridge.ae2.MatterKeyType;
import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;

import org.joml.Matrix4f;

/**
 * Client-only render handler for the Matter AE2 key type (icons and tooltips in terminals).
 */
@OnlyIn(Dist.CLIENT)
public class MatterKeyRenderHandler implements AEKeyRenderHandler<ReplicationMatterKey> {

    public static void register() {
        AEKeyRendering.register(MatterKeyType.INSTANCE, ReplicationMatterKey.class, new MatterKeyRenderHandler());
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, ReplicationMatterKey stack) {
        var displayStack = stack.wrapForDisplayOrFilter();
        if (displayStack.isEmpty()) {
            return;
        }
        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        guiGraphics.renderItem(displayStack, x, y);
        guiGraphics.renderItemDecorations(minecraft.font, displayStack, x, y, "");
        poseStack.popPose();
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, ReplicationMatterKey what, float scale,
            int combinedLight, Level level) {
        var displayStack = what.wrapForDisplayOrFilter();
        if (displayStack.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.01f);
        poseStack.mulPose(new Matrix4f().scale(scale, scale, 0.001f));
        poseStack.last().normal().rotateX(Mth.DEG_TO_RAD * -45f);
        Minecraft.getInstance().getItemRenderer().renderStatic(displayStack, ItemDisplayContext.GUI,
                combinedLight, OverlayTexture.NO_OVERLAY, poseStack, buffers, level, 0);
        poseStack.popPose();
    }

    @Override
    public Component getDisplayName(ReplicationMatterKey stack) {
        return stack.getDisplayName();
    }

    @Override
    public List<Component> getTooltip(ReplicationMatterKey stack) {
        var displayStack = stack.wrapForDisplayOrFilter();
        if (!displayStack.isEmpty()) {
            return displayStack.getTooltipLines(
                    Item.TooltipContext.of(Minecraft.getInstance().level),
                    Minecraft.getInstance().player,
                    Minecraft.getInstance().options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED
                            : TooltipFlag.Default.NORMAL);
        }
        return AEKeyRenderHandler.super.getTooltip(stack);
    }
}

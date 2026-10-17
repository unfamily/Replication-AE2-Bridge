package net.unfamily.repae2bridge.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import appeng.client.api.AEKeyRenderer;
import appeng.client.api.AEKeyRendering;
import net.unfamily.repae2bridge.ae2.MatterKeyType;
import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;

/**
 * Client render handler for the Matter AE2 key type (icons and tooltips in terminals).
 */
public class MatterKeyRenderHandler implements AEKeyRenderer<ReplicationMatterKey, ItemStackRenderState> {

    private final ItemModelResolver itemModelResolver;

    public MatterKeyRenderHandler() {
        itemModelResolver = Minecraft.getInstance().getItemModelResolver();
    }

    public static void register() {
        AEKeyRendering.register(MatterKeyType.INSTANCE, ReplicationMatterKey.class, new MatterKeyRenderHandler());
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, int x, int y,
            ReplicationMatterKey stack) {
        var poseStack = guiGraphics.pose();
        poseStack.pushMatrix();
        try {
            var displayStack = stack.wrapForDisplayOrFilter();
            if (!displayStack.isEmpty()) {
                // Do not call itemDecorations: MEStorageScreen draws the AE amount label.
                // Decorations also push the pose stack; a throw there would leak and crash
                // after ~16 matter slots (Matrix3x2fStack max depth).
                guiGraphics.item(displayStack, x, y);
            }
        } finally {
            poseStack.popMatrix();
        }
    }

    @Override
    public Class<ItemStackRenderState> stateClass() {
        return ItemStackRenderState.class;
    }

    @Override
    public ItemStackRenderState createState() {
        return new ItemStackRenderState();
    }

    @Override
    public void extract(ItemStackRenderState state, ReplicationMatterKey what, @Nullable Level level, int seed) {
        var displayStack = what.wrapForDisplayOrFilter();
        if (displayStack.isEmpty()) {
            return;
        }
        itemModelResolver.updateForTopItem(
                state,
                displayStack,
                ItemDisplayContext.GUI,
                level,
                new ItemOwner() {
                    @Override
                    public Level level() {
                        return level;
                    }

                    @Override
                    public Vec3 position() {
                        return Vec3.ZERO;
                    }

                    @Override
                    public float getVisualRotationYInDegrees() {
                        return 0;
                    }
                },
                seed);
    }

    @Override
    public void submit(PoseStack poseStack, ItemStackRenderState state, SubmitNodeCollector nodes, int lightCoords) {
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.01f);
        poseStack.mulPose(new Matrix4f().scale(1, 1, 0.001f));
        poseStack.last().normal().rotateX(Mth.DEG_TO_RAD * -45f);
        state.submit(poseStack, nodes, lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
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
        return List.of(stack.getDisplayName());
    }
}

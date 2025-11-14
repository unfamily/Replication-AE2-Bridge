package net.unfamily.repae2bridge.item;

import appeng.api.stacks.AEItemKey;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.ReplicationRegistry;
import net.unfamily.repae2bridge.component.MatterComponent;
import net.unfamily.repae2bridge.component.ModDataComponents;
import net.unfamily.repae2bridge.util.MatterTypeUtil;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class UniversalMatterItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public UniversalMatterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (entity instanceof Player && !level.isClientSide()) {
            stack.setCount(0);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        MatterComponent component = stack.get(ModDataComponents.MATTER.get());
        if (component != null) {
            String matterName = component.matterTypeName();
            String capitalizedName = matterName.substring(0, 1).toUpperCase() + matterName.substring(1);
            return Component.literal(capitalizedName + " Matter");
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        MatterComponent component = stack.get(ModDataComponents.MATTER.get());
        if (component != null) {
            var info = MatterTypeUtil.getMatterInfo(component.matterTypeName());
            if (info != null && !info.registryId().getNamespace().equals("replication")) {
                tooltipComponents.add(Component.literal("From: " + info.registryId().getNamespace())
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        // Add warning for universal matter items
        tooltipComponents.add(Component.translatable("item.rep_ae2_bridge.universal_matter.warning")
                .withStyle(ChatFormatting.RED));

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        MatterComponent component = stack.get(ModDataComponents.MATTER.get());
        if (component != null) {
            // Create unique description ID based on matter type
            return "item.rep_ae2_bridge.universal_matter." + component.matterTypeName().toLowerCase().replace(" ", "_");
        }
        return super.getDescriptionId(stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    public static ItemStack createMatterStack(IMatterType matterType, int count) {
        var info = MatterTypeUtil.getMatterInfo(matterType);

        String name = matterType.getName();
        float[] color = matterType.getColor().get();
        ResourceLocation texture;

        if (info != null) {
            texture = info.texture();
        } else {
            // For built-in matter types that don't have custom info, get the registry ID
            // and use its namespace for the texture
            var registryId = ReplicationRegistry.MATTER_TYPES_REGISTRY.getKey(matterType);
            if (registryId != null) {
                texture = ResourceLocation.fromNamespaceAndPath(
                        registryId.getNamespace(),
                        "gui/mattertypes/" + name.toLowerCase()
                );
            } else {
                // Fallback to replication namespace
                texture = ResourceLocation.fromNamespaceAndPath(
                        "replication",
                        "gui/mattertypes/" + name.toLowerCase()
                );
            }
        }

        ItemStack stack = new ItemStack(ModItems.UNIVERSAL_MATTER.get(), count);
        MatterComponent component = new MatterComponent(name, texture, color);

        // Debug logging for component creation (disabled by default like other debug logs)
        // LOGGER.info("UniversalMatterItem: Creating component for {}: {}", name, component);
        // LOGGER.info("UniversalMatterItem: Component key: {}", ModDataComponents.MATTER.get());

        stack.set(ModDataComponents.MATTER.get(), component);

        // Verify component was set
        var verifyComponent = stack.get(ModDataComponents.MATTER.get());

        // LOGGER.info("UniversalMatterItem: Retrieved component: {}", verifyComponent);
        // LOGGER.info("UniversalMatterItem: Stack components after set: {}", stack.getComponents());

        if (verifyComponent == null) {
            // LOGGER.warn("UniversalMatterItem: Failed to set MatterComponent on stack for {}", name);
        }

        return stack;
    }
}

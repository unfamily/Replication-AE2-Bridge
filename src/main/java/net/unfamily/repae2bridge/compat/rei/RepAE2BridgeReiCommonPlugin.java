package net.unfamily.repae2bridge.compat.rei;

import me.shedaniel.rei.api.common.plugins.REIServerPlugin;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessorRegistry;
import me.shedaniel.rei.forge.REIPluginCommon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

import net.unfamily.repae2bridge.RepAE2Bridge;

/**
 * Common/server REI plugin: serialize {@link AppEngSlotAccessor} so the move-items packet
 * recreates our accessor on the server (vanilla would recreate VanillaSlotAccessor instead).
 */
@REIPluginCommon
public class RepAE2BridgeReiCommonPlugin implements REIServerPlugin {

    private static final ResourceLocation APPENG_SLOT_ID =
            ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "appeng_slot");

    @Override
    public void registerSlotAccessors(SlotAccessorRegistry registry) {
        registry.register(APPENG_SLOT_ID, accessor -> accessor instanceof AppEngSlotAccessor, new SlotAccessorRegistry.Serializer() {
            @Override
            public me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor read(
                    net.minecraft.world.inventory.AbstractContainerMenu menu,
                    net.minecraft.world.entity.player.Player player,
                    CompoundTag tag) {
                int index = tag.getInt("Slot");
                Slot slot = menu.slots.get(index);
                return new AppEngSlotAccessor(slot);
            }

            @Override
            public CompoundTag save(
                    net.minecraft.world.inventory.AbstractContainerMenu menu,
                    net.minecraft.world.entity.player.Player player,
                    me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor accessor) {
                if (!(accessor instanceof AppEngSlotAccessor appeng)) {
                    throw new IllegalArgumentException("Expected AppEngSlotAccessor");
                }
                CompoundTag tag = new CompoundTag();
                tag.putInt("Slot", appeng.getSlot().index);
                return tag;
            }
        });
    }
}

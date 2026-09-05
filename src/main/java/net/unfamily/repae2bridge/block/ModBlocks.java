package net.unfamily.repae2bridge.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.repae2bridge.block.custom.*;
import net.unfamily.repae2bridge.item.ModItems;
import net.unfamily.repae2bridge.RepAE2Bridge;
import com.buuz135.replication.block.MatterPipeBlock;
import com.mojang.logging.LogUtils;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(RepAE2Bridge.MOD_ID);

    public static final DeferredBlock<Block> REPAE2BRIDGE = registerBlock("rep_ae2_bridge",
            () -> new RepAE2BridgeBl(BlockBehaviour.Properties.of()
                .strength(5.0F, 6.0F)
                .sound(SoundType.COPPER)
                .noOcclusion()));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        
        // Add our block to the list of blocks the pipe can connect to
        // This is done after registration so the block is available
        registerConnectableBlocks();
    }
    
    /**
     * Register our block in the list of blocks to which Matter Network pipes can connect
     */
    private static void registerConnectableBlocks() {
        try {
            // Add our namespace to the list of allowed namespaces
            MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block -> 
                block instanceof RepAE2BridgeBl || 
                (block.getClass().getName().contains("repae2bridge"))
            );
        } catch (NoClassDefFoundError | NullPointerException e) {
            LogUtils.getLogger().warn("Replication mod not fully loaded yet, connection registration will be handled later");
        }
    }
}

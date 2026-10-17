package net.unfamily.repae2bridge.block;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.repae2bridge.block.custom.*;
import net.unfamily.repae2bridge.item.ModItems;
import net.unfamily.repae2bridge.RepAE2Bridge;
import com.buuz135.replication.block.MatterPipeBlock;
import com.mojang.logging.LogUtils;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(RepAE2Bridge.MOD_ID);

    public static final DeferredBlock<Block> REPAE2BRIDGE = BLOCKS.registerBlock("rep_ae2_bridge",
            RepAE2BridgeBl::new,
            () -> BlockBehaviour.Properties.of()
                .strength(5.0F, 6.0F)
                .sound(SoundType.COPPER)
                .noOcclusion());

    // Match AE2 MolecularAssembler: metalProps(p).noOcclusion()
    // (AEBaseBlock.metalProps = strength 2.2/11, MapColor.METAL, SoundType.METAL, forceSolidOn)
    public static final DeferredBlock<Block> MATTER_ASSEMBLER = BLOCKS.registerBlock("matter_assembler",
            MatterAssemblerBlock::new,
            () -> BlockBehaviour.Properties.of()
                .strength(2.2F, 11.0F)
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .forceSolidOn()
                .noOcclusion());

    static {
        ModItems.ITEMS.registerSimpleBlockItem(REPAE2BRIDGE);
        ModItems.ITEMS.registerSimpleBlockItem(MATTER_ASSEMBLER);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        registerConnectableBlocks();
    }

    private static void registerConnectableBlocks() {
        try {
            MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block ->
                block instanceof RepAE2BridgeBl ||
                (block.getClass().getName().contains("repae2bridge"))
            );
        } catch (NoClassDefFoundError | NullPointerException e) {
            LogUtils.getLogger().warn("Replication mod not fully loaded yet, connection registration will be handled later");
        }
    }
}

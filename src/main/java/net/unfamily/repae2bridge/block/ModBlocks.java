package net.unfamily.repae2bridge.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.custom.RepAE2BridgeBlock;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;

/**
 * Classe per la registrazione di tutti i blocchi e block entities del mod.
 */
public class ModBlocks {
    
    // DeferredRegister per i blocchi
    public static final DeferredRegister<Block> BLOCKS = 
            DeferredRegister.create(ForgeRegistries.BLOCKS, RepAE2Bridge.MOD_ID);
    
    // DeferredRegister per i block entities
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = 
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RepAE2Bridge.MOD_ID);
    
    // DeferredRegister per gli items (BlockItems)
    public static final DeferredRegister<Item> ITEMS = 
            DeferredRegister.create(ForgeRegistries.ITEMS, RepAE2Bridge.MOD_ID);

    // Registrazione del blocco RepAE2Bridge
    public static final RegistryObject<Block> REP_AE2_BRIDGE_BLOCK = BLOCKS.register("rep_ae2_bridge",
            RepAE2BridgeBlock::new);

    // Registrazione del BlockEntity
    @SuppressWarnings({"null", "nullness"})
    public static final RegistryObject<BlockEntityType<RepAE2BridgeBlockEntity>> REP_AE2_BRIDGE_BLOCK_ENTITY = 
            BLOCK_ENTITIES.register("rep_ae2_bridge", 
                    () -> BlockEntityType.Builder.of(
                            RepAE2BridgeBlockEntity::new,
                            REP_AE2_BRIDGE_BLOCK.get()
                    ).build(null));

    // Registrazione del BlockItem (l'item che rappresenta il blocco nell'inventario)
    public static final RegistryObject<Item> REP_AE2_BRIDGE_ITEM = ITEMS.register("rep_ae2_bridge",
            () -> new BlockItem(REP_AE2_BRIDGE_BLOCK.get(), new Item.Properties()));

    /**
     * Registra tutti i blocchi nel bus degli eventi.
     */
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        ITEMS.register(eventBus);
    }
}


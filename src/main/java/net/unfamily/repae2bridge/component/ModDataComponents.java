package net.unfamily.repae2bridge.component;

import net.unfamily.repae2bridge.RepAE2Bridge;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, RepAE2Bridge.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MatterComponent>> MATTER =
            DATA_COMPONENTS.register("matter", () -> DataComponentType.<MatterComponent>builder()
                    .persistent(MatterComponent.CODEC)
                    .networkSynchronized(MatterComponent.STREAM_CODEC)
                    .build());

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}

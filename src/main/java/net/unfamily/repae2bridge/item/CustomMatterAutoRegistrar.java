package net.unfamily.repae2bridge.item;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.IMatterType;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.RepAE2Bridge;
import org.slf4j.Logger;

import java.util.Set;

/**
 * Creates dedicated bridge items for every non-builtin matter type once the matter-types
 * registry has been filled (including KubeJS startup script types).
 * <p>
 * Runs at {@link EventPriority#LOWEST} so KubeJS (LOW) and Replication builtins have already
 * registered. ITEM is still unfrozen between RegisterEvents, so direct {@link Registry#register}
 * is valid here.
 */
@EventBusSubscriber(modid = RepAE2Bridge.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CustomMatterAutoRegistrar {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> BUILTIN_MATTER_NAMES = Set.of(
            "empty", "earth", "nether", "organic", "ender", "metallic", "precious", "living", "quantum"
    );

    private CustomMatterAutoRegistrar() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ReplicationRegistry.MATTER_TYPES_KEY)) {
            return;
        }

        CustomMatterBindings.clear();
        ModItems.CUSTOM_MATTER_ITEMS.clear();

        Registry<IMatterType> matterRegistry = event.getRegistry(ReplicationRegistry.MATTER_TYPES_KEY);
        if (matterRegistry == null) {
            LOGGER.warn("RepAE2Bridge: Matter types registry missing during RegisterEvent");
            return;
        }

        int registered = 0;
        for (var entry : matterRegistry.entrySet()) {
            ResourceLocation matterId = entry.getKey().location();
            IMatterType matterType = entry.getValue();

            if (isBuiltin(matterType, matterId)) {
                continue;
            }

            String itemName = toItemRegistryName(matterId);
            if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, itemName))) {
                Item existing = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, itemName));
                if (existing instanceof CustomMatterItem custom) {
                    custom.bindToMatterType(matterId.toString());
                    CustomMatterBindings.bind(existing, matterType, itemName);
                    ModItems.CUSTOM_MATTER_ITEMS.put(itemName, existing);
                }
                continue;
            }

            CustomMatterItem item = new CustomMatterItem(new Item.Properties(), itemName);
            item.bindToMatterType(matterId.toString());
            Registry.register(
                    BuiltInRegistries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, itemName),
                    item
            );

            CustomMatterBindings.bind(item, matterType, itemName);
            ModItems.CUSTOM_MATTER_ITEMS.put(itemName, item);
            registered++;

            if (Config.enableDebugLogging) {
                LOGGER.info("RepAE2Bridge: Registered custom matter item '{}' for '{}'", itemName, matterId);
            }
        }

        LOGGER.info("RepAE2Bridge: Auto-registered {} custom matter item(s) from matter type registry", registered);
    }

    private static boolean isBuiltin(IMatterType matterType, ResourceLocation matterId) {
        if (matterId.getNamespace().equals("replication") && BUILTIN_MATTER_NAMES.contains(matterId.getPath())) {
            return true;
        }
        String name = matterType.getName();
        return name != null && BUILTIN_MATTER_NAMES.contains(name.toLowerCase());
    }

    /**
     * Stable item registry path: {@code namespace_path} with non-[a-z0-9_] replaced by underscore.
     */
    public static String toItemRegistryName(ResourceLocation matterId) {
        String raw = matterId.getNamespace() + "_" + matterId.getPath();
        return raw.toLowerCase().replaceAll("[^a-z0-9_/]", "_").replace('/', '_');
    }
}

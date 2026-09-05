package net.unfamily.repae2bridge.item;

import com.buuz135.replication.api.IMatterType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Dedicated virtual matter item for a custom (non-builtin) matter type.
 * Texture/color come from the bound {@link IMatterType}; no per-item JSON assets required.
 */
public class CustomMatterItem extends MatterItem {
    private final String itemId;
    private String matterTypeId;

    public CustomMatterItem(Properties properties, String itemId) {
        super(properties);
        this.itemId = itemId;
        this.matterTypeId = null;
    }

    public String getItemId() {
        return itemId;
    }

    public String getMatterTypeId() {
        return matterTypeId;
    }

    public void bindToMatterType(String matterTypeId) {
        this.matterTypeId = matterTypeId;
    }

    public boolean isBound() {
        return matterTypeId != null;
    }

    /**
     * ARGB color from the bound matter type, or a deterministic fallback from itemId.
     */
    public int getMatterTypeColor() {
        IMatterType type = CustomMatterBindings.itemToMatter().get(this);
        if (type != null) {
            float[] rgba = type.getColor().get();
            int r = (int) (clamp(rgba[0]) * 255);
            int g = (int) (clamp(rgba[1]) * 255);
            int b = (int) (clamp(rgba[2]) * 255);
            int a = rgba.length > 3 ? (int) (clamp(rgba[3]) * 255) : 255;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        return fallbackColor(itemId);
    }

    @Override
    public Component getName(ItemStack stack) {
        IMatterType type = CustomMatterBindings.itemToMatter().get(this);
        if (type != null) {
            String name = type.getName();
            Component translated = Component.translatable("replication.matter_type." + name);
            if (!translated.getString().equals("replication.matter_type." + name)) {
                return Component.translatable("item.rep_ae2_bridge.custom_matter", translated);
            }
            return Component.literal(formatDisplayName(name));
        }
        return Component.literal(formatDisplayName(itemId));
    }

    private static String formatDisplayName(String path) {
        String formatted = path.replace('_', ' ').replace('-', ' ');
        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase());
            }
        }
        if (result.isEmpty()) {
            return "Custom Matter";
        }
        String base = result.toString();
        if (!base.toLowerCase().endsWith("matter")) {
            base = base + " Matter";
        }
        return base;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int fallbackColor(String id) {
        int hash = 0;
        for (char c : id.toCharArray()) {
            hash = 31 * hash + c;
        }
        hash = Math.abs(hash);
        int r = 55 + (hash % 201);
        int g = 55 + ((hash >> 8) % 201);
        int b = 55 + ((hash >> 16) % 201);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}

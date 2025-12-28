package net.unfamily.repae2bridge.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Custom matter item that represents a specific matter type defined in configuration.
 * These items will disappear if left in the player's inventory.
 */
public class CustomMatterItem extends MatterItem {
    private String matterTypeId;
    private final String itemId; // The registry name of this item
    private final int cachedColor; // Pre-calculated color based on itemId for consistency

    public CustomMatterItem(Properties properties, String itemId) {
        super(properties);
        this.itemId = itemId;
        this.matterTypeId = null; // Will be set later during binding phase
        this.cachedColor = calculateColor(itemId); // Cache color based on itemId for client-server consistency
    }

    public String getItemId() {
        return itemId;
    }

    public String getMatterTypeId() {
        return matterTypeId;
    }

    /**
     * Bind this item to a specific matter type.
     * This is called during the late binding phase when matter types become available.
     */
    public void bindToMatterType(String matterTypeId) {
        this.matterTypeId = matterTypeId;
    }

    /**
     * Check if this item is bound to a matter type.
     */
    public boolean isBound() {
        return matterTypeId != null;
    }

    /**
     * Get the cached color for this item.
     * The color is pre-calculated based on itemId for client-server consistency.
     */
    public int getMatterTypeColor() {
        return cachedColor;
    }

    /**
     * Calculate a deterministic color based on the given ID.
     * Uses a simple but consistent hash function to ensure client-server compatibility.
     */
    private static int calculateColor(String id) {
        // Use a simple deterministic hash function for consistent colors
        // This ensures the same ID always produces the same color on client and server
        int hash = 0;
        for (char c : id.toCharArray()) {
            hash = 31 * hash + c;
        }

        // Use absolute value to ensure positive numbers
        hash = Math.abs(hash);

        // Generate RGB values from different parts of the hash
        int r = 55 + (hash % 201);        // Red: 55-255
        int g = 55 + ((hash >> 8) % 201); // Green: 55-255
        int b = 55 + ((hash >> 16) % 201); // Blue: 55-255

        return 0xFF000000 | (r << 16) | (g << 8) | b; // Full alpha + RGB
    }
}

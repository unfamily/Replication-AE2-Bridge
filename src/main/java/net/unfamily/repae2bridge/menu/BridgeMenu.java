package net.unfamily.repae2bridge.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;

/**
 * Bridge menu: energy-feed toggle only (no player inventory).
 */
public class BridgeMenu extends AEBaseMenu {

    private static final String ACTION_TOGGLE_ENERGY_FEED = "toggleEnergyFeed";

    public static final MenuType<BridgeMenu> TYPE = MenuTypeBuilder
            .create(BridgeMenu::new, RepAE2BridgeBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "bridge"));

    private final RepAE2BridgeBlockEntity host;

    @GuiSync(1)
    public boolean feedReplicationEnergy = true;

    public BridgeMenu(int id, Inventory playerInventory, RepAE2BridgeBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        registerClientAction(ACTION_TOGGLE_ENERGY_FEED, Boolean.class, this::doSetFeedReplicationEnergy);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            feedReplicationEnergy = host.isFeedReplicationEnergy();
        }
        super.broadcastChanges();
    }

    public RepAE2BridgeBlockEntity getHost() {
        return host;
    }

    public boolean isFeedReplicationEnergy() {
        return feedReplicationEnergy;
    }

    private void doSetFeedReplicationEnergy(Boolean enabled) {
        host.setFeedReplicationEnergy(enabled);
        feedReplicationEnergy = enabled;
    }

    public void setFeedReplicationEnergy(boolean enabled) {
        if (isClientSide()) {
            feedReplicationEnergy = enabled;
            sendClientAction(ACTION_TOGGLE_ENERGY_FEED, enabled);
        } else {
            doSetFeedReplicationEnergy(enabled);
        }
    }

    public void toggleFeedReplicationEnergy() {
        setFeedReplicationEnergy(!feedReplicationEnergy);
    }
}

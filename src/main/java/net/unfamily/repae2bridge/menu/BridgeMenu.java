package net.unfamily.repae2bridge.menu;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.ClientActionKey;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;

/**
 * Bridge menu: energy-feed toggle only (no player inventory).
 */
public class BridgeMenu extends AEBaseMenu {

    private static final ClientActionKey<Boolean> ACTION_TOGGLE_ENERGY_FEED =
            new ClientActionKey<>("toggleEnergyFeed");

    public static final MenuType<BridgeMenu> TYPE = MenuTypeBuilder
            .create(BridgeMenu::new, RepAE2BridgeBlockEntity.class)
            .buildUnregistered(Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "bridge"));

    private final RepAE2BridgeBlockEntity host;

    @GuiSync(1)
    public boolean feedReplicationEnergy = true;

    public BridgeMenu(int id, Inventory playerInventory, RepAE2BridgeBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        registerClientAction(ACTION_TOGGLE_ENERGY_FEED, ByteBufCodecs.BOOL, this::setFeedReplicationEnergy);
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

    public void setFeedReplicationEnergy(boolean enabled) {
        if (isClientSide()) {
            feedReplicationEnergy = enabled;
            sendClientAction(ACTION_TOGGLE_ENERGY_FEED, enabled);
        } else {
            host.setFeedReplicationEnergy(enabled);
            feedReplicationEnergy = enabled;
        }
    }

    public void toggleFeedReplicationEnergy() {
        setFeedReplicationEnergy(!feedReplicationEnergy);
    }
}

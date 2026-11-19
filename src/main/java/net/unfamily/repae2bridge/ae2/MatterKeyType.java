package net.unfamily.repae2bridge.ae2;

import java.util.Objects;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.unfamily.repae2bridge.RepAE2Bridge;

/**
 * AE2 key type for Replication matter.
 * This allows AE2 terminals to treat matter as its own type that can be filtered separately.
 */
public final class MatterKeyType extends AEKeyType {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter");

    public static final MatterKeyType INSTANCE = new MatterKeyType();

    private MatterKeyType() {
        super(ID, ReplicationMatterKey.class, Component.literal("Matter"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return ReplicationMatterKey.MAP_CODEC;
    }

    @Override
    public ReplicationMatterKey readFromPacket(RegistryFriendlyByteBuf input) {
        Objects.requireNonNull(input);
        return ReplicationMatterKey.fromPacket(input);
    }

    @Override
    public int getAmountPerByte() {
        // Crafting simulation: bytes += amount * crafts / getAmountPerByte() * 8
        // Fluid-scale density so high datapack matter costs do not exhaust CPU storage.
        return 1_000_000;
    }

    @Override
    public int getAmountPerOperation() {
        // Crafting CPU pattern power: sum(inputAmount / getAmountPerOperation()).
        // Must match fluid-scale density — value 1 made a 1500×8-type craft demand ~12k AE
        // in one extract and blocked pushPattern when the network buffer was smaller.
        return 1_000_000;
    }
}


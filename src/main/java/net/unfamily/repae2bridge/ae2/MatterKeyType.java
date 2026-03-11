package net.unfamily.repae2bridge.ae2;

import java.util.Objects;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.unfamily.repae2bridge.RepAE2Bridge;

/**
 * AE2 key type for Replication matter.
 * This allows AE2 terminals to treat matter as its own type that can be filtered separately.
 */
public final class MatterKeyType extends AEKeyType {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter");

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

}


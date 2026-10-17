package net.unfamily.repae2bridge.ae2;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.MatterType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.unfamily.repae2bridge.util.MatterTypeUtil;

/**
 * AE2 key implementation representing a Replication matter type.
 * Each distinct {@link IMatterType} is represented by a single key instance,
 * identified by the registry id of the matter type.
 * <p>
 * {@link #getPrimaryKey()} returns the cached key singleton from {@link #ofId}:
 * AE2's {@code KeyCounter} indexes by primary key with a reference map.
 * A mismatched primary key makes craft simulation report missing matter
 * (Start stays grey / Partial Plan) while the terminal still lists the stack.
 */
public final class ReplicationMatterKey extends AEKey {

    private static final ConcurrentHashMap<Identifier, Identifier> INTERNED_IDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Identifier, ReplicationMatterKey> CACHE = new ConcurrentHashMap<>();

    public static final MapCodec<ReplicationMatterKey> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Identifier.CODEC
                            .fieldOf("matter")
                            .forGetter(ReplicationMatterKey::getRegistryId))
                    .apply(instance, ReplicationMatterKey::ofId));

    public static final Codec<ReplicationMatterKey> CODEC = MAP_CODEC.codec();

    private final Identifier registryId;
    private final int hashCode;

    /**
     * Prefer {@link #ofId(Identifier)} / {@link #of(IMatterType)} so keys are cached.
     * Only {@link #ofId} should construct instances — the CACHE singleton is the
     * {@link #getPrimaryKey()} so AE2 {@code KeyCounter} lookups match craft simulation.
     */
    private ReplicationMatterKey(Identifier registryId) {
        this.registryId = intern(Objects.requireNonNull(registryId, "registryId"));
        this.hashCode = this.registryId.hashCode();
    }

    private static Identifier intern(Identifier id) {
        Identifier existing = INTERNED_IDS.putIfAbsent(id, id);
        return existing != null ? existing : id;
    }

    /**
     * Canonical key for a registry id (identity-stable primary key).
     */
    public static ReplicationMatterKey ofId(Identifier registryId) {
        Identifier interned = intern(Objects.requireNonNull(registryId, "registryId"));
        return CACHE.computeIfAbsent(interned, ReplicationMatterKey::new);
    }

    /**
     * Creates a key for the given matter type.
     */
    public static ReplicationMatterKey of(IMatterType matterType) {
        if (matterType == null || matterType.equals(MatterType.EMPTY)) {
            return null;
        }

        var registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
        if (registry != null) {
            var key = registry.getResourceKey(matterType).orElse(null);
            if (key != null) {
                return ofId(key.identifier());
            }
        }

        // Fallback: best-effort key based on the matter name
        return ofId(Identifier.fromNamespaceAndPath("replication", matterType.getName().toLowerCase()));
    }

    public static ReplicationMatterKey fromPacket(RegistryFriendlyByteBuf data) {
        return ofId(Identifier.STREAM_CODEC.decode(data));
    }

    public Identifier getRegistryId() {
        return registryId;
    }

    /**
     * Resolves the underlying matter type from the Replication registry.
     */
    public IMatterType resolveMatterType() {
        var registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
        if (registry == null) {
            return null;
        }
        return registry.getValue(registryId);
    }

    @Override
    public AEKeyType getType() {
        return MatterKeyType.INSTANCE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public void toTag(ValueOutput output) {
        output.store(MAP_CODEC, this);
    }

    @Override
    public Object getPrimaryKey() {
        // Cached singleton for this registry id (same object for every equal key).
        // Identifier-as-primaryKey was fragile with KeyCounter's reference map and made
        // craft simulation report missing matter while the terminal still listed it.
        return ofId(registryId);
    }

    @Override
    public Identifier getId() {
        return registryId;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        Identifier.STREAM_CODEC.encode(data, registryId);
    }

    @Override
    protected Component computeDisplayName() {
        IMatterType matterType = resolveMatterType();
        if (matterType != null) {
            return Component.literal(matterType.getName());
        }
        return Component.literal(registryId.toString());
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        // Matter is virtual; do not drop anything into the world.
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    @Override
    public ItemStack wrapForDisplayOrFilter() {
        IMatterType matterType = resolveMatterType();
        if (matterType == null) {
            return super.wrapForDisplayOrFilter();
        }
        Item item = MatterTypeUtil.getDisplayItemForMatterType(matterType);
        if (item != null) {
            return new ItemStack(item, 1);
        }
        return super.wrapForDisplayOrFilter();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReplicationMatterKey other)) {
            return false;
        }
        return registryId.equals(other.registryId);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "ReplicationMatterKey[" + registryId + "]";
    }
}

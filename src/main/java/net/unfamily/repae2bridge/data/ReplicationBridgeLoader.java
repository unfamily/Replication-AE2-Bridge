package net.unfamily.repae2bridge.data;

import java.util.Collections;
import java.util.Map;

/**
 * Legacy loader for {@code rep_ae2_bridge_matters.json}.
 * Unused: custom matter items are created by
 * {@link net.unfamily.repae2bridge.item.CustomMatterAutoRegistrar}.
 */
@Deprecated
public final class ReplicationBridgeLoader {
    private ReplicationBridgeLoader() {}

    /** @deprecated No longer used; returns an empty map. */
    @Deprecated
    public static Map<String, String> getMatterDefinitions() {
        return Collections.emptyMap();
    }

    /** @deprecated No longer used. */
    @Deprecated
    public static String getMatterId(String registryName) {
        return null;
    }
}

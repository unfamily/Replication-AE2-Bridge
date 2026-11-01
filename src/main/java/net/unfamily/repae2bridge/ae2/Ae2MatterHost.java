package net.unfamily.repae2bridge.ae2;

import com.buuz135.replication.api.IMatterType;

/**
 * Shared AE2-backed matter access for block bridge and cable bridge part.
 */
public interface Ae2MatterHost {

    long getAe2MatterAmount(IMatterType type);

    long getAe2MatterFreeSpace(IMatterType type);

    long insertAe2Matter(IMatterType type, long amount, boolean simulate);

    long extractAe2Matter(IMatterType type, long amount, boolean simulate);

    void notifyAe2MatterChanged(IMatterType type);
}

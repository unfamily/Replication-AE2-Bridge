package net.unfamily.repae2bridge.compat;

import net.neoforged.fml.ModList;

/** Optional MEGA Cells integration gate. */
public final class MegaCellsCompat {

    public static final String MOD_ID = "megacells";

    private MegaCellsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}

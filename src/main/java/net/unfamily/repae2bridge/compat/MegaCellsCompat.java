package net.unfamily.repae2bridge.compat;

import net.neoforged.fml.ModList;

/** Optional MEGA Cells integration gate (enabled on 1.21.1 where MEGA Cells exists). */
public final class MegaCellsCompat {

    public static final String MOD_ID = "megacells";

    private MegaCellsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}

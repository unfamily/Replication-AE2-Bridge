package net.unfamily.repae2bridge.compat;

import net.neoforged.fml.ModList;

/**
 * Optional MEGA Cells integration gate.
 * Hard-locked: a standalone mega housing is pointless until real mega matter cells ship with a MEGACells 26.+ port.
 */
public final class MegaCellsCompat {

    public static final String MOD_ID = "megacells";

    /** Keep false until mega matter cells are implemented against a MEGACells 26.+ build. */
    private static final boolean ENABLED = false;

    private MegaCellsCompat() {
    }

    public static boolean isLoaded() {
        return ENABLED && ModList.get().isLoaded(MOD_ID);
    }
}

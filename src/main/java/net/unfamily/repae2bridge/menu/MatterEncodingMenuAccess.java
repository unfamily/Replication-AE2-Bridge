package net.unfamily.repae2bridge.menu;

import java.util.List;

import appeng.api.stacks.GenericStack;

/**
 * Accessors for matter-disintegration encoding mode on the AE2 PET menu.
 */
public interface MatterEncodingMenuAccess {

    boolean repAe2Bridge$isMatterMode();

    void repAe2Bridge$setMatterMode(boolean enabled);

    List<GenericStack> repAe2Bridge$getMatterPreviewOutputs();

    /** Rotate primary matter output (same role as AE2 processing cycle). */
    void repAe2Bridge$cycleMatterOutputs();

    boolean repAe2Bridge$canCycleMatterOutputs();

    /** Re-apply amount display on the disintegration input (stonecutting slot). */
    void repAe2Bridge$ensureMatterInputAmountVisible();
}

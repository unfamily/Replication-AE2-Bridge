package net.unfamily.repae2bridge.ae2.encoding;

/**
 * Extra state on AE2 {@code PatternEncodingLogic} for matter-disintegration encoding.
 * <p>
 * Matter is a parallel tab that reuses the stonecutting input slot layout, but must be
 * persisted separately so reopening the PET does not fall back to stonecutting.
 */
public interface MatterEncodingLogicAccess {

    void repAe2Bridge$setAllowStackedInputs(boolean allow);

    boolean repAe2Bridge$allowStackedInputs();

    void repAe2Bridge$setMatterEncoding(boolean matterEncoding);

    boolean repAe2Bridge$isMatterEncoding();
}

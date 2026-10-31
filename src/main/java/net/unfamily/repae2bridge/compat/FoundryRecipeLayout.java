package net.unfamily.repae2bridge.compat;

/**
 * Shared JEI/EMI/REI layout for Matter Foundry recipes.
 *
 * <pre>
 * [I][I][I][I][I]                 row 1 (inputs)
 * [M][M][M][M][M]                 matter row 1
 * [M][M][M][M][M]  →  [O]        matter row 2 — arrow/output aligned here
 * [M][M][M][M][M]
 * [M][M][M][M][M]
 * Energy FE
 * Ticks
 * </pre>
 */
public final class FoundryRecipeLayout {

    public static final int MATTER_COLS = 5;
    public static final int MATTER_ROWS = 4;
    public static final int MAX_MATTER_SLOTS = MATTER_COLS * MATTER_ROWS;

    public static final int SLOT_SIZE = 18;
    public static final int SLOT_STEP = 20;

    public static final int INPUT_Y = 1;
    public static final int INPUT_X0 = 1;
    public static final int MAX_INPUTS = 5;

    public static final int MATTER_X0 = 1;
    public static final int MATTER_Y0 = 22;

    /** 0-based matter row aligned with arrow + output (2nd matter / 3rd overall row). */
    public static final int ARROW_ALIGN_MATTER_ROW = 1;

    public static final int ARROW_W = 22;
    public static final int ARROW_H = 15;
    public static final int ARROW_X = MATTER_X0 + MATTER_COLS * SLOT_STEP + 4;
    public static final int ARROW_ROW_Y = MATTER_Y0 + ARROW_ALIGN_MATTER_ROW * SLOT_STEP;
    public static final int ARROW_Y = ARROW_ROW_Y + (SLOT_SIZE - ARROW_H) / 2;

    public static final int OUTPUT_X = ARROW_X + ARROW_W + 4;
    public static final int OUTPUT_Y = ARROW_ROW_Y;

    public static final int WIDTH = OUTPUT_X + SLOT_SIZE + 2;
    public static final int HEIGHT = MATTER_Y0 + MATTER_ROWS * SLOT_STEP + 28;

    public static final int TEXT_MARGIN = 6;
    public static final int TEXT_ENERGY_Y = MATTER_Y0 + MATTER_ROWS * SLOT_STEP + 4;
    public static final int TEXT_TIME_Y = TEXT_ENERGY_Y + 10;

    /** Item drawn inset inside the 18×18 slot frame. */
    public static final int ITEM_INSET = 1;

    private FoundryRecipeLayout() {
    }

    public static int inputX(int index) {
        return INPUT_X0 + index * SLOT_STEP;
    }

    public static int matterX(int index) {
        return MATTER_X0 + (index % MATTER_COLS) * SLOT_STEP;
    }

    public static int matterY(int index) {
        return MATTER_Y0 + (index / MATTER_COLS) * SLOT_STEP;
    }

    /** @deprecated use {@link #matterX(int)} / {@link #matterY(int)} */
    @Deprecated
    public static final int MATTER_Y = MATTER_Y0;
}

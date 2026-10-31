package net.unfamily.repae2bridge.compat;

/**
 * Shared JEI/EMI/REI layout for Matter Foundry recipes.
 *
 * <pre>
 * [I][I][I][I][I]  →  [O]     y=1
 * [M][M][M]...                 y=24
 * Energy FE                    y=46
 * Ticks                        y=56
 * </pre>
 */
public final class FoundryRecipeLayout {

    public static final int WIDTH = 154;
    public static final int HEIGHT = 68;

    public static final int SLOT_SIZE = 18;
    public static final int SLOT_STEP = 20;

    public static final int INPUT_Y = 1;
    public static final int INPUT_X0 = 1;
    public static final int MAX_INPUTS = 5;

    public static final int ARROW_X = 104;
    public static final int ARROW_Y = 2;
    public static final int ARROW_W = 22;
    public static final int ARROW_H = 15;

    public static final int OUTPUT_X = 130;
    public static final int OUTPUT_Y = 1;

    public static final int MATTER_Y = 24;
    public static final int MATTER_X0 = 1;

    public static final int TEXT_MARGIN = 6;
    public static final int TEXT_ENERGY_Y = 46;
    public static final int TEXT_TIME_Y = 56;

    /** Item drawn inset inside the 18×18 slot frame. */
    public static final int ITEM_INSET = 1;

    private FoundryRecipeLayout() {
    }

    public static int inputX(int index) {
        return INPUT_X0 + index * SLOT_STEP;
    }

    public static int matterX(int index) {
        return MATTER_X0 + index * SLOT_STEP;
    }
}

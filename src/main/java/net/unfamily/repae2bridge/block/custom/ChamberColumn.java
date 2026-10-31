package net.unfamily.repae2bridge.block.custom;

import net.minecraft.util.StringRepresentable;

/**
 * Visual style for chamber frame.
 * {@link #NONE} base texture (corners / junctions);
 * {@link #INT} interior of a flat frame plane (e.g. 5×5 → borders are X/Y/Z columns, inner 3×3 is INT).
 */
public enum ChamberColumn implements StringRepresentable {
    NONE("none"),
    INT("int"),
    X("x"),
    Y("y"),
    Z("z");

    private final String name;

    ChamberColumn(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

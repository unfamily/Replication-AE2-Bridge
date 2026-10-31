package net.unfamily.repae2bridge.block.custom;

import net.minecraft.util.StringRepresentable;

/**
 * Visual column axis for chamber frame. {@link #NONE} keeps the base frame texture (corners / junctions).
 */
public enum ChamberColumn implements StringRepresentable {
    NONE("none"),
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

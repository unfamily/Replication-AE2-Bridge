package net.unfamily.repae2bridge.client.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

public class ReplicationUnitRenderState extends BlockEntityRenderState {
    public boolean drawHull;
    public int fillColor;

    /** Relative hull AABB (chamber unit volume), in block coords from this BE. */
    public float relMinX;
    public float relMinY;
    public float relMinZ;
    public float relMaxX;
    public float relMaxY;
    public float relMaxZ;

    public Identifier hullTexFrom;
    public Identifier hullTexTo;
    public float modeBlend;
}

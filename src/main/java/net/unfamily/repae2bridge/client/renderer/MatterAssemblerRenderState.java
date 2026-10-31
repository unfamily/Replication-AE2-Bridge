package net.unfamily.repae2bridge.client.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class MatterAssemblerRenderState extends BlockEntityRenderState {
    public final ItemStackRenderState item = new ItemStackRenderState();
    public float spin;
    public float progress;
    public boolean hasItem;
}

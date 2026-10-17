package net.unfamily.repae2bridge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import appeng.client.InitScreens;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.entity.ModBlockEntities;
import net.unfamily.repae2bridge.client.gui.BridgeScreen;
import net.unfamily.repae2bridge.client.gui.MatterAssemblerScreen;
import net.unfamily.repae2bridge.client.renderer.MatterAssemblerRenderer;
import net.unfamily.repae2bridge.menu.BridgeMenu;
import net.unfamily.repae2bridge.menu.MatterAssemblerMenu;

/**
 * Client setup: AE2 screen styles, BER registration.
 */
@EventBusSubscriber(modid = RepAE2Bridge.MOD_ID, value = Dist.CLIENT)
public class ModClientSetup {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(event, BridgeMenu.TYPE, BridgeScreen::new, "/screens/rep_ae2_bridge.json");
        InitScreens.register(event, MatterAssemblerMenu.TYPE, MatterAssemblerScreen::new,
                "/screens/matter_assembler.json");
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MATTER_ASSEMBLER_BE.get(), MatterAssemblerRenderer::new);
    }
}

package com.corazondemelon.client;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.registry.ModEntities;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = CorazonDeMelon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping SHOW_BAR = new KeyMapping(
            "key.corazondemelon.show_bar", GLFW.GLFW_KEY_I, "key.categories.corazondemelon");

    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.corazondemelon.open_settings", GLFW.GLFW_KEY_K, "key.categories.corazondemelon");

    private ClientSetup() {}

    /** Botón "Config" en la lista de mods. */
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new MelonConfigScreen(parent)));
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SHOW_BAR);
        event.register(OPEN_SETTINGS);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MAO.get(), MaoRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MaoModel.LAYER, MaoModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("innocence", InnocenceOverlay::render);
    }
}

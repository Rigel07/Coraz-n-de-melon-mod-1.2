package com.corazondemelon.client;

import com.corazondemelon.CorazonDeMelon;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Abre los ajustes de Mao al pulsar la tecla (por defecto K). */
@Mod.EventBusSubscriber(modid = CorazonDeMelon.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (ClientSetup.OPEN_SETTINGS.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new MelonConfigScreen(null));
            }
        }
    }
}

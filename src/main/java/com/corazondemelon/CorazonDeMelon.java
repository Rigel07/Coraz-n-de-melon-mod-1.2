package com.corazondemelon;

import com.corazondemelon.entity.MaoEntity;
import com.corazondemelon.innocence.InnocenceNetwork;
import com.corazondemelon.registry.ModEntities;
import com.corazondemelon.registry.ModItems;
import com.corazondemelon.registry.ModTabs;
import com.corazondemelon.client.ClientSetup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CorazonDeMelon.MOD_ID)
public class CorazonDeMelon {
    public static final String MOD_ID = "corazondemelon";

    public CorazonDeMelon() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModTabs.TABS.register(bus);
        bus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MelonConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientSetup::registerConfigScreen);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(InnocenceNetwork::register);
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {
        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(ModEntities.MAO.get(), MaoEntity.createAttributes().build());
        }
    }
}

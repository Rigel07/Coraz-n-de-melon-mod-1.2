package com.corazondemelon.registry;

import com.corazondemelon.CorazonDeMelon;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CorazonDeMelon.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.corazondemelon"))
                    .icon(() -> new ItemStack(ModItems.HEART_ROSA.get()))
                    .displayItems((params, out) -> {
                        out.accept(ModItems.HEART_ROSA.get());
                        out.accept(ModItems.HEART_AZUL.get());
                        out.accept(ModItems.HEART_DORADO.get());
                        out.accept(ModItems.HEART_VIOLETA.get());
                        out.accept(ModItems.MAO_SPAWN_EGG.get());
                    })
                    .build());

    private ModTabs() {}
}

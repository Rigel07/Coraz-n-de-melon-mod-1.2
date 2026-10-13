package com.corazondemelon.registry;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.item.HeartItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CorazonDeMelon.MOD_ID);

    public static final RegistryObject<Item> HEART_ROSA = ITEMS.register("corazon_magico",
            () -> new HeartItem(HeartItem.Tier.ROSA, new Item.Properties().stacksTo(16).rarity(Rarity.COMMON)));
    public static final RegistryObject<Item> HEART_AZUL = ITEMS.register("corazon_magico_azul",
            () -> new HeartItem(HeartItem.Tier.AZUL, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> HEART_DORADO = ITEMS.register("corazon_magico_dorado",
            () -> new HeartItem(HeartItem.Tier.DORADO, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> HEART_VIOLETA = ITEMS.register("corazon_magico_violeta",
            () -> new HeartItem(HeartItem.Tier.VIOLETA, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> MAO_SPAWN_EGG = ITEMS.register("mao_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.MAO, 0xFFFFFF, 0xFFD54F, new Item.Properties()));

    private ModItems() {}
}

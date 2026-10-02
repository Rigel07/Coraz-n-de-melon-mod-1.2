package com.corazondemelon.registry;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.entity.MaoEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CorazonDeMelon.MOD_ID);

    public static final RegistryObject<EntityType<MaoEntity>> MAO = ENTITIES.register("mao",
            () -> EntityType.Builder.of(MaoEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.95F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(CorazonDeMelon.MOD_ID, "mao").toString()));

    private ModEntities() {}
}

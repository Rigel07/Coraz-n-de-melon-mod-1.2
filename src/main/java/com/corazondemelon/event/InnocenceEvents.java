package com.corazondemelon.event;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.ai.MaoBrain;
import com.corazondemelon.ai.MaoPlaces;
import com.corazondemelon.entity.MaoEntity;
import com.corazondemelon.innocence.Innocence;
import com.corazondemelon.item.HeartItem;
import com.corazondemelon.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = CorazonDeMelon.MOD_ID)
public final class InnocenceEvents {

    private static final Pattern CALL_MAO = Pattern.compile("^@?mao[\\s,:;!¡]+(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private InnocenceEvents() {}

    // ---------- Persistencia / sincronización ----------

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        event.getEntity().getPersistentData().putFloat(Innocence.KEY,
                event.getOriginal().getPersistentData().getFloat(Innocence.KEY));
        CompoundTag oldData = event.getOriginal().getPersistentData();
        if (oldData.contains(MaoPlaces.KEY)) {   // los sitios guardados con Mao sobreviven a la muerte
            event.getEntity().getPersistentData().put(MaoPlaces.KEY, oldData.getCompound(MaoPlaces.KEY).copy());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) Innocence.sync(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) Innocence.sync(sp);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) Innocence.sync(sp);
    }

    // ---------- Corazones recogidos del suelo ----------

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        ItemEntity entity = event.getItem();
        ItemStack stack = entity.getItem();
        if (stack.getItem() instanceof HeartItem heart && event.getEntity() instanceof ServerPlayer sp) {
            int count = stack.getCount();
            event.setCanceled(true);          // no entra al inventario: se absorbe directamente
            sp.take(entity, count);
            entity.discard();
            Innocence.addXp(sp, heart.getTier().xp * count);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F + heart.getTier().ordinal() * 0.15F);
        }
    }

    // ---------- Buenas acciones ----------

    @SubscribeEvent
    public static void onCropHarvest(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer sp && !sp.isCreative()
                && event.getState().getBlock() instanceof CropBlock crop
                && crop.isMaxAge(event.getState())) {
            Innocence.addXp(sp, 0.12F);
        }
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) Innocence.addXp(sp, 0.4F);
    }

    @SubscribeEvent
    public static void onTame(AnimalTameEvent event) {
        if (event.getTamer() instanceof ServerPlayer sp) Innocence.addXp(sp, 1.5F);
    }

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() instanceof ServerPlayer sp) Innocence.addXp(sp, 0.3F);
    }

    // ---------- Corazones en cofres ----------

    @SubscribeEvent
    public static void onLootTable(LootTableLoadEvent event) {
        ResourceLocation id = event.getName();
        if (!id.getNamespace().equals("minecraft") || !id.getPath().startsWith("chests/")) return;
        if (id.getPath().contains("spawn_bonus")) return;
        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(0.30F))
                .add(LootItem.lootTableItem(ModItems.HEART_ROSA.get()).setWeight(62))
                .add(LootItem.lootTableItem(ModItems.HEART_AZUL.get()).setWeight(26))
                .add(LootItem.lootTableItem(ModItems.HEART_DORADO.get()).setWeight(10))
                .add(LootItem.lootTableItem(ModItems.HEART_VIOLETA.get()).setWeight(2));
        event.getTable().addPool(pool.build());
    }

    // ---------- Hablar con Mao por el chat: "Mao, ¿qué hago de noche?" ----------

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        Matcher m = CALL_MAO.matcher(event.getRawText().trim());
        if (!m.matches()) return;
        MaoEntity mao = MaoEntity.findOwnedMao(player);
        if (mao == null) return;
        String text = m.group(1).trim();
        player.server.execute(() -> MaoBrain.talk(player, mao, text));
    }
}

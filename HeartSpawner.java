package com.corazondemelon.event;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.MelonConfig;
import com.corazondemelon.entity.MaoEntity;
import com.corazondemelon.item.HeartItem;
import com.corazondemelon.registry.ModEntities;
import com.corazondemelon.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Deja corazones mágicos tirados por el suelo (y algún Mao salvaje) cerca de los jugadores en el Overworld. */
@Mod.EventBusSubscriber(modid = CorazonDeMelon.MOD_ID)
public final class HeartSpawner {
    private static int counter = 0;

    private HeartSpawner() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++counter < MelonConfig.HEART_INTERVAL_SECONDS.get() * 20) return;
        counter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.level().dimension() != Level.OVERWORLD) continue;
            ServerLevel level = player.serverLevel();
            RandomSource r = level.random;
            if (r.nextDouble() < MelonConfig.HEART_CHANCE.get()) trySpawnHeart(level, player, r);
            if (level.isDay() && r.nextDouble() < MelonConfig.MAO_SPAWN_CHANCE.get()) trySpawnMao(level, player, r);
        }
    }

    private static BlockPos surfaceAround(ServerLevel level, ServerPlayer player, RandomSource r, int minDist, int maxDist) {
        double angle = r.nextDouble() * Math.PI * 2;
        double dist = minDist + r.nextInt(maxDist - minDist + 1);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
        BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.hasChunkAt(probe)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        BlockPos below = pos.below();
        if (!level.getFluidState(below).isEmpty() || !level.getFluidState(pos).isEmpty()) return null;
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return null;
        return pos;
    }

    private static void trySpawnHeart(ServerLevel level, ServerPlayer player, RandomSource r) {
        int nearby = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(80),
                e -> e.getItem().getItem() instanceof HeartItem).size();
        if (nearby >= MelonConfig.MAX_HEARTS_NEARBY.get()) return;
        BlockPos pos = surfaceAround(level, player, r, 20, 48);
        if (pos == null) return;

        int roll = r.nextInt(100);
        Item item = roll < 70 ? ModItems.HEART_ROSA.get()
                : roll < 90 ? ModItems.HEART_AZUL.get()
                : roll < 98 ? ModItems.HEART_DORADO.get()
                : ModItems.HEART_VIOLETA.get();

        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, new ItemStack(item));
        entity.setDeltaMovement(0, 0, 0);
        entity.setUnlimitedLifetime();
        entity.setGlowingTag(MelonConfig.HEARTS_GLOW.get());
        entity.setPickUpDelay(10);
        level.addFreshEntity(entity);
    }

    /** Mantiene varios Maos salvajes repartidos alrededor de cada jugador (hasta maoMaxNearby), en grupitos de 1-2. */
    private static void trySpawnMao(ServerLevel level, ServerPlayer player, RandomSource r) {
        int wild = level.getEntitiesOfClass(MaoEntity.class, player.getBoundingBox().inflate(128), m -> !m.isTame()).size();
        int max = MelonConfig.MAO_MAX_NEARBY.get();
        if (wild >= max) return;
        BlockPos base = surfaceAround(level, player, r, 24, 64);
        if (base == null) return;
        int group = Math.min(max - wild, 1 + r.nextInt(2));
        for (int i = 0; i < group; i++) {
            MaoEntity mao = ModEntities.MAO.get().create(level);
            if (mao == null) return;
            mao.moveTo(base.getX() + 0.5 + r.nextInt(5) - 2, base.getY() + 1.5, base.getZ() + 0.5 + r.nextInt(5) - 2, r.nextFloat() * 360F, 0F);
            level.addFreshEntity(mao);
        }
    }
}

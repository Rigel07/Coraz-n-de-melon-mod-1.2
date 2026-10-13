package com.corazondemelon.event;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.entity.MaoEntity;
import com.corazondemelon.innocence.Innocence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cómo reaccionan los mobs según la Inocencia del jugador:
 *  - Nv 5+: hostiles pueden retirarse; animales pasivos regalan objetos y efectos.
 *  - Nv 7+: aura de Suerte.
 *  - Nv 8+: lobos/gatos/loros/caballos salvajes se domestican solos; los hostiles "calmados" te defienden unos segundos.
 *  - Nv 9+: los hostiles calmados a veces te dejan un regalo.
 */
@Mod.EventBusSubscriber(modid = CorazonDeMelon.MOD_ID)
public final class MobReactions {

    private static final String CALM_UNTIL = "melon_calm_until";
    private static final String CALM_OWNER = "melon_calm_owner";
    private static final String ROLL_UNTIL = "melon_roll_until";
    private static final String GIFT_UNTIL = "melon_gift_until";
    private static final String PLAYER_GIFT_NEXT = "melon_player_gift_next";

    private record Gift(Item item, int max, MobEffect effect, int ticks, int minLevel) {}

    private static final Map<EntityType<?>, Gift> GIFTS = new HashMap<>();
    private static final Map<EntityType<?>, Item> HOSTILE_GIFTS = new HashMap<>();

    static {
        GIFTS.put(EntityType.CHICKEN, new Gift(Items.EGG, 2, MobEffects.SLOW_FALLING, 400, 5));
        GIFTS.put(EntityType.COW, new Gift(Items.LEATHER, 1, MobEffects.REGENERATION, 100, 5));
        GIFTS.put(EntityType.SHEEP, new Gift(Items.WHITE_WOOL, 2, MobEffects.ABSORPTION, 600, 5));
        GIFTS.put(EntityType.PIG, new Gift(Items.CARROT, 2, MobEffects.SATURATION, 40, 5));
        GIFTS.put(EntityType.MOOSHROOM, new Gift(Items.RED_MUSHROOM, 2, MobEffects.REGENERATION, 100, 5));
        GIFTS.put(EntityType.RABBIT, new Gift(Items.RABBIT_HIDE, 1, MobEffects.JUMP, 600, 5));
        GIFTS.put(EntityType.BEE, new Gift(Items.HONEYCOMB, 1, MobEffects.MOVEMENT_SPEED, 600, 5));
        GIFTS.put(EntityType.CAT, new Gift(Items.STRING, 2, null, 0, 5));
        GIFTS.put(EntityType.FOX, new Gift(Items.SWEET_BERRIES, 3, MobEffects.NIGHT_VISION, 600, 6));
        GIFTS.put(EntityType.PANDA, new Gift(Items.BAMBOO, 3, MobEffects.DIG_SPEED, 400, 6));
        GIFTS.put(EntityType.FROG, new Gift(Items.SLIME_BALL, 1, MobEffects.JUMP, 400, 6));
        GIFTS.put(EntityType.AXOLOTL, new Gift(Items.TROPICAL_FISH, 1, MobEffects.REGENERATION, 200, 6));
        GIFTS.put(EntityType.IRON_GOLEM, new Gift(Items.POPPY, 2, MobEffects.DAMAGE_RESISTANCE, 600, 6));
        GIFTS.put(EntityType.SNIFFER, new Gift(Items.TORCHFLOWER_SEEDS, 1, null, 0, 6));
        GIFTS.put(EntityType.TURTLE, new Gift(Items.SCUTE, 1, MobEffects.WATER_BREATHING, 600, 7));
        GIFTS.put(EntityType.SQUID, new Gift(Items.INK_SAC, 1, MobEffects.WATER_BREATHING, 400, 6));
        GIFTS.put(EntityType.GLOW_SQUID, new Gift(Items.GLOW_INK_SAC, 1, MobEffects.WATER_BREATHING, 400, 6));
        GIFTS.put(EntityType.VILLAGER, new Gift(Items.EMERALD, 1, MobEffects.HERO_OF_THE_VILLAGE, 1200, 8));

        HOSTILE_GIFTS.put(EntityType.CREEPER, Items.GUNPOWDER);
        HOSTILE_GIFTS.put(EntityType.SKELETON, Items.BONE);
        HOSTILE_GIFTS.put(EntityType.SPIDER, Items.STRING);
        HOSTILE_GIFTS.put(EntityType.CAVE_SPIDER, Items.STRING);
        HOSTILE_GIFTS.put(EntityType.ZOMBIE, Items.ROTTEN_FLESH);
        HOSTILE_GIFTS.put(EntityType.SLIME, Items.SLIME_BALL);
        HOSTILE_GIFTS.put(EntityType.PHANTOM, Items.PHANTOM_MEMBRANE);
        HOSTILE_GIFTS.put(EntityType.WITCH, Items.REDSTONE);
        HOSTILE_GIFTS.put(EntityType.ENDERMAN, Items.ENDER_PEARL);
    }

    private MobReactions() {}

    // ------------------------------------------------------------------ probabilidades

    private static float calmChance(int lvl) {
        return switch (lvl) {
            case 5 -> 0.10F;
            case 6 -> 0.20F;
            case 7 -> 0.30F;
            case 8 -> 0.45F;
            case 9 -> 0.65F;
            default -> lvl >= 10 ? 0.85F : 0F;
        };
    }

    private static int calmTicks(int lvl) {
        if (lvl >= 10) return 45 * 20;
        if (lvl == 9) return 30 * 20;
        if (lvl >= 7) return 20 * 20;
        return 15 * 20;
    }

    private static float tameChance(int lvl) {
        return lvl >= 10 ? 0.50F : lvl == 9 ? 0.25F : 0.12F;
    }

    // ------------------------------------------------------------------ utilidades

    private static boolean isImmune(Mob m) {
        return m.getType().is(Tags.EntityTypes.BOSSES) || m instanceof Warden || m instanceof MaoEntity;
    }

    private static boolean isCalmed(Mob m, ServerPlayer p, long now) {
        CompoundTag d = m.getPersistentData();
        return d.getLong(CALM_UNTIL) > now && d.getString(CALM_OWNER).equals(p.getStringUUID());
    }

    private static boolean recentlyHurtBy(Mob m, ServerPlayer p) {
        return m.getLastHurtByMob() == p && m.tickCount - m.getLastHurtByMobTimestamp() < 300;
    }

    private static void spawnItemToward(ServerLevel level, Mob from, ServerPlayer to, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, from.getX(), from.getY() + 0.6, from.getZ(), stack);
        Vec3 dir = to.position().subtract(from.position());
        dir = new Vec3(dir.x, 0, dir.z).normalize().scale(0.18);
        item.setDeltaMovement(dir.x, 0.22, dir.z);
        item.setPickUpDelay(15);
        level.addFreshEntity(item);
    }

    // ------------------------------------------------------------------ hostiles

    private static boolean tryCalm(Mob m, ServerPlayer p, int lvl, long now, RandomSource r) {
        CompoundTag d = m.getPersistentData();
        if (d.getLong(ROLL_UNTIL) > now) return false;          // una sola tirada cada ~8 s por mob
        d.putLong(ROLL_UNTIL, now + 160);
        if (recentlyHurtBy(m, p)) return false;                  // si lo atacas, no se calma
        if (r.nextFloat() >= calmChance(lvl)) return false;
        calm(m, p, lvl, now, r);
        return true;
    }

    private static void calm(Mob m, ServerPlayer p, int lvl, long now, RandomSource r) {
        CompoundTag d = m.getPersistentData();
        d.putLong(CALM_UNTIL, now + calmTicks(lvl));
        d.putString(CALM_OWNER, p.getStringUUID());
        m.setTarget(null);
        m.getNavigation().stop();
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.HEART, m.getX(), m.getEyeY() + 0.3, m.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
        if (lvl >= 9 && r.nextFloat() < 0.25F) {
            Item gift = HOSTILE_GIFTS.get(m.getType());
            if (gift != null && !(gift == Items.ENDER_PEARL && lvl < 10)) {
                spawnItemToward(level, m, p, new ItemStack(gift, 1 + r.nextInt(2)));
            }
        }
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof ServerPlayer p)) return;
        if (!(event.getEntity() instanceof Mob m) || !(m instanceof Enemy) || isImmune(m)) return;
        int lvl = Innocence.getLevel(p);
        if (lvl < 5) return;
        long now = m.level().getGameTime();
        if (isCalmed(m, p, now) || tryCalm(m, p, lvl, now, m.getRandom())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ tick del jugador

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient()) return;
        if (!(event.player instanceof ServerPlayer p) || p.isSpectator() || p.tickCount % 40 != 0) return;
        int lvl = Innocence.getLevel(p);
        if (lvl >= 7) {
            p.addEffect(new MobEffectInstance(MobEffects.LUCK, 100, lvl >= 10 ? 1 : 0, true, false, true));
        }
        if (lvl < 5) return;

        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        RandomSource r = p.getRandom();
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(14), Mob::isAlive);

        for (Mob m : mobs) {
            if (isImmune(m)) continue;
            if (m instanceof Enemy) {
                if (isCalmed(m, p, now)) {
                    if (m.getTarget() == p) m.setTarget(null);
                } else if (m.getTarget() == p) {
                    tryCalm(m, p, lvl, now, r);
                }
            } else {
                if (lvl >= 8 && tryTame(m, p, lvl, r)) continue;
                tryGift(m, p, lvl, now, r);
            }
        }
        if (lvl >= 8) defend(p, mobs, now);
    }

    /** Los hostiles calmados atacan a los hostiles que todavía te persiguen. */
    private static void defend(ServerPlayer p, List<Mob> mobs, long now) {
        List<Mob> threats = mobs.stream()
                .filter(m -> m instanceof Enemy && !isImmune(m) && m.getTarget() == p && !isCalmed(m, p, now))
                .toList();
        if (threats.isEmpty()) return;
        for (Mob guard : mobs) {
            if (!(guard instanceof Enemy) || isImmune(guard) || !isCalmed(guard, p, now)) continue;
            LivingEntity current = guard.getTarget();
            if (current instanceof Mob && current.isAlive()) continue;
            Mob best = null;
            double bestDist = Double.MAX_VALUE;
            for (Mob t : threats) {
                if (t == guard) continue;
                double dist = guard.distanceToSqr(t);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = t;
                }
            }
            if (best != null) guard.setTarget(best);
        }
    }

    // ------------------------------------------------------------------ domesticación automática

    private static boolean tryTame(Mob m, ServerPlayer p, int lvl, RandomSource r) {
        ServerLevel level = p.serverLevel();
        if (m instanceof TamableAnimal t && !(t instanceof MaoEntity) && !t.isTame()) {
            if (r.nextFloat() >= tameChance(lvl)) return false;
            t.tame(p);
            t.setOrderedToSit(false);
            t.setTarget(null);
            t.getNavigation().stop();
            if (t instanceof NeutralMob n) n.stopBeingAngry();
            level.broadcastEntityEvent(t, (byte) 7);
            Item gift = t instanceof Wolf ? Items.BONE : t instanceof Cat ? Items.STRING : t instanceof Parrot ? Items.WHEAT_SEEDS : null;
            if (gift != null) spawnItemToward(level, t, p, new ItemStack(gift, 1 + r.nextInt(2)));
            return true;
        }
        if (m instanceof AbstractHorse h && !h.isTamed()) {
            if (r.nextFloat() >= tameChance(lvl)) return false;
            h.tameWithName(p);
            level.broadcastEntityEvent(h, (byte) 7);
            spawnItemToward(level, h, p, new ItemStack(Items.APPLE));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ regalos de animales

    private static void tryGift(Mob m, ServerPlayer p, int lvl, long now, RandomSource r) {
        Gift g = GIFTS.get(m.getType());
        if (g == null || lvl < g.minLevel()) return;
        CompoundTag d = m.getPersistentData();
        if (d.getLong(GIFT_UNTIL) > now) return;
        CompoundTag pd = p.getPersistentData();
        if (pd.getLong(PLAYER_GIFT_NEXT) > now) return;           // como mucho un regalo por minuto por jugador
        if (r.nextFloat() > 0.20F + 0.04F * (lvl - 5)) return;

        d.putLong(GIFT_UNTIL, now + 4800 + r.nextInt(4800) + (10 - lvl) * 1200L);
        pd.putLong(PLAYER_GIFT_NEXT, now + 1200);

        ServerLevel level = p.serverLevel();
        spawnItemToward(level, m, p, new ItemStack(g.item(), 1 + r.nextInt(g.max())));
        if (g.effect() != null) {
            p.addEffect(new MobEffectInstance(g.effect(), g.ticks() + lvl * 40, 0));
        }
        level.sendParticles(ParticleTypes.HEART, m.getX(), m.getEyeY() + 0.3, m.getZ(), 5, 0.4, 0.3, 0.4, 0.0);
        level.playSound(null, m.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1.0F, 1.2F);
    }
}

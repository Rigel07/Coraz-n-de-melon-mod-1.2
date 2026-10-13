package com.corazondemelon.event;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.innocence.Innocence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CorazonDeMelon.MOD_ID)
public final class InnocenceReviveEvent {
    private static final String REVIVE_KEY = "cdm_last_saint_revive";
    private InnocenceReviveEvent() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || Innocence.getLevel(player) < 10) return;
        long now=player.serverLevel().getGameTime();
        long last=player.getPersistentData().getLong(REVIVE_KEY);
        // Level 10 grants one miraculous recovery every in-game day, not an unlimited death loop.
        if (last != 0L && now-last < 24000L) return;
        player.getPersistentData().putLong(REVIVE_KEY,now==0L?1L:now);
        event.setCanceled(true);
        player.setHealth(Math.max(6.0F, player.getMaxHealth()*0.35F));
        player.clearFire();
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20*20, 1, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20*60, 2, true, true));
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,player.getX(),player.getY()+1.0,player.getZ(),40,0.7,1.0,0.7,0.2);
        player.serverLevel().playSound(null,player.blockPosition(),SoundEvents.TOTEM_USE,SoundSource.PLAYERS,1.0F,1.0F);
        player.sendSystemMessage(Component.literal("✦ La Inocencia te ha devuelto a la vida. Este milagro volverá a estar disponible dentro de un día de Minecraft."));
    }
}

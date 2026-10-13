package com.corazondemelon.innocence;

import com.corazondemelon.MelonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Lógica de la barra de Inocencia: 10 niveles, de 1 (Bruto) a 10 (Santo). */
public final class Innocence {
    public static final String KEY = "corazondemelon_innocence";
    public static final int MAX_LEVEL = 10;

    /** XP total necesaria para ALCANZAR cada nivel (índice 0 = nivel 1). Cada nivel cuesta más que el anterior. */
    private static final float[] THRESHOLDS = {0F, 8F, 25F, 55F, 105F, 180F, 290F, 450F, 700F, 1100F};

    private static final String[] NAMES = {
            "Bruto", "Salvaje", "Arisco", "Tranquilo", "Amable",
            "Gentil", "Bondadoso", "Puro", "Angelical", "Santo"
    };

    private static final int[] COLORS = {
            0xA0522D, 0xC0793A, 0xD9A441, 0xC8D04A, 0x8FD14F,
            0x4FD18B, 0x4FC3D1, 0x6FA8FF, 0xC59BFF, 0xFFE066
    };

    private Innocence() {}

    public static float getXp(Player player) {
        return player.getPersistentData().getFloat(KEY);
    }

    public static int levelForXp(float xp) {
        int level = 1;
        for (int i = 1; i < MAX_LEVEL; i++) {
            if (xp >= THRESHOLDS[i]) level = i + 1;
        }
        return level;
    }

    public static int getLevel(Player player) {
        return levelForXp(getXp(player));
    }

    public static String levelName(int level) {
        return NAMES[Math.max(1, Math.min(MAX_LEVEL, level)) - 1];
    }

    public static int color(int level) {
        return COLORS[Math.max(1, Math.min(MAX_LEVEL, level)) - 1];
    }

    public static float levelStart(int level) {
        return THRESHOLDS[Math.max(1, Math.min(MAX_LEVEL, level)) - 1];
    }

    /** XP necesaria para el siguiente nivel (en el nivel máximo devuelve el umbral actual). */
    public static float levelEnd(int level) {
        return level >= MAX_LEVEL ? THRESHOLDS[MAX_LEVEL - 1] : THRESHOLDS[level];
    }

    public static void addXp(ServerPlayer player, float amount) {
        if (amount <= 0F || player.isSpectator()) return;
        amount *= MelonConfig.XP_MULTIPLIER.get().floatValue();
        setXpInternal(player, getXp(player) + amount, true);
    }

    public static void setXp(ServerPlayer player, float xp) {
        setXpInternal(player, Math.max(0F, xp), false);
    }

    public static void setLevel(ServerPlayer player, int level) {
        setXp(player, levelStart(level));
    }

    private static void setXpInternal(ServerPlayer player, float xp, boolean announce) {
        float max = THRESHOLDS[MAX_LEVEL - 1] + 500F; // tope para que la barra no crezca sin fin
        xp = Math.min(xp, max);
        int before = getLevel(player);
        player.getPersistentData().putFloat(KEY, xp);
        int after = levelForXp(xp);
        sync(player);
        if (announce && after > before) {
            player.sendSystemMessage(Component.translatable("message.corazondemelon.level_up", after, levelName(after))
                    .withStyle(ChatFormatting.GOLD));
            ServerLevel level = player.serverLevel();
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.8, player.getZ(), 12, 0.5, 0.4, 0.5, 0.0);
        }
    }

    public static void sync(ServerPlayer player) {
        InnocenceNetwork.sendTo(player, getXp(player));
    }
}

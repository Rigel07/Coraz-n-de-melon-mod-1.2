package com.corazondemelon.item;

import com.corazondemelon.innocence.Innocence;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HeartItem extends Item {

    public enum Tier {
        ROSA(1.0F, ChatFormatting.LIGHT_PURPLE),
        AZUL(3.0F, ChatFormatting.AQUA),
        DORADO(8.0F, ChatFormatting.GOLD),
        VIOLETA(20.0F, ChatFormatting.DARK_PURPLE);

        public final float xp;
        public final ChatFormatting color;

        Tier(float xp, ChatFormatting color) {
            this.xp = xp;
            this.color = color;
        }
    }

    private final Tier tier;

    public HeartItem(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public Tier getTier() {
        return tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Innocence.addXp(sp, tier.xp);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier == Tier.DORADO || tier == Tier.VIOLETA;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.corazondemelon.heart_xp", String.valueOf((int) tier.xp)).withStyle(tier.color));
        tooltip.add(Component.translatable("tooltip.corazondemelon.heart_use").withStyle(ChatFormatting.GRAY));
    }
}

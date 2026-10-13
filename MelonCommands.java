package com.corazondemelon.command;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.ai.MaoBrain;
import com.corazondemelon.entity.MaoEntity;
import com.corazondemelon.innocence.Innocence;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CorazonDeMelon.MOD_ID)
public final class MelonCommands {
    private MelonCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("inocencia")
                .executes(ctx -> {
                    show(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("nivel").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("nivel", IntegerArgumentType.integer(1, Innocence.MAX_LEVEL))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    Innocence.setLevel(p, IntegerArgumentType.getInteger(ctx, "nivel"));
                                    show(p);
                                    return 1;
                                })))
                .then(Commands.literal("dar").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("xp", FloatArgumentType.floatArg(0F))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    Innocence.addXp(p, FloatArgumentType.getFloat(ctx, "xp"));
                                    show(p);
                                    return 1;
                                }))));

        event.getDispatcher().register(Commands.literal("mao")
                .then(Commands.argument("mensaje", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            MaoEntity mao = MaoEntity.findOwnedMao(p);
                            if (mao == null) {
                                ctx.getSource().sendFailure(Component.literal("No encuentro ningún Mao tuyo (puede estar en una zona descargada). Domestica uno con una zanahoria dorada (Inocencia nivel 3+)."));
                                return 0;
                            }
                            MaoBrain.talk(p, mao, StringArgumentType.getString(ctx, "mensaje"));
                            return 1;
                        })));
    }

    private static void show(ServerPlayer p) {
        int lvl = Innocence.getLevel(p);
        float xp = Innocence.getXp(p);
        String progress = lvl >= Innocence.MAX_LEVEL
                ? "nivel máximo"
                : String.format(java.util.Locale.ROOT, "%.1f / %.0f", xp, Innocence.levelEnd(lvl));
        p.sendSystemMessage(Component.literal("Inocencia: nivel " + lvl + " · " + Innocence.levelName(lvl) + " (" + progress + ")"));
    }
}

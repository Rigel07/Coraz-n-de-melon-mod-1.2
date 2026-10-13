package com.corazondemelon.command;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.entity.MaoEntity;
import com.corazondemelon.entity.MaoStorage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = CorazonDeMelon.MOD_ID)
public final class MaoManagementCommands {
    private MaoManagementCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("maos")
            .then(Commands.literal("lista").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                List<MaoEntity> maos = owned(p);
                if (maos.isEmpty()) { p.sendSystemMessage(Component.literal("Todavía no tienes ningún Mao domesticado.")); return 0; }
                p.sendSystemMessage(Component.literal("Tus Maos:"));
                for (int i=0;i<maos.size();i++) {
                    MaoEntity m=maos.get(i);
                    String n=m.isRepresentative()?"Mao (representante)":displayName(m);
                    p.sendSystemMessage(Component.literal((i+1)+". "+n+" · nivel "+(m.getOwner() instanceof ServerPlayer owner?com.corazondemelon.innocence.Innocence.getLevel(owner):1)+" · color "+m.getMaoColorName()+" · "+(m.isPatrolling()?"patrullando":"libre")+" · "+Math.round(m.distanceTo(p))+" bloques"));
                }
                return maos.size();
            }))
            .then(Commands.literal("renombrar")
                .then(Commands.argument("actual", StringArgumentType.string())
                    .then(Commands.argument("nuevo", StringArgumentType.greedyString()).executes(ctx -> {
                        ServerPlayer p=ctx.getSource().getPlayerOrException(); String old=StringArgumentType.getString(ctx,"actual"); String next=StringArgumentType.getString(ctx,"nuevo").trim();
                        MaoEntity m=find(p,old);
                        if(m==null){p.sendSystemMessage(Component.literal("No encuentro un Mao llamado «"+old+"». Usa /maos lista."));return 0;}
                        if(m.isRepresentative()){p.sendSystemMessage(Component.literal("El Mao representante conserva el nombre Mao para que siempre sea fácil identificarlo."));return 0;}
                        if(next.isBlank()||next.length()>32){p.sendSystemMessage(Component.literal("El nombre debe tener entre 1 y 32 caracteres."));return 0;}
                        m.setCustomName(Component.literal(next)); m.setCustomNameVisible(true);
                        p.sendSystemMessage(Component.literal("¡Hecho! Ahora se llama «"+next+"».")); return 1;
                    }))))
            .then(Commands.literal("color")
                .then(Commands.argument("nombre", StringArgumentType.string())
                    .then(Commands.argument("color", StringArgumentType.word()).executes(ctx -> {
                        ServerPlayer p=ctx.getSource().getPlayerOrException(); String name=StringArgumentType.getString(ctx,"nombre"); String color=StringArgumentType.getString(ctx,"color").toLowerCase(Locale.ROOT);
                        if(!List.of("rosa","verde","azul","violeta","morado","pink","green","blue","purple").contains(color)){p.sendSystemMessage(Component.literal("Colores disponibles: rosa, verde, azul o violeta."));return 0;}
                        MaoEntity m=find(p,name); if(m==null){p.sendSystemMessage(Component.literal("No encuentro a «"+name+"». Usa /maos lista."));return 0;}
                        m.setMaoColor(color); p.sendSystemMessage(Component.literal("«"+displayName(m)+"» ahora es de color "+m.getMaoColorName()+"."));return 1;
                    }))))
            .then(Commands.literal("patrulla")
                .then(Commands.argument("nombre", StringArgumentType.string())
                    .then(Commands.argument("radio", IntegerArgumentType.integer(4,64)).executes(ctx -> {
                        ServerPlayer p=ctx.getSource().getPlayerOrException(); String name=StringArgumentType.getString(ctx,"nombre"); int radius=IntegerArgumentType.getInteger(ctx,"radio"); MaoEntity m=find(p,name);
                        if(m==null){p.sendSystemMessage(Component.literal("No encuentro a «"+name+"». Usa /maos lista."));return 0;}
                        if(m.isRepresentative()){p.sendSystemMessage(Component.literal("El Mao representante siempre te acompaña y no puede quedarse patrullando."));return 0;}
                        m.startPatrol(p.blockPosition(),radius); p.sendSystemMessage(Component.literal(displayName(m)+" patrullará la zona en un radio de "+radius+" bloques y defenderá a sus amigos.")); return 1;
                    }))))
            .then(Commands.literal("seguir")
                .then(Commands.argument("nombre", StringArgumentType.string()).executes(ctx -> {
                    ServerPlayer p=ctx.getSource().getPlayerOrException(); String name=StringArgumentType.getString(ctx,"nombre"); MaoEntity m=find(p,name);
                    if(m==null){p.sendSystemMessage(Component.literal("No encuentro a «"+name+"». Usa /maos lista."));return 0;}
                    m.stopPatrol();m.setOrderedToSit(false);m.setInSittingPose(false);
                    if(m.level()!=p.level()||m.distanceToSqr(p)>400)m.warpTo(p.serverLevel(),p.getX()+1,p.getY()+1,p.getZ());
                    p.sendSystemMessage(Component.literal(displayName(m)+" vuelve a seguirte."));return 1;
                })))
            .then(Commands.literal("detener")
                .then(Commands.argument("nombre", StringArgumentType.string()).executes(ctx -> {
                    ServerPlayer p=ctx.getSource().getPlayerOrException(); MaoEntity m=find(p,StringArgumentType.getString(ctx,"nombre"));
                    if(m==null){p.sendSystemMessage(Component.literal("No encuentro ese Mao. Usa /maos lista."));return 0;}
                    if(m.isRepresentative()){p.sendSystemMessage(Component.literal("El representante siempre te acompaña."));return 0;}
                    m.stopPatrol();m.setOrderedToSit(true);m.setInSittingPose(true);p.sendSystemMessage(Component.literal(displayName(m)+" se queda aquí."));return 1;
                })))
            .then(Commands.literal("minar")
                .then(Commands.argument("nombre", StringArgumentType.string())
                    .then(Commands.argument("material", StringArgumentType.word()).executes(ctx -> {
                        ServerPlayer p=ctx.getSource().getPlayerOrException(); MaoEntity m=find(p,StringArgumentType.getString(ctx,"nombre")); String material=StringArgumentType.getString(ctx,"material").toLowerCase(Locale.ROOT);
                        if(m==null){p.sendSystemMessage(Component.literal("No encuentro ese Mao. Usa /maos lista."));return 0;}
                        if(!List.of("diamante","diamantes","hierro","oro","carbon","cobre","redstone","lapislazuli","lapis","esmeralda","esmeraldas","cuarzo","netherita","escombros","coal","iron","gold","diamond").contains(material)) {p.sendSystemMessage(Component.literal("Dime un mineral concreto: diamante, hierro, oro, carbón, cobre, redstone, lapislázuli, esmeralda o escombros."));return 0;}
                        m.setMiningTarget(material);m.stopPatrol();m.setOrderedToSit(false);m.setInSittingPose(false);
                        p.sendSystemMessage(Component.literal(displayName(m)+" buscará lentamente "+material+" en las zonas cargadas y guardará lo que encuentre en su inventario infinito. Usa /maos dejar-minar "+displayName(m).replace(" ","_")+" para detenerlo."));return 1;
                    }))))
            .then(Commands.literal("dejar-minar")
                .then(Commands.argument("nombre", StringArgumentType.string()).executes(ctx -> {
                    ServerPlayer p=ctx.getSource().getPlayerOrException(); MaoEntity m=find(p,StringArgumentType.getString(ctx,"nombre"));
                    if(m==null){p.sendSystemMessage(Component.literal("No encuentro ese Mao. Usa /maos lista."));return 0;}
                    m.setMiningTarget("");m.getPersistentData().putInt("cdm_mining_progress",0);p.sendSystemMessage(Component.literal(displayName(m)+" ha dejado de buscar minerales."));return 1;
                })))
            .then(Commands.literal("craftear")
                .then(Commands.argument("nombre", StringArgumentType.string())
                    .then(Commands.argument("objeto", StringArgumentType.word()).executes(ctx -> {
                        ServerPlayer p=ctx.getSource().getPlayerOrException(); MaoEntity m=find(p,StringArgumentType.getString(ctx,"nombre")); String wanted=StringArgumentType.getString(ctx,"objeto");
                        if(m==null){p.sendSystemMessage(Component.literal("No encuentro ese Mao. Usa /maos lista."));return 0;}
                        net.minecraft.world.item.ItemStack crafted=MaoStorage.craft(p.serverLevel(),m,wanted);
                        if(crafted.isEmpty())p.sendSystemMessage(Component.literal("No encuentro una receta para «"+wanted+"» que pueda fabricar con los materiales que tengo guardados."));
                        else p.sendSystemMessage(Component.literal("¡He fabricado "+crafted.getCount()+"x "+crafted.getHoverName().getString()+" y lo he guardado en mi inventario infinito!"));
                        return crafted.isEmpty()?0:1;
                    }))))
            .then(Commands.literal("inventario")
                .then(Commands.argument("nombre", StringArgumentType.string()).executes(ctx -> {
                    ServerPlayer p=ctx.getSource().getPlayerOrException(); MaoEntity m=find(p,StringArgumentType.getString(ctx,"nombre"));
                    if(m==null){p.sendSystemMessage(Component.literal("No encuentro ese Mao. Usa /maos lista."));return 0;}
                    p.sendSystemMessage(Component.literal(displayName(m)+": "+MaoStorage.summary(m)));return 1;
                })))
            .then(Commands.literal("revisar-cofre")
                .executes(ctx -> {
                    ServerPlayer p=ctx.getSource().getPlayerOrException();
                    if(!MaoStorage.inspectLookedAtContainer(p))p.sendSystemMessage(Component.literal("Mira directamente a un cofre o contenedor a menos de 6 bloques."));
                    return 1;
                }))
            .then(Commands.literal("recoger-cofre")
                .then(Commands.argument("nombre", StringArgumentType.string()).executes(ctx -> {
                    ServerPlayer p=ctx.getSource().getPlayerOrException(); MaoEntity m=find(p,StringArgumentType.getString(ctx,"nombre"));
                    if(m==null){p.sendSystemMessage(Component.literal("No encuentro ese Mao. Usa /maos lista."));return 0;}
                    if(!MaoStorage.collectLookedAtContainer(p,m))p.sendSystemMessage(Component.literal("Mira directamente a un cofre o contenedor a menos de 6 bloques."));return 1;
                })))
            .then(Commands.literal("dejar-cofre")
                .then(Commands.argument("nombre", StringArgumentType.string()).executes(ctx -> {
                    ServerPlayer p=ctx.getSource().getPlayerOrException(); MaoEntity m=find(p,StringArgumentType.getString(ctx,"nombre"));
                    if(m==null){p.sendSystemMessage(Component.literal("No encuentro ese Mao. Usa /maos lista."));return 0;}
                    if(!MaoStorage.depositToLookedAtContainer(p,m))p.sendSystemMessage(Component.literal("Mira directamente a un cofre o contenedor a menos de 6 bloques."));return 1;
                })))
        );
    }

    /** Small offline natural-language bridge for the most common companion-management requests. */
    public static boolean handleChat(ServerPlayer player, String raw) {
        String text=Normalizer.normalize(raw.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim();
        List<MaoEntity> maos=owned(player); if(maos.isEmpty()) return false;
        MaoEntity selected=maos.get(0);
        Matcher named=Pattern.compile("\\bmao\\s*(\\d+)\\b").matcher(text);
        boolean namedSelection=named.find();
        if(namedSelection) {
            String alias="Mao "+named.group(1);
            MaoEntity exact=maos.stream().filter(m->m.getCustomName()!=null&&m.getCustomName().getString().equalsIgnoreCase(alias)).findFirst().orElse(null);
            if(exact!=null) selected=exact;
            else { int idx; try { idx=Integer.parseInt(named.group(1)); } catch(NumberFormatException ex){idx=1;} if(idx>=1&&idx<=maos.size())selected=maos.get(idx-1); }
        }

        Matcher color=Pattern.compile("(?:color|pinta|pon).*?(rosa|verde|azul|violeta).*?").matcher(text);
        if(color.find() && (text.contains("color")||text.contains("pinta")||text.startsWith("pon "))) { selected.setMaoColor(color.group(1)); player.sendSystemMessage(Component.literal(displayName(selected)+" ahora es de color "+selected.getMaoColorName()+".")); return true; }
        Matcher rename=Pattern.compile("(?:renombra|cambia el nombre de|llama a)\\s+(mao\\s*\\d+|mao)\\s+(?:a|como|por)\\s+(.+)$").matcher(text);
        if(rename.find()) { if(selected.isRepresentative()){player.sendSystemMessage(Component.literal("El Mao representante conserva su nombre para distinguirlo."));return true;} String next=rename.group(2).trim(); if(next.length()>0&&next.length()<=32){selected.setCustomName(Component.literal(next));selected.setCustomNameVisible(true);player.sendSystemMessage(Component.literal("¡Ahora me llamo "+next+"!"));} return true; }
        if(text.contains("patrull") || text.contains("vigila esta zona") || text.contains("vigila la zona")) {
            if(selected.isRepresentative() && !namedSelection) selected=maos.stream().filter(m->!m.isRepresentative()).findFirst().orElse(selected);
            if(selected.isRepresentative()){player.sendSystemMessage(Component.literal("El representante siempre te acompaña; elige otro Mao con «Mao 2 patrulla» o usa /maos patrulla."));return true;}
            Matcher radius=Pattern.compile("(?:radio|radios)\\s+(\\d+)").matcher(text); int r=16; if(radius.find())try{r=Integer.parseInt(radius.group(1));}catch(NumberFormatException ignored){}
            selected.startPatrol(player.blockPosition(),Math.max(4,Math.min(64,r)));player.sendSystemMessage(Component.literal(displayName(selected)+" empieza a patrullar esta zona."));return true;
        }
        Matcher mining=Pattern.compile("(?:busca|pica|mina|minar)\\s+(diamantes?|hierro|oro|carbon|cobre|redstone|lapislazuli|lapis|esmeraldas?|cuarzo|netherita|escombros)").matcher(text);
        if(mining.find()){selected.setMiningTarget(mining.group(1));selected.stopPatrol();selected.setOrderedToSit(false);player.sendSystemMessage(Component.literal(displayName(selected)+" buscará lentamente "+mining.group(1)+" en las zonas cargadas."));return true;}
        if(text.contains("inventario")||text.contains("que tienes guardado")){player.sendSystemMessage(Component.literal(displayName(selected)+": "+MaoStorage.summary(selected)));return true;}
        if(text.contains("que hay en este cofre")||text.contains("revisa este cofre")||text.contains("mira el cofre")){if(!MaoStorage.inspectLookedAtContainer(player))player.sendSystemMessage(Component.literal("Mira a un cofre y vuelve a pedírmelo."));return true;}
        boolean asksChest=text.contains("recoge este cofre")||text.contains("coge esto del cofre")||text.contains("coge")&&text.contains("cofre")||text.contains("vacia el cofre");
        boolean asksCraft=text.contains("craftea")||text.contains("fabrica")||text.contains("crea");
        if(asksChest && asksCraft) {
            if(!MaoStorage.collectLookedAtContainer(player,selected)){player.sendSystemMessage(Component.literal("Mira a un cofre y vuelve a pedírmelo."));return true;}
            Matcher combinedCraft=Pattern.compile("(?:craftea|fabrica|crea)\\s+(?:una?\\s+|el\\s+|la\\s+)?([a-z0-9_]+)").matcher(text);
            if(combinedCraft.find()){ItemStack made=MaoStorage.craft(player.serverLevel(),selected,combinedCraft.group(1));player.sendSystemMessage(Component.literal(made.isEmpty()?"He recogido los materiales, pero no encuentro una receta que pueda fabricar con ellos.":"¡He recogido los objetos del cofre y fabricado "+made.getCount()+"x "+made.getHoverName().getString()+"!"));}
            return true;
        }
        if(asksChest){if(!MaoStorage.collectLookedAtContainer(player,selected))player.sendSystemMessage(Component.literal("Mira a un cofre y vuelve a pedírmelo."));return true;}
        Matcher craft=Pattern.compile("(?:craftea|fabrica|crea)\\s+(?:una?\\s+|el\\s+|la\\s+)?([a-z0-9_]+)").matcher(text);
        if(craft.find()){ItemStack made=MaoStorage.craft(player.serverLevel(),selected,craft.group(1));player.sendSystemMessage(Component.literal(made.isEmpty()?"No tengo los materiales necesarios o no conozco esa receta.":"¡Listo! He fabricado "+made.getCount()+"x "+made.getHoverName().getString()+" y lo he guardado."));return true;}
        return false;
    }

    private static List<MaoEntity> owned(ServerPlayer p) {
        List<MaoEntity> out=new ArrayList<>();
        for(ServerLevel l:p.server.getAllLevels()) for(Entity e:l.getAllEntities()) if(e instanceof MaoEntity m && m.isAlive() && m.isOwnedByUuid(p)) out.add(m);
        out.sort(Comparator.comparing((MaoEntity m)->!m.isRepresentative()).thenComparingDouble(m->m.distanceToSqr(p)));
        return out;
    }
    private static MaoEntity find(ServerPlayer p,String name) {
        String n=name.toLowerCase(Locale.ROOT).trim(); List<MaoEntity> list=owned(p);
        MaoEntity exact=list.stream().filter(m->displayName(m).equalsIgnoreCase(name)|| (m.getCustomName()!=null&&m.getCustomName().getString().equalsIgnoreCase(name))).findFirst().orElse(null);
        if(exact!=null)return exact;
        if(n.matches("mao ?[0-9]+")) {
            String digits=n.replaceAll("[^0-9]", ""); int idx;
            try { idx=Integer.parseInt(digits); } catch(NumberFormatException ex) { idx=-1; }
            if(idx>=1 && idx<=list.size()) return list.get(idx-1);
        }
        return list.stream().filter(m->m.isRepresentative()&&n.equals("mao")).findFirst().orElse(null);
    }
    private static String displayName(MaoEntity m) { return m.getCustomName()==null?(m.isRepresentative()?"Mao":"Mao sin nombre"):m.getCustomName().getString(); }
}

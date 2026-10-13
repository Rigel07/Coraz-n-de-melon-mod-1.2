package com.corazondemelon.ai;

import com.corazondemelon.MelonConfig;
import com.corazondemelon.entity.MaoEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lo que Mao sabe de OTROS mods. No depende de ningún mod en concreto (no hay que añadir librerías): trabaja con los
 * registros de Forge, así que reconoce criaturas, bloques y objetos de cualquier mod instalado.
 *
 * Amigos de Mao: las criaturas de la lista {@code friendEntities} de la config (por defecto Flowys y Nutrias) y las
 * mascotas domesticadas por el mismo dueño. A los amigos Mao no los ataca, los defiende, los abraza y les cura.
 */
public final class MaoMods {
    /** Mods que no se cuentan como "otros mods". */
    private static final Set<String> HIDDEN = Set.of("minecraft", "forge", "corazondemelon", "mixin", "mixinextras");

    /** Lo que Mao sabe de algunos mods concretos (se usa en las respuestas y en el prompt de la IA). */
    private static final Map<String, String> NOTES = Map.of(
            "flowys", "flores vivas que vuelan en bandada, se domestican con agua y dicen cosas bonitas",
            "nutriamod", "nutrias que se domestican con salmón, juegan en el agua y defienden a su dueño",
            "cargakawaii", "las pantallas de carga y el menú, todo muy cute"
    );

    private MaoMods() {}

    // ------------------------------------------------------------------ nombres y mods instalados

    public static String displayName(String namespace) {
        if ("minecraft".equals(namespace)) return "Minecraft";
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    /** " [Nombre del mod]" para cosas de otros mods; vacío para las de Minecraft. */
    public static String modTag(ResourceLocation id) {
        if (id == null || "minecraft".equals(id.getNamespace())) return "";
        return " [" + displayName(id.getNamespace()) + "]";
    }

    static List<IModInfo> installed() {
        List<IModInfo> out = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            if (!HIDDEN.contains(info.getModId())) out.add(info);
        }
        out.sort(Comparator.comparing(IModInfo::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static boolean enabled(ServerPlayer player) {
        if (MelonConfig.MOD_INTERACTIONS.get()) return true;
        MaoActions.say(player, "Tengo desactivado eso de reconocer otros mods (opción recognizeMods en mi config).");
        return false;
    }

    // ------------------------------------------------------------------ amigos

    public static boolean isFriendType(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) return false;
        for (String raw : MelonConfig.FRIEND_ENTITIES.get()) {
            if (raw == null) continue;
            String p = raw.trim().toLowerCase(Locale.ROOT);
            if (p.equals(id.toString())) return true;
            if (p.endsWith(":*") && p.substring(0, p.length() - 2).equals(id.getNamespace())) return true;
        }
        return false;
    }

    public static boolean isFriend(MaoEntity mao, Entity e) {
        if (e == mao || !(e instanceof LivingEntity) || e instanceof Player || e instanceof ArmorStand) return false;
        if (!MelonConfig.MOD_INTERACTIONS.get()) return false;
        if (e instanceof MaoEntity) return true;
        if (e instanceof TamableAnimal t && t.isTame() && mao.getOwnerUUID() != null && mao.getOwnerUUID().equals(t.getOwnerUUID())) {
            return true;
        }
        return isFriendType(e.getType());
    }

    static List<LivingEntity> nearbyLiving(Level level, Entity center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != center && !(e instanceof Player) && !(e instanceof ArmorStand));
    }

    /** El amigo (que no sea otro Mao) más cercano, o null. */
    public static LivingEntity nearestFriend(MaoEntity mao, double radius) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : nearbyLiving(mao.level(), mao, radius)) {
            if (e instanceof MaoEntity || !isFriend(mao, e)) continue;
            double d = e.distanceToSqr(mao);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Una flor al azar (de Minecraft o de cualquier mod: usa la etiqueta de flores). */
    public static Item randomFlower(RandomSource random) {
        ITagManager<Item> tags = ForgeRegistries.ITEMS.tags();
        if (tags == null) return null;
        return tags.getTag(ItemTags.FLOWERS).getRandomElement(random).orElse(null);
    }

    // ------------------------------------------------------------------ texto para el prompt de la IA

    public static String promptMods() {
        List<IModInfo> mods = installed();
        if (mods.isEmpty()) return "ninguno";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (IModInfo i : mods) {
            if (n++ >= 15) {
                sb.append(",y ").append(mods.size() - 15).append(" más");
                break;
            }
            if (sb.length() > 0) sb.append(",");
            sb.append(i.getDisplayName());
        }
        return sb.toString();
    }

    public static String promptKnowledge() {
        StringBuilder sb = new StringBuilder();
        for (IModInfo i : installed()) {
            String note = NOTES.get(i.getModId());
            if (note == null) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(i.getDisplayName()).append(" (").append(note).append(")");
        }
        return sb.toString();
    }

    public static String promptFriendsNearby(MaoEntity mao) {
        Map<String, Integer> count = new LinkedHashMap<>();
        for (LivingEntity e : nearbyLiving(mao.level(), mao, 24.0D)) {
            if (e instanceof MaoEntity || !isFriend(mao, e)) continue;
            count.merge(e.getType().getDescription().getString(), 1, Integer::sum);
        }
        if (count.isEmpty()) return "ninguno";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> en : count.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(en.getKey()).append(" x").append(en.getValue());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ acción: MODS

    static void listMods(ServerPlayer player) {
        if (!enabled(player)) return;
        List<IModInfo> mods = installed();
        if (mods.isEmpty()) {
            MaoActions.say(player, "Solo veo Minecraft y a mí... ¡instala más mods y los reconoceré!");
            return;
        }
        MaoActions.say(player, "¡Reconozco " + mods.size() + (mods.size() == 1 ? " mod" : " mods") + " además de mí!");
        int shown = 0;
        for (IModInfo i : mods) {
            if (shown >= 20) {
                player.sendSystemMessage(Component.literal(" … y " + (mods.size() - 20) + " más").withStyle(ChatFormatting.YELLOW));
                break;
            }
            shown++;
            String note = NOTES.get(i.getModId());
            boolean friend = friendNamespace(i.getModId());
            String line = " • " + i.getDisplayName() + " (" + i.getVersion() + ")"
                    + (friend ? " ♥" : "") + (note != null ? " — " + note : "");
            player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static boolean friendNamespace(String namespace) {
        for (String raw : MelonConfig.FRIEND_ENTITIES.get()) {
            if (raw != null && raw.trim().toLowerCase(Locale.ROOT).startsWith(namespace + ":")) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ acción: MOBS

    private static final class Group {
        int count;
        double best = Double.MAX_VALUE;
        LivingEntity nearest;
        boolean friend;
    }

    private static boolean vanilla(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id == null || "minecraft".equals(id.getNamespace());
    }

    static void listMobs(ServerPlayer player, MaoEntity mao) {
        if (!enabled(player)) return;
        if (MaoActions.cooling(player)) return;
        int r = Math.min(MelonConfig.SCAN_RADIUS.get(), 48);
        Map<EntityType<?>, Group> groups = new LinkedHashMap<>();
        for (LivingEntity e : nearbyLiving(player.level(), player, r)) {
            if (e == mao) continue;
            Group g = groups.computeIfAbsent(e.getType(), k -> new Group());
            g.count++;
            double d = e.distanceToSqr(player);
            if (d < g.best) {
                g.best = d;
                g.nearest = e;
            }
            if (mao != null && isFriend(mao, e)) g.friend = true;
        }
        if (groups.isEmpty()) {
            MaoActions.say(player, "No veo ninguna criatura en " + r + " bloques a la redonda. ¡Qué tranquilidad!");
            return;
        }
        List<Map.Entry<EntityType<?>, Group>> list = new ArrayList<>(groups.entrySet());
        list.sort((a, b) -> {
            int va = vanilla(a.getKey()) ? 1 : 0;
            int vb = vanilla(b.getKey()) ? 1 : 0;
            if (va != vb) return Integer.compare(va, vb);          // primero las de otros mods
            return Double.compare(a.getValue().best, b.getValue().best);
        });
        boolean modded = list.stream().anyMatch(en -> !vanilla(en.getKey()));
        MaoActions.say(player, modded ? "¡Veo criaturas de otros mods por aquí (" + r + " bloques a la redonda)!"
                : "Esto es lo que veo a mi alrededor (" + r + " bloques a la redonda):");
        int shown = 0;
        for (Map.Entry<EntityType<?>, Group> en : list) {
            if (shown++ >= 10) break;
            Group g = en.getValue();
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(en.getKey());
            MutableComponent line = Component.literal(" • ").append(en.getKey().getDescription())
                    .append(Component.literal(" ×" + g.count + modTag(id) + (g.friend ? " ♥" : "")
                            + " — la más cercana a " + MaoActions.describe(player.blockPosition(), g.nearest.blockPosition(), false)));
            player.sendSystemMessage(line.withStyle(ChatFormatting.YELLOW));
        }
    }

    // ------------------------------------------------------------------ acción: QUE_ES

    /** @param param "mano" para priorizar el objeto que lleva el jugador. */
    static void whatIs(ServerPlayer player, String param) {
        if (!enabled(player)) return;
        ItemStack held = player.getMainHandItem();
        if ("mano".equals(param) && !held.isEmpty()) {
            describeItem(player, held);
            return;
        }
        LivingEntity ent = lookedEntity(player, 10.0D);
        if (ent != null) {
            describeEntity(player, ent);
            return;
        }
        HitResult hit = player.pick(10.0D, 1.0F, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            describeBlock(player, bhr);
            return;
        }
        if (!held.isEmpty()) {
            describeItem(player, held);
            return;
        }
        MaoActions.say(player, "No veo nada concreto... Mira un bloque o una criatura, o sostén un objeto, y pregúntame otra vez.");
    }

    private static LivingEntity lookedEntity(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestDot = 0.95D;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
                x -> x != p && x.isAlive() && !(x instanceof ArmorStand))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.1D) continue;
            double dot = to.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    private static String origin(ResourceLocation id) {
        if (id == null || "minecraft".equals(id.getNamespace())) return "Es de Minecraft.";
        String note = NOTES.get(id.getNamespace());
        return "Viene del mod «" + displayName(id.getNamespace()) + "»" + (note != null ? " (" + note + ")" : "") + ".";
    }

    private static void line(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(" • " + text).withStyle(ChatFormatting.YELLOW));
    }

    private static void describeEntity(ServerPlayer player, LivingEntity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        MaoActions.say(player, Component.literal("¡Eso es ").append(e.getName()).append(Component.literal("! " + origin(id))));
        line(player, "ID: " + id);
        line(player, "Vida: " + Math.round(e.getHealth()) + "/" + Math.round(e.getMaxHealth()));
        if (e instanceof TamableAnimal t && t.isTame()) {
            ServerPlayer owner = t.getOwnerUUID() == null ? null : player.server.getPlayerList().getPlayer(t.getOwnerUUID());
            line(player, owner == null ? "Está domesticado." : (owner == player ? "Es tu mascota." : "Su dueño es " + owner.getGameProfile().getName() + "."));
        } else if (e instanceof Monster) {
            line(player, "¡Cuidado, es hostil!");
        }
        MaoEntity mao = MaoEntity.findOwnedMao(player);
        if (mao != null && isFriend(mao, e)) line(player, "¡Es amiguito mío! ♥");
    }

    private static void describeBlock(ServerPlayer player, BlockHitResult hit) {
        BlockState s = player.level().getBlockState(hit.getBlockPos());
        Block b = s.getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        MaoActions.say(player, Component.literal("¡Eso es ").append(b.getName()).append(Component.literal("! " + origin(id))));
        line(player, "ID: " + id);
        if (s.is(Tags.Blocks.ORES)) line(player, "Es un mineral.");
        if (b instanceof CropBlock) line(player, "Es un cultivo: puedo cosecharlo si me dices «cultiva».");
        if (s.is(BlockTags.LOGS)) line(player, "Es un tronco.");
        if (s.getDestroySpeed(player.level(), hit.getBlockPos()) < 0.0F) line(player, "No se puede romper.");
    }

    private static void describeItem(ServerPlayer player, ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        MaoActions.say(player, Component.literal("¡Llevas ").append(stack.getHoverName()).append(Component.literal("! " + origin(id))));
        line(player, "ID: " + id);
        if (stack.getItem().isEdible()) line(player, "Es comida.");
        if (stack.is(ItemTags.FLOWERS)) line(player, "Es una flor: a mis amigos les encantan.");
        if (stack.isDamageableItem()) line(player, "Durabilidad: " + (stack.getMaxDamage() - stack.getDamageValue()) + "/" + stack.getMaxDamage());
        if (stack.isEnchanted()) line(player, "Está encantado.");
    }

    // ------------------------------------------------------------------ acción: ABRAZAR

    private static boolean matchesName(LivingEntity e, String normQuery) {
        String name = MaoPlaces.norm(e.getName().getString());
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        String path = id == null ? "" : id.getPath().replace('_', ' ');
        return name.contains(normQuery) || path.contains(normQuery) || (!name.isEmpty() && normQuery.contains(name));
    }

    static void hug(ServerPlayer player, MaoEntity mao, String param) {
        if (!enabled(player) || mao == null) return;
        String q = param == null ? "" : MaoPlaces.norm(param);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : nearbyLiving(mao.level(), mao, 32.0D)) {
            if (e instanceof MaoEntity || !isFriend(mao, e)) continue;
            if (!q.isEmpty() && !matchesName(e, q)) continue;
            double d = e.distanceToSqr(mao);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) {
            MaoActions.say(player, q.isEmpty() ? "No veo amiguitos cerca para abrazar... ¡busquemos alguno!"
                    : "No veo a «" + param + "» cerca.");
            return;
        }
        mao.setOrderedToSit(false);
        mao.setInSittingPose(false);
        mao.startHug(best);
        MaoActions.say(player, Component.literal("¡Voy a darle un abrazo a ").append(best.getName()).append("!"));
    }
}

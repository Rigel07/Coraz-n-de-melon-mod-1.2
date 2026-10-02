package com.corazondemelon.ai;

import com.corazondemelon.MelonConfig;
import com.corazondemelon.entity.MaoEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Todo lo que Mao sabe hacer como asistente: sentarse/seguir, curar, guardar lugares y teletransportar,
 * buscar minerales y estructuras, romper bloques y cultivar.
 */
public final class MaoActions {
    private static final Map<UUID, Long> HEAVY = new ConcurrentHashMap<>();
    private static final String[] DIRS = {"este", "noreste", "norte", "noroeste", "oeste", "suroeste", "sur", "sureste"};

    private MaoActions() {}

    // ------------------------------------------------------------------ mensajes

    public static void say(ServerPlayer player, Component text) {
        player.sendSystemMessage(Component.literal("<Mao> ").withStyle(ChatFormatting.GOLD)
                .append(text.copy().withStyle(ChatFormatting.WHITE)));
    }

    public static void say(ServerPlayer player, String text) {
        say(player, Component.literal(text));
    }

    public static void sparkle(MaoEntity mao) {
        if (mao != null && mao.isAlive() && mao.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mao.getX(), mao.getY() + 1.1, mao.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        }
    }

    // ------------------------------------------------------------------ despachador

    /** @param announce true si la acción debe confirmar con un mensaje (órdenes locales); false si ya habló la IA. */
    public static void run(ServerPlayer player, MaoEntity mao, String action, String param, boolean announce) {
        if (action == null) return;
        switch (action) {
            case "SENTAR" -> sit(player, mao, announce);
            case "SEGUIR" -> follow(player, mao, announce);
            case "VENIR" -> come(player, mao, announce);
            case "CURAR" -> heal(player, announce);
            case "GUARDAR" -> savePlace(player, param);
            case "IR" -> gotoPlace(player, mao, param);
            case "LUGARES" -> listPlaces(player, param);
            case "BORRAR_LUGAR" -> deletePlace(player, param);
            case "MINERALES" -> scanOres(player, param);
            case "ESTRUCTURAS" -> scanStructures(player);
            case "ROMPER" -> breakBlocks(player, mao, param);
            case "CULTIVAR" -> farm(player, mao);
            default -> { }
        }
    }

    private static boolean cooling(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long last = HEAVY.get(p.getUUID());
        if (last != null && now - last < 2500L) {
            say(p, "Dame un segundito, que todavía estoy terminando lo anterior...");
            return true;
        }
        HEAVY.put(p.getUUID(), now);
        return false;
    }

    // ------------------------------------------------------------------ sentar / seguir / venir / curar

    private static void sit(ServerPlayer player, MaoEntity mao, boolean announce) {
        mao.setOrderedToSit(true);
        mao.setInSittingPose(true);
        mao.getNavigation().stop();
        if (announce) say(player, "¡Vale, me quedo aquí quietecito!");
    }

    private static void follow(ServerPlayer player, MaoEntity mao, boolean announce) {
        mao.setOrderedToSit(false);
        mao.setInSittingPose(false);
        if (mao.level() != player.level() || mao.distanceToSqr(player) > 400.0D) {
            warpNearPlayer(player, mao);
        }
        if (announce) say(player, "¡Voy contigo!");
    }

    private static void come(ServerPlayer player, MaoEntity mao, boolean announce) {
        mao.setOrderedToSit(false);
        mao.setInSittingPose(false);
        warpNearPlayer(player, mao);
        if (announce) say(player, "¡Ya estoy aquí!");
    }

    private static MaoEntity warpNearPlayer(ServerPlayer player, MaoEntity mao) {
        return mao.warpTo(player.serverLevel(), player.getX() + 1.2, player.getY() + 1.0, player.getZ());
    }

    private static void heal(ServerPlayer player, boolean announce) {
        player.heal(4.0F);
        player.serverLevel().sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.8, player.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
        if (announce) say(player, "¡Toma un poco de magia curativa!");
    }

    // ------------------------------------------------------------------ lugares

    private static String dimName(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> dim;
        };
    }

    private static void savePlace(ServerPlayer player, String param) {
        String key = MaoPlaces.cleanName(param);
        if (key.isEmpty()) {
            say(player, "¿Cómo quieres que llame a este sitio? Dime por ejemplo: «guarda este sitio como mi casa».");
            return;
        }
        if (!MaoPlaces.save(player, key)) {
            say(player, "Ya me sé " + MaoPlaces.MAX_PLACES + " sitios, ¡no me caben más! Borra alguno con «olvida el sitio X».");
            return;
        }
        say(player, "¡Guardado! «" + key + "» está en " + player.getBlockX() + ", " + player.getBlockY() + ", "
                + player.getBlockZ() + " (" + dimName(player.level().dimension().location().toString()) + ").");
    }

    private static void deletePlace(ServerPlayer player, String param) {
        if (MaoPlaces.remove(player, param)) {
            say(player, "Vale, me he olvidado de «" + MaoPlaces.cleanName(param) + "».");
        } else {
            say(player, "No tengo ningún sitio llamado «" + MaoPlaces.cleanName(param) + "».");
        }
    }

    private static void listPlaces(ServerPlayer player, String param) {
        if (param != null && !param.isBlank()) {
            MaoPlaces.Place pl = MaoPlaces.get(player, param);
            if (pl == null) {
                say(player, "No tengo ningún sitio llamado «" + MaoPlaces.cleanName(param) + "».");
                return;
            }
            String extra = "";
            if (pl.dim().equals(player.level().dimension().location().toString())) {
                BlockPos here = player.blockPosition();
                extra = " Está a " + describe(here, new BlockPos(pl.x(), pl.y(), pl.z()), false) + ".";
            }
            say(player, "«" + pl.name() + "» está en " + pl.x() + ", " + pl.y() + ", " + pl.z() + " (" + dimName(pl.dim()) + ")." + extra);
            return;
        }
        List<MaoPlaces.Place> list = MaoPlaces.list(player);
        if (list.isEmpty()) {
            say(player, "Todavía no me has pedido guardar ningún sitio. Prueba: «Mao, guarda este sitio como mi casa».");
            return;
        }
        say(player, "Estos son los sitios que me sé:");
        for (MaoPlaces.Place pl : list) {
            player.sendSystemMessage(Component.literal(" • " + pl.name() + " — " + pl.x() + ", " + pl.y() + ", " + pl.z()
                    + " (" + dimName(pl.dim()) + ")").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty();
    }

    private static BlockPos safeSpot(ServerLevel level, BlockPos pos) {
        for (int dy = 0; dy <= 8; dy++) {
            BlockPos p = pos.above(dy);
            if (isFree(level, p) && isFree(level, p.above())) return p;
        }
        return pos;
    }

    private static void gotoPlace(ServerPlayer player, MaoEntity mao, String param) {
        if (!MelonConfig.ALLOW_TELEPORT.get()) {
            say(player, "Mi magia de teletransporte está desactivada en este mundo.");
            return;
        }
        MaoPlaces.Place pl = MaoPlaces.get(player, param);
        if (pl == null) {
            List<MaoPlaces.Place> list = MaoPlaces.list(player);
            String names = list.isEmpty() ? "ninguno todavía" : String.join(", ", list.stream().map(MaoPlaces.Place::name).toList());
            say(player, "No conozco ningún sitio llamado «" + MaoPlaces.cleanName(param) + "». Sitios guardados: " + names + ".");
            return;
        }
        ResourceLocation dimId = ResourceLocation.tryParse(pl.dim());
        ServerLevel dest = dimId == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (dest == null) {
            say(player, "No consigo encontrar esa dimensión... ¿has quitado algún mod?");
            return;
        }
        BlockPos safe = safeSpot(dest, new BlockPos(pl.x(), pl.y(), pl.z()));

        ServerLevel from = player.serverLevel();
        from.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
        from.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        player.teleportTo(dest, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.fallDistance = 0.0F;

        dest.sendParticles(ParticleTypes.PORTAL, safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.2);
        dest.playSound(null, safe, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (mao != null && mao.isAlive() && !mao.isOrderedToSit()) {
            mao.warpTo(dest, safe.getX() + 1.5, safe.getY() + 1.0, safe.getZ() + 0.5);
        }
        say(player, "¡Allá vamos! Te he llevado a «" + pl.name() + "».");
    }

    // ------------------------------------------------------------------ utilidades de texto/ubicación

    /** "12 bloques al noreste, 5 más abajo (x, y, z)". */
    static String describe(BlockPos from, BlockPos to, boolean withCoords) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int horiz = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        StringBuilder sb = new StringBuilder();
        if (horiz <= 1 && Math.abs(dy) <= 2) {
            sb.append("justo aquí");
        } else {
            if (horiz > 1) sb.append(horiz).append(" bloques al ").append(direction(dx, dz));
            if (dy >= 3) sb.append(horiz > 1 ? ", " : "").append(dy).append(" más arriba");
            if (dy <= -3) sb.append(horiz > 1 ? ", " : "").append(-dy).append(" más abajo");
        }
        if (withCoords) sb.append(" (").append(to.getX()).append(", ").append(to.getY()).append(", ").append(to.getZ()).append(")");
        return sb.toString();
    }

    static String direction(int dx, int dz) {
        double deg = Math.toDegrees(Math.atan2(-dz, dx)); // 0 = este, 90 = norte (en Minecraft el norte es -Z)
        int idx = (int) Math.round(deg / 45.0D);
        return DIRS[((idx % 8) + 8) % 8];
    }

    // ------------------------------------------------------------------ filtros de bloques

    private record Kw(String prefix, Predicate<BlockState> test) {}

    private static Predicate<BlockState> pathContains(String sub) {
        return s -> {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
            return id != null && id.getPath().contains(sub);
        };
    }

    private static final List<Kw> KEYWORDS = List.of(
            new Kw("mineral", s -> s.is(Tags.Blocks.ORES)),
            new Kw("mena", s -> s.is(Tags.Blocks.ORES)),
            new Kw("madera", s -> s.is(BlockTags.LOGS)),
            new Kw("tronco", s -> s.is(BlockTags.LOGS)),
            new Kw("arbol", s -> s.is(BlockTags.LOGS)),
            new Kw("lena", s -> s.is(BlockTags.LOGS)),
            new Kw("hoja", s -> s.is(BlockTags.LEAVES)),
            new Kw("tierra", s -> s.is(BlockTags.DIRT)),
            new Kw("arena", s -> s.is(BlockTags.SAND)),
            new Kw("piedra", s -> s.is(Tags.Blocks.STONE) || s.is(Tags.Blocks.COBBLESTONE)),
            new Kw("roca", s -> s.is(Tags.Blocks.STONE) || s.is(Tags.Blocks.COBBLESTONE)),
            new Kw("flor", s -> s.is(BlockTags.FLOWERS)),
            new Kw("diamante", pathContains("diamond_ore")),
            new Kw("hierro", pathContains("iron_ore")),
            new Kw("oro", pathContains("gold_ore")),
            new Kw("carbon", pathContains("coal_ore")),
            new Kw("cobre", pathContains("copper_ore")),
            new Kw("redstone", pathContains("redstone_ore")),
            new Kw("lapis", pathContains("lapis_ore")),
            new Kw("esmeralda", pathContains("emerald_ore")),
            new Kw("cuarzo", pathContains("quartz_ore")),
            new Kw("netherita", pathContains("ancient_debris")),
            new Kw("escombros", pathContains("ancient_debris"))
    );

    private static Predicate<BlockState> matcher(String param) {
        if (param == null || param.isBlank()) return s -> s.is(Tags.Blocks.ORES);
        String kw = MaoPlaces.norm(param);
        for (Kw k : KEYWORDS) {
            if (kw.startsWith(k.prefix())) return k.test();
        }
        String en = kw.replace(' ', '_');
        if (en.length() < 3) return s -> false;
        return pathContains(en);
    }

    // ------------------------------------------------------------------ minerales cercanos

    private static final class Found {
        final Block block;
        int count;
        double best = Double.MAX_VALUE;
        BlockPos pos;

        Found(Block block) {
            this.block = block;
        }
    }

    private static void scanOres(ServerPlayer player, String param) {
        if (cooling(player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos o = player.blockPosition();
        int r = MelonConfig.SCAN_RADIUS.get();
        Predicate<BlockState> test = matcher(param);
        Map<Block, Found> found = new HashMap<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), o.getY() - r);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, o.getY() + r);

        for (int x = o.getX() - r; x <= o.getX() + r; x++) {
            for (int z = o.getZ() - r; z <= o.getZ() + r; z++) {
                mp.set(x, o.getY(), z);
                if (!level.hasChunkAt(mp)) continue;
                for (int y = minY; y <= maxY; y++) {
                    mp.set(x, y, z);
                    BlockState s = level.getBlockState(mp);
                    if (s.isAir() || !test.test(s)) continue;
                    double d = o.distSqr(mp);
                    Found f = found.computeIfAbsent(s.getBlock(), Found::new);
                    f.count++;
                    if (d < f.best) {
                        f.best = d;
                        f.pos = mp.immutable();
                    }
                }
            }
        }

        boolean specific = param != null && !param.isBlank();
        if (found.isEmpty()) {
            say(player, specific
                    ? "No veo «" + param + "» en " + r + " bloques a la redonda. ¡Hay que explorar más!"
                    : "No veo ningún mineral en " + r + " bloques a la redonda. ¡Hay que explorar más!");
            return;
        }
        List<Found> list = new ArrayList<>(found.values());
        list.sort(Comparator.comparingDouble(f -> f.best));
        say(player, specific ? "¡Mira lo que veo (" + r + " bloques a la redonda)!" : "¡Mis ojitos ven minerales (" + r + " bloques a la redonda)!");
        int shown = 0;
        for (Found f : list) {
            if (shown++ >= 8) break;
            MutableComponent line = Component.literal(" • ").append(f.block.getName())
                    .append(Component.literal(" ×" + f.count + " — el más cercano a " + describe(o, f.pos, true)));
            player.sendSystemMessage(line.withStyle(ChatFormatting.YELLOW));
        }
    }

    // ------------------------------------------------------------------ estructuras cercanas

    public record StructInfo(String path, String key, BlockPos center, double dist) {}

    private static final String[][] STRUCT_NAMES = {
            {"village", "una aldea"}, {"pillager_outpost", "un puesto de saqueadores"}, {"mineshaft", "una mina abandonada"},
            {"stronghold", "una fortaleza (stronghold)"}, {"desert_pyramid", "una pirámide del desierto"},
            {"jungle_pyramid", "un templo de la jungla"}, {"igloo", "un iglú"}, {"swamp_hut", "una cabaña de bruja"},
            {"ocean_monument", "un monumento oceánico"}, {"mansion", "una mansión del bosque"},
            {"ruined_portal", "un portal en ruinas"}, {"shipwreck", "un naufragio"}, {"ocean_ruin", "unas ruinas oceánicas"},
            {"buried_treasure", "un tesoro enterrado"}, {"ancient_city", "una ciudad ancestral"},
            {"trail_ruins", "unas ruinas de senda"}, {"fortress", "una fortaleza del Nether"}, {"bastion", "un bastión"},
            {"end_city", "una ciudad del End"}, {"nether_fossil", "un fósil del Nether"}
    };

    static String structName(String path) {
        for (String[] e : STRUCT_NAMES) {
            if (path.contains(e[0])) return e[1];
        }
        return "una estructura (" + path.replace('_', ' ') + ")";
    }

    /** Estructuras ya generadas en los chunks cargados alrededor de la posición (no genera nada nuevo). */
    static List<StructInfo> nearbyStructures(ServerLevel level, BlockPos o, int chunkRadius) {
        List<StructInfo> out = new ArrayList<>();
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        int cx0 = o.getX() >> 4;
        int cz0 = o.getZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx0 + dx, cz0 + dz);
                if (chunk == null) continue;
                for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
                    StructureStart st = e.getValue();
                    if (st == null || !st.isValid()) continue;
                    ResourceLocation id = reg.getKey(e.getKey());
                    if (id == null) continue;
                    BlockPos c = st.getBoundingBox().getCenter();
                    double ddx = c.getX() - o.getX();
                    double ddz = c.getZ() - o.getZ();
                    out.add(new StructInfo(id.getPath(), id + "@" + c.getX() + "," + c.getZ(), c, Math.sqrt(ddx * ddx + ddz * ddz)));
                }
            }
        }
        out.sort(Comparator.comparingDouble(StructInfo::dist));
        return out;
    }

    private static void scanStructures(ServerPlayer player) {
        if (cooling(player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos o = player.blockPosition();
        int chunks = MelonConfig.STRUCT_RADIUS.get();
        Map<String, StructInfo> bestPerType = new HashMap<>();
        for (StructInfo s : nearbyStructures(level, o, chunks)) bestPerType.putIfAbsent(s.path(), s);
        if (bestPerType.isEmpty()) {
            say(player, "No conozco ninguna estructura por aquí (miro unos " + (chunks * 16) + " bloques, solo lo que ya está generado). ¡Explora un poco más!");
            return;
        }
        List<StructInfo> list = new ArrayList<>(bestPerType.values());
        list.sort(Comparator.comparingDouble(StructInfo::dist));
        say(player, "Esto es lo que veo por aquí cerca:");
        int shown = 0;
        for (StructInfo s : list) {
            if (shown++ >= 8) break;
            player.sendSystemMessage(Component.literal(" • " + capitalize(structName(s.path())) + " — a "
                    + describe(o, s.center(), true)).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** Comentario espontáneo de Mao sobre una estructura nueva cerca. @return true si dijo algo. */
    public static boolean announceNearby(ServerPlayer owner, MaoEntity mao) {
        ServerLevel level = owner.serverLevel();
        BlockPos o = owner.blockPosition();
        for (StructInfo s : nearbyStructures(level, o, 6)) {
            if (s.dist() > 90.0D) break;
            if (mao.announced.size() > 300) mao.announced.clear();
            if (!mao.announced.add(s.key())) continue;
            say(owner, "¡Mira! Creo que hay " + structName(s.path()) + " a " + describe(o, s.center(), false) + ".");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ romper bloques

    private static boolean breakable(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.getDestroySpeed(level, pos) < 0.0F) return false;       // bedrock, barreras...
        if (level.getBlockEntity(pos) != null) return false;               // cofres, hornos, spawners...
        if (!level.mayInteract(player, pos)) return false;                 // protección de spawn
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)); // claims de otros mods
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack s = stack.copy();
        player.getInventory().add(s);
        if (!s.isEmpty()) {
            ItemEntity ie = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), s);
            ie.setNoPickUpDelay();
            player.level().addFreshEntity(ie);
        }
    }

    private static void breakBlocks(ServerPlayer player, MaoEntity mao, String param) {
        if (!MelonConfig.ALLOW_BREAK.get()) {
            say(player, "Mis manitas tienen prohibido romper cosas en este mundo.");
            return;
        }
        if (cooling(player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos o = player.blockPosition();
        List<BlockPos> targets = new ArrayList<>();
        boolean looked = param == null || param.isBlank();

        if (looked) {
            HitResult hit = player.pick(24.0D, 1.0F, false);
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                targets.add(bhr.getBlockPos());
            } else {
                say(player, "No veo qué bloque quieres que rompa. Mírale y dime «rompe ese bloque», o dime qué tipo: «rompe la piedra».");
                return;
            }
        } else {
            Predicate<BlockState> test = matcher(param);
            int r = MelonConfig.BREAK_RADIUS.get();
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            int minY = Math.max(level.getMinBuildHeight(), o.getY() - r);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, o.getY() + r);
            for (int x = o.getX() - r; x <= o.getX() + r; x++) {
                for (int z = o.getZ() - r; z <= o.getZ() + r; z++) {
                    mp.set(x, o.getY(), z);
                    if (!level.hasChunkAt(mp)) continue;
                    for (int y = minY; y <= maxY; y++) {
                        mp.set(x, y, z);
                        BlockState s = level.getBlockState(mp);
                        if (!s.isAir() && test.test(s)) targets.add(mp.immutable());
                    }
                }
            }
            targets.sort(Comparator.comparingDouble(p -> o.distSqr(p)));
        }

        int max = MelonConfig.BREAK_MAX.get();
        int broken = 0;
        for (BlockPos pos : targets) {
            if (broken >= max) break;
            BlockState state = level.getBlockState(pos);
            if (!breakable(level, player, pos, state)) continue;
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, mao, ItemStack.EMPTY);
            level.destroyBlock(pos, false, mao);
            for (ItemStack d : drops) giveOrDrop(player, d);
            broken++;
        }

        if (broken == 0) {
            say(player, looked ? "Uy, ese bloque no lo puedo romper." : "No encuentro «" + param + "» que pueda romper a menos de "
                    + MelonConfig.BREAK_RADIUS.get() + " bloques.");
        } else {
            say(player, "¡Listo! He roto " + broken + (broken == 1 ? " bloque" : " bloques") + " y te he traído lo que soltaron."
                    + (broken >= max && targets.size() > broken ? " (Hay más; pídemelo otra vez.)" : ""));
        }
    }

    // ------------------------------------------------------------------ cultivar

    private static boolean hasStemNeighbour(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof AttachedStemBlock) return true;
        }
        return false;
    }

    private static boolean canTouch(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (!level.mayInteract(player, pos)) return false;
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player));
    }

    private static void farm(ServerPlayer player, MaoEntity mao) {
        if (cooling(player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos o = player.blockPosition();
        int r = MelonConfig.FARM_RADIUS.get();
        int harvested = 0;
        int planted = 0;

        List<BlockPos> area = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-r, -3, -r), o.offset(r, 3, r))) {
            if (level.hasChunkAt(p)) area.add(p.immutable());
        }

        // 1) cosechar y replantar
        for (BlockPos pos : area) {
            BlockState state = level.getBlockState(pos);
            Block b = state.getBlock();
            if (b instanceof CropBlock crop && crop.isMaxAge(state)) {
                if (!canTouch(level, player, pos, state)) continue;
                List<ItemStack> drops = Block.getDrops(state, level, pos, null, mao, ItemStack.EMPTY);
                level.setBlock(pos, crop.getStateForAge(0), 3);
                for (ItemStack d : drops) giveOrDrop(player, d);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.0);
                harvested++;
            } else if (b instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= 3) {
                if (!canTouch(level, player, pos, state)) continue;
                List<ItemStack> drops = Block.getDrops(state, level, pos, null, mao, ItemStack.EMPTY);
                level.setBlock(pos, state.setValue(NetherWartBlock.AGE, 0), 3);
                for (ItemStack d : drops) giveOrDrop(player, d);
                harvested++;
            } else if ((state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) && hasStemNeighbour(level, pos)) {
                if (!canTouch(level, player, pos, state)) continue;
                List<ItemStack> drops = Block.getDrops(state, level, pos, null, mao, ItemStack.EMPTY);
                level.destroyBlock(pos, false, mao);
                for (ItemStack d : drops) giveOrDrop(player, d);
                harvested++;
            }
        }

        // 2) sembrar en tierra de cultivo vacía con las semillas del inventario del jugador
        for (BlockPos pos : area) {
            if (!level.getBlockState(pos).isAir()) continue;
            if (!level.getBlockState(pos.below()).is(Blocks.FARMLAND)) continue;
            if (!level.mayInteract(player, pos)) continue;
            for (ItemStack inv : player.getInventory().items) {
                if (inv.isEmpty() || !(inv.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof CropBlock cb)) continue;
                BlockState seed = cb.defaultBlockState();
                if (!seed.canSurvive(level, pos)) continue;
                level.setBlock(pos, seed, 3);
                if (!player.getAbilities().instabuild) inv.shrink(1);
                planted++;
                break;
            }
        }

        if (harvested == 0 && planted == 0) {
            say(player, "No veo cultivos maduros ni tierra de cultivo vacía a menos de " + r + " bloques. ¡Ponte cerca de tu huerto!");
            return;
        }
        level.playSound(null, o, SoundEvents.CROP_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
        say(player, "¡Huerto listo! Coseché " + harvested + (harvested == 1 ? " cultivo" : " cultivos") + " (y los volví a plantar)"
                + (planted > 0 ? " y sembré " + planted + " más con tus semillas" : "") + ".");
    }
}

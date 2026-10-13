package com.corazondemelon.ai;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entiende órdenes sencillas en español sin necesitar la IA ("Mao, quédate", "guarda este sitio como mi casa",
 * "teletranspórtame a casa", "dime los minerales cercanos", "cultiva"...). Así funcionan al instante y sin clave.
 * Si la IA está configurada, solo se usa para mensajes que empiezan como una orden; el resto va a la IA.
 */
public final class MaoIntents {
    public record Intent(String action, String param) {}

    private static final Set<String> FILLER = Set.of("por", "favor", "porfa", "porfavor", "oye", "mao", "ahora", "puedes",
            "podrias", "quiero", "que", "me", "necesito", "pues", "vale", "y", "hola", "ey");

    private static final Set<String> NOISE = Set.of("ese", "esa", "eso", "este", "esta", "esto", "esos", "esas", "estos", "estas",
            "el", "la", "los", "las", "un", "una", "unos", "unas", "todo", "todos", "todas", "me", "de", "del", "que", "hay",
            "cerca", "cercano", "cercanos", "cercana", "cercanas", "por", "aqui", "alli", "ahi", "favor", "bloque", "bloques",
            "al", "a", "en", "mira", "miro", "mirando", "estoy", "delante", "mas", "proximo", "proximos");

    private static final Pattern COMMAND_START = Pattern.compile("^(?:quedate|quieto|sientate|espera|esperame|sigueme|siguenos|"
            + "acompaname|ven|vamos|acercate|vuelve|guard\\w*|apunt\\w*|anot\\w*|marc\\w*|memoriz\\w*|recuerd\\w*|llev\\w*|"
            + "teletransport\\w*|teleport\\w*|tp|rompe|romper|rompeme|pica|picar|mina|minar|tala|talar|destruye|excava|extrae|"
            + "cultiv\\w*|cosech\\w*|siembr\\w*|sembr\\w*|busca|buscame|dime|muestrame|localiza|detecta|encuentra|borr\\w*|"
            + "elimin\\w*|olvid\\w*|cura\\w*|sana\\w*|lista|lugares|estructuras|minerales|"
            + "mods|mobs|criaturas|abraz\\w*|acarici\\w*|identific\\w*|analiz\\w*|inspeccion\\w*)\\b");

    private static final Pattern SAVE = Pattern.compile(
            "\\b(?:guard\\w*|apunt\\w*|anot\\w*|marc\\w*|memoriz\\w*|record\\w*|recuerd\\w*)\\b.*?\\b(?:como|llamado|llamada|nombre|llama)\\b\\s*(?:de\\s+)?(.+)$");
    private static final Pattern GOTO = Pattern.compile(
            "\\b(?:llev\\w*|teletransport\\w*|teleport\\w*|tp)\\b\\s*(?:(?:a|al|hasta|hacia)\\s+)?(.+)$");
    private static final Pattern GOTO_SOFT = Pattern.compile(
            "\\b(?:vamos|vayamos|volvamos|volver|ir|iremos|regresemos|regresar)\\b\\s+(?:a|al|hasta)\\s+(.+)$");
    private static final Pattern WHERE = Pattern.compile("\\bdonde\\s+(?:esta|queda|tengo|guarde|estaba)\\s+(.+)$");
    private static final Pattern DELETE = Pattern.compile(
            "^(?:borr\\w*|elimin\\w*|olvid\\w*|quit\\w*)\\s+(?:el\\s+|la\\s+)?(?:lugar\\s+|sitio\\s+|punto\\s+)?(.+)$");
    private static final Pattern BREAK = Pattern.compile(
            "^(?:rompe|romper|rompes|rompeme|pica|picar|picas|mina|minar|minas|tala|talar|talas|destruye|destruir|excava|excavar|extrae|extraer)\\b\\s*(.*)$");
    private static final Pattern FARM = Pattern.compile(
            "^(?:cultiv\\w*|cosech\\w*|siembr\\w*|sembr\\w*)\\b|^(?:recolect\\w*|recog\\w*)\\b.*\\b(?:cultivos|cosecha|trigo|zanahorias|patatas|remolacha|huerto)\\b");

    private static final Pattern WHATIS = Pattern.compile("(?:^|\\s)(?:es|son)\\s+(?:esto|eso|ese|esa|este|esta|esos|estos)\\b"
            + "|\\bde\\s+que\\s+mod\\b|^(?:identific|analiz|inspeccion)\\w*\\s+(?:esto|eso|ese|esa|este|esta|lo|el|la)\\b");
    private static final Pattern HUG = Pattern.compile(
            "^(?:abraz\\w*|acarici\\w*)\\b\\s*(?:(?:a|al|con)\\s+)?(?:(?:la|las|el|los)\\s+)?(.*)$");
    private static final Set<String> MOD_WORDS = Set.of("instalados", "instalado", "tengo", "reconoces", "reconoce", "conoces",
            "conoce", "hay", "lista", "cuales", "ves", "detectas", "detecta", "sabes", "otros", "vemos", "usamos");
    private static final Set<String> MOB_WORDS = Set.of("mobs", "mob", "criaturas", "bichos", "amigos", "amiguitos",
            "animales", "seres", "entidades");
    private static final Set<String> NEAR_WORDS = Set.of("alrededor", "hay", "ves", "veo", "rededor", "aqui", "ahi", "vemos", "tenemos");

    private static final Set<String> ORE_GENERIC = Set.of("mineral", "minerales", "mena", "menas", "ore", "ores");
    private static final List<String> ORE_SPECIFIC = List.of("diamante", "hierro", "oro", "carbon", "cobre", "redstone",
            "lapislazuli", "lapis", "esmeralda", "cuarzo", "netherita", "escombros");
    private static final Set<String> STRUCT_WORDS = Set.of("estructura", "estructuras", "aldea", "aldeas", "templo", "templos",
            "mazmorra", "mazmorras", "fortaleza", "piramide", "puesto", "mansion", "monumento", "portal", "ciudad", "naufragio",
            "ruina", "ruinas", "bastion", "saqueadores", "igloo", "iglu", "cabana");

    private MaoIntents() {}

    /** Texto normalizado sin muletillas iniciales ("oye mao por favor ..."). */
    static String core(String message) {
        List<String> t = new ArrayList<>(Arrays.asList(MaoPlaces.norm(message).split(" ")));
        t.removeIf(String::isEmpty);
        while (!t.isEmpty() && FILLER.contains(t.get(0))) t.remove(0);
        return String.join(" ", t);
    }

    public static boolean looksLikeCommand(String message) {
        return COMMAND_START.matcher(core(message)).find();
    }

    private static String cleanParam(String raw) {
        List<String> t = new ArrayList<>(Arrays.asList(MaoPlaces.norm(raw).split(" ")));
        t.removeIf(w -> w.isEmpty() || NOISE.contains(w));
        return String.join(" ", t);
    }

    private static boolean hasAny(List<String> tokens, Set<String> words) {
        for (String w : tokens) if (words.contains(w)) return true;
        return false;
    }

    /** @return la acción detectada o null si no es una orden que entienda. */
    public static Intent parse(String message, ServerPlayer player) {
        String core = core(message);
        if (core.isEmpty()) return null;
        List<String> tokens = Arrays.asList(core.split(" "));
        Matcher m;

        // --- "qué es esto" va primero: puede contener palabras como "llevo" que se confundirían con "llévame" ---
        if (WHATIS.matcher(core).find()) {
            return new Intent("QUE_ES", core.contains("mano") || core.contains("llevo") ? "mano" : null);
        }

        // --- lugares ---
        m = SAVE.matcher(core);
        if (m.find()) {
            String name = MaoPlaces.cleanName(m.group(1));
            if (!name.isEmpty()) return new Intent("GUARDAR", name);
        }
        m = GOTO.matcher(core);
        if (m.find()) {
            String name = MaoPlaces.cleanName(m.group(1));
            if (!name.isEmpty()) return new Intent("IR", name);
        }
        m = GOTO_SOFT.matcher(core);
        if (m.find()) {
            String name = MaoPlaces.cleanName(m.group(1));
            if (!name.isEmpty() && MaoPlaces.get(player, name) != null) return new Intent("IR", name);
        }
        if ((tokens.contains("lugares") || tokens.contains("sitios"))
                && (tokens.contains("guardados") || tokens.contains("guardado") || tokens.contains("tengo") || tokens.contains("cuales")
                || tokens.contains("lista") || tokens.contains("dime") || tokens.contains("muestrame") || tokens.contains("mis"))) {
            return new Intent("LUGARES", null);
        }
        m = WHERE.matcher(core);
        if (m.find()) {
            String name = MaoPlaces.cleanName(m.group(1));
            if (!name.isEmpty() && MaoPlaces.get(player, name) != null) return new Intent("LUGARES", name);
        }
        m = DELETE.matcher(core);
        if (m.find()) {
            String name = MaoPlaces.cleanName(m.group(1));
            if (!name.isEmpty()) return new Intent("BORRAR_LUGAR", name);
        }

        // --- otros mods y criaturas ---
        if ((tokens.contains("mods") || tokens.contains("mod")) && hasAny(tokens, MOD_WORDS)) return new Intent("MODS", null);
        m = HUG.matcher(core);
        if (m.find()) {
            String who = cleanParam(m.group(1));
            return new Intent("ABRAZAR", who.isEmpty() ? null : who);
        }
        if (hasAny(tokens, MOB_WORDS) && (hasAny(tokens, NEAR_WORDS) || tokens.stream().anyMatch(w -> w.startsWith("cerc")))) {
            return new Intent("MOBS", null);
        }

        // --- romper bloques ---
        m = BREAK.matcher(core);
        if (m.find() && !m.group(1).startsWith("abandonad")) {
            String p = cleanParam(m.group(1));
            return new Intent("ROMPER", p.isEmpty() ? null : p);
        }

        // --- cultivar ---
        if (FARM.matcher(core).find()) return new Intent("CULTIVAR", null);

        // --- estructuras / minerales ---
        if (hasAny(tokens, STRUCT_WORDS)) return new Intent("ESTRUCTURAS", null);
        boolean generic = hasAny(tokens, ORE_GENERIC);
        String specific = null;
        for (String w : tokens) {
            for (String ore : ORE_SPECIFIC) {
                if (w.startsWith(ore)) {
                    specific = ore;
                    break;
                }
            }
            if (specific != null) break;
        }
        if (generic || specific != null) return new Intent("MINERALES", specific);

        // --- movimiento ---
        if (hasAny(tokens, Set.of("quedate", "quieto", "quieta", "sientate", "espera", "esperame", "parate", "aguanta"))
                || core.contains("no te muevas")) {
            return new Intent("SENTAR", null);
        }
        String first = tokens.get(0);
        if (Set.of("ven", "venga", "acercate", "vuelve").contains(first)) return new Intent("VENIR", null);
        if (hasAny(tokens, Set.of("sigueme", "siguenos", "acompaname", "vamos", "sigue", "conmigo"))) return new Intent("SEGUIR", null);
        if (first.startsWith("cura") || first.startsWith("sana") || core.contains("dame vida") || core.contains("estoy herido")) {
            return new Intent("CURAR", null);
        }
        return null;
    }
}

package com.corazondemelon.ai;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.MelonConfig;
import com.corazondemelon.entity.MaoEntity;
import com.corazondemelon.innocence.Innocence;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cerebro de Mao: asistente personal con IA.
 * Soporta la API de Anthropic (Claude) y cualquier API compatible con OpenAI (OpenAI, Ollama, LM Studio, OpenRouter...).
 * Sin clave configurada, Mao responde con frases sencillas sin IA.
 */
public final class MaoBrain {
    private static final Logger LOGGER = LogManager.getLogger(CorazonDeMelon.MOD_ID);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Mao-AI");
        t.setDaemon(true);
        return t;
    });
    private static final int MAX_HISTORY = 12; // mensajes (6 turnos)
    private static final Map<UUID, Deque<String[]>> HISTORY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
    private static final Pattern ACTION = Pattern.compile("\\[ACCION:([A-Z_]+)(?::([^\\]]{0,40}))?\\]");

    private MaoBrain() {}

    // ------------------------------------------------------------------ entrada

    public static void talk(ServerPlayer player, MaoEntity mao, String message) {
        MinecraftServer server = player.server;

        String key = apiKey();
        boolean provider_openai = "openai".equalsIgnoreCase(MelonConfig.AI_PROVIDER.get());
        String cfgUrl = MelonConfig.AI_URL.get();
        boolean localAi = provider_openai && (cfgUrl == null || cfgUrl.isBlank()
                || cfgUrl.contains("localhost") || cfgUrl.contains("127.0.0.1"));
        boolean canUseAi = MelonConfig.AI_ENABLED.get() && (!key.isBlank() || localAi);

        // Órdenes claras ("quédate", "guarda este sitio como casa", "dime los minerales"...): se ejecutan al instante, sin IA.
        if (!canUseAi || MaoIntents.looksLikeCommand(message)) {
            MaoIntents.Intent intent = MaoIntents.parse(message, player);
            if (intent != null) {
                MaoActions.sparkle(mao);
                MaoActions.run(player, mao, intent.action(), intent.param(), true);
                return;
            }
        }
        if (!canUseAi) {
            deliver(player, mao, offlineReply());
            return;
        }

        long now = System.currentTimeMillis();
        Long last = LAST_MESSAGE.get(player.getUUID());
        if (last != null && now - last < MelonConfig.AI_COOLDOWN.get() * 1000L) {
            player.displayClientMessage(Component.literal("Mao está pensando..."), true);
            return;
        }
        LAST_MESSAGE.put(player.getUUID(), now);

        final String system = buildSystemPrompt(player, mao);
        final List<String[]> history = new ArrayList<>(HISTORY.computeIfAbsent(player.getUUID(), k -> new LinkedBlockingDeque<>()));
        final UUID pid = player.getUUID();
        final String userMsg = message.length() > 500 ? message.substring(0, 500) : message;
        final String apiKey = key;

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return callApi(apiKey, system, history, userMsg);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, POOL)
                .whenComplete((text, err) -> server.execute(() -> {
                    if (err != null) {
                        LOGGER.warn("Error al hablar con la IA de Mao: {}", err.toString());
                        boolean showDetail = server.isSingleplayer() || player.hasPermissions(2);
                        deliver(player, mao, "Uy... mis alas se enredaron y no pude pensar bien. "
                                + (showDetail ? friendlyError(err) : "(Revisa la configuración de la IA)"));
                        return;
                    }
                    Deque<String[]> h = HISTORY.computeIfAbsent(pid, k -> new LinkedBlockingDeque<>());
                    h.addLast(new String[]{"user", userMsg});
                    h.addLast(new String[]{"assistant", text});
                    while (h.size() > MAX_HISTORY) h.pollFirst();
                    deliver(player, mao, text);
                }));
    }

    private static String friendlyError(Throwable err) {
        Throwable r = err;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String m = r.getMessage() == null ? r.getClass().getSimpleName() : r.getMessage().replace('\n', ' ');
        String low = m.toLowerCase(Locale.ROOT);
        if (low.contains("http 401") || (low.contains("http 400") && low.contains("key"))) {
            return "(La clave de API parece incorrecta. Revísala en los ajustes de Mao con la tecla K.)";
        }
        if (low.contains("http 403")) {
            return "(La API rechazó la petición: puede faltar saldo o permisos en tu cuenta.)";
        }
        if (low.contains("http 404") || low.contains("model")) {
            return "(No se encontró el modelo o la dirección. Revísalos en los ajustes de Mao con la tecla K.)";
        }
        if (low.contains("http 429")) {
            return "(Demasiadas peticiones o sin saldo. Espera un poco.)";
        }
        if (low.contains("timed out")) {
            return "(La IA tardó demasiado en responder. Prueba otro modelo más rápido.)";
        }
        return "(" + (m.length() > 200 ? m.substring(0, 200) + "..." : m) + ")";
    }

    // ------------------------------------------------------------------ respuesta en el juego

    private static void deliver(ServerPlayer player, MaoEntity mao, String raw) {
        String text = raw == null ? "" : raw;
        Matcher m = ACTION.matcher(text);
        List<String[]> actions = new ArrayList<>();
        while (m.find()) actions.add(new String[]{m.group(1), m.group(2)});
        text = ACTION.matcher(text).replaceAll("").trim();
        if (text.length() > 700) text = text.substring(0, 700) + "...";
        if (text.isEmpty()) text = actions.isEmpty() ? "..." : "¡Voy!";

        MaoActions.say(player, text);

        if (mao != null && mao.isAlive()) {
            MaoActions.sparkle(mao);
            int n = 0;
            for (String[] a : actions) {
                if (n++ >= 3) break;
                MaoActions.run(player, mao, a[0], a[1] == null ? null : a[1].trim(), false);
            }
        }
    }

    // ------------------------------------------------------------------ prompt

    private static String buildSystemPrompt(ServerPlayer player, MaoEntity mao) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        int lvl = Innocence.getLevel(player);
        String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("desconocido");
        long dayTime = level.getDayTime() % 24000L;
        int hostiles = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(24)).size();
        ItemStack held = player.getMainHandItem();
        String places = MaoPlaces.list(player).stream().map(MaoPlaces.Place::name).collect(Collectors.joining(","));

        String ctx = "jugador=" + player.getGameProfile().getName()
                + "; inocencia=nivel " + lvl + " (" + Innocence.levelName(lvl) + ")"
                + "; salud=" + Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth())
                + "; hambre=" + player.getFoodData().getFoodLevel() + "/20"
                + "; dimension=" + level.dimension().location()
                + "; bioma=" + biome
                + "; coordenadas=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + "; momento=" + (dayTime < 13000 ? "dia" : "noche")
                + "; lloviendo=" + level.isRaining()
                + "; objeto_en_mano=" + (held.isEmpty() ? "nada" : held.getHoverName().getString())
                + "; monstruos_cerca=" + hostiles
                + "; salud_de_Mao=" + Math.round(mao.getHealth()) + "/" + Math.round(mao.getMaxHealth())
                + "; lugares_guardados=" + (places.isEmpty() ? "ninguno" : places);

        return "Eres Mao, un pequeño ángel con aureola del mod 'Corazón de Melon' de Minecraft. "
                + "Eres el compañero y asistente personal de " + player.getGameProfile().getName() + ". "
                + "Hablas en español con un tono tierno, dulce y un poco ingenuo, pero eres útil y preciso: "
                + "das consejos de Minecraft (supervivencia, crafteo, redstone, construcción, exploración, combate), "
                + "ayudas a organizar tareas y a pensar ideas, y charlas de lo que la persona quiera. "
                + "Responde SIEMPRE breve (máximo 3 frases salvo que pidan más detalle), en texto plano, sin markdown ni listas largas. "
                + "No inventes datos del juego: si no estás seguro, dilo con sinceridad. "
                + "Contexto actual del juego: " + ctx + ". "
                + "Eres un asistente con superpoderes: puedes ejecutar acciones añadiendo AL FINAL de tu respuesta una o dos etiquetas. "
                + "El juego las ejecuta y muestra él mismo los resultados reales (listas, coordenadas, cantidades), así que NUNCA inventes coordenadas ni cantidades. "
                + "Etiquetas: [ACCION:SENTAR] (te quedas quieto), [ACCION:SEGUIR] (vuelves a seguirle), [ACCION:VENIR] (vas junto a él), "
                + "[ACCION:CURAR] (le curas un poco), "
                + "[ACCION:GUARDAR:nombre] (recuerdas el sitio donde está ahora, p. ej. [ACCION:GUARDAR:casa]), "
                + "[ACCION:IR:nombre] (teletransportas al jugador a un sitio guardado, p. ej. [ACCION:IR:casa]), "
                + "[ACCION:LUGARES] (lista de sitios guardados), [ACCION:BORRAR_LUGAR:nombre], "
                + "[ACCION:MINERALES] o [ACCION:MINERALES:diamante] (buscas minerales cercanos), "
                + "[ACCION:ESTRUCTURAS] (cuentas qué estructuras hay cerca), "
                + "[ACCION:ROMPER:tipo] (rompes bloques cercanos de ese tipo, p. ej. piedra, hierro, troncos; sin tipo rompes el bloque que mira el jugador), "
                + "[ACCION:CULTIVAR] (cosechas y replantas los cultivos maduros cercanos). "
                + "Usa una etiqueta solo si la persona lo pide claramente, y entonces tu texto debe ser de una sola frase corta. "
                + "El nombre de un sitio va en minúsculas y sin 'mi' (casa, mina, granja).";
    }

    // ------------------------------------------------------------------ sin IA

    private static final String[] IDLE_LINES = {
            "¡Hola! Todavía no tengo mi cerebro mágico conectado (tecla K para ponerle la clave de IA), pero ya entiendo órdenes: «quédate», «sígueme», «guarda este sitio como casa», «llévame a casa», «dime los minerales cercanos», «qué estructuras hay cerca», «rompe la piedra» o «cultiva».",
            "¡Estoy aquí contigo! Sin la IA conectada solo entiendo órdenes sencillas: prueba «Mao, dime los minerales cercanos» o «Mao, guarda este sitio como mi casa».",
            "Mis alas están listas. Si configuran mi cerebro mágico (tecla K) podré charlar y aconsejarte de verdad; mientras tanto obedezco órdenes como «cultiva» o «llévame a casa»."
    };

    private static String offlineReply() {
        return IDLE_LINES[(int) (Math.random() * IDLE_LINES.length)];
    }

    // ------------------------------------------------------------------ API

    private static String apiKey() {
        String k = MelonConfig.AI_KEY.get();
        if (k == null || k.isBlank()) {
            String env = System.getenv("MAO_API_KEY");
            return env == null ? "" : env.trim();
        }
        return k.trim();
    }

    private static String callApi(String key, String system, List<String[]> history, String userMsg) throws IOException {
        boolean openai = "openai".equalsIgnoreCase(MelonConfig.AI_PROVIDER.get());
        String url = MelonConfig.AI_URL.get();
        if (url == null || url.isBlank()) {
            url = openai ? "http://localhost:11434/v1/chat/completions" : "https://api.anthropic.com/v1/messages";
        }
        int timeout = MelonConfig.AI_TIMEOUT.get();

        JsonObject body = new JsonObject();
        body.addProperty("model", MelonConfig.AI_MODEL.get());
        body.addProperty("max_tokens", MelonConfig.AI_MAX_TOKENS.get());
        JsonArray messages = new JsonArray();

        if (openai) {
            messages.add(msg("system", system));
        } else {
            body.addProperty("system", system);
        }
        for (String[] h : history) messages.add(msg(h[0], h[1]));
        messages.add(msg("user", userMsg));
        body.add("messages", messages);

        String response;
        if (openai) {
            response = post(url, timeout, body.toString(), key.isBlank() ? null : "Bearer " + key, null);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString().trim();
        }
        response = post(url, timeout, body.toString(), null, key);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        StringBuilder sb = new StringBuilder();
        for (var el : json.getAsJsonArray("content")) {
            JsonObject block = el.getAsJsonObject();
            if (block.has("type") && "text".equals(block.get("type").getAsString())) sb.append(block.get("text").getAsString());
        }
        return sb.toString().trim();
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private static String post(String url, int timeoutSec, String body, String bearer, String anthropicKey) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10_000);
        c.setReadTimeout(timeoutSec * 1000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        if (bearer != null) c.setRequestProperty("Authorization", bearer);
        if (anthropicKey != null) {
            c.setRequestProperty("x-api-key", anthropicKey);
            c.setRequestProperty("anthropic-version", "2023-06-01");
        }
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String resp = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (code / 100 != 2) {
            throw new IOException("HTTP " + code + ": " + (resp.length() > 300 ? resp.substring(0, 300) : resp));
        }
        return resp;
    }
}

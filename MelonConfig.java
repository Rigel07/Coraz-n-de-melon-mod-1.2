package com.corazondemelon;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Configuración en config/corazondemelon-common.toml */
public final class MelonConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue HEART_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue HEART_CHANCE;
    public static final ForgeConfigSpec.IntValue MAX_HEARTS_NEARBY;
    public static final ForgeConfigSpec.BooleanValue HEARTS_GLOW;
    public static final ForgeConfigSpec.DoubleValue MAO_SPAWN_CHANCE;
    public static final ForgeConfigSpec.IntValue MAO_MAX_NEARBY;

    public static final ForgeConfigSpec.IntValue SCAN_RADIUS;
    public static final ForgeConfigSpec.IntValue BREAK_RADIUS;
    public static final ForgeConfigSpec.IntValue BREAK_MAX;
    public static final ForgeConfigSpec.IntValue FARM_RADIUS;
    public static final ForgeConfigSpec.IntValue STRUCT_RADIUS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_TELEPORT;
    public static final ForgeConfigSpec.BooleanValue ALLOW_BREAK;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_STRUCTURES;

    public static final ForgeConfigSpec.BooleanValue MOD_INTERACTIONS;
    public static final ForgeConfigSpec.BooleanValue PROTECT_FRIENDS;
    public static final ForgeConfigSpec.BooleanValue MOD_HUGS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FRIEND_ENTITIES;

    public static final ForgeConfigSpec.BooleanValue AI_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER;
    public static final ForgeConfigSpec.ConfigValue<String> AI_URL;
    public static final ForgeConfigSpec.ConfigValue<String> AI_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> AI_MODEL;
    public static final ForgeConfigSpec.IntValue AI_MAX_TOKENS;
    public static final ForgeConfigSpec.IntValue AI_COOLDOWN;
    public static final ForgeConfigSpec.IntValue AI_TIMEOUT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("gameplay");
        XP_MULTIPLIER = b.comment("Multiplicador de toda la experiencia de Inocencia (1.0 = normal).")
                .defineInRange("xpMultiplier", 1.0, 0.0, 100.0);
        HEART_INTERVAL_SECONDS = b.comment("Cada cuántos segundos se intenta generar un corazón cerca de cada jugador.")
                .defineInRange("heartSpawnIntervalSeconds", 45, 5, 3600);
        HEART_CHANCE = b.comment("Probabilidad (0-1) de que el intento genere un corazón.")
                .defineInRange("heartSpawnChance", 0.40, 0.0, 1.0);
        MAX_HEARTS_NEARBY = b.comment("Máximo de corazones en el suelo en 80 bloques alrededor del jugador.")
                .defineInRange("maxHeartsNearby", 2, 0, 50);
        HEARTS_GLOW = b.comment("Los corazones del suelo brillan (se ven a través de bloques). Ponlo en false para hacerlo más difícil.")
                .define("heartsGlow", true);
        MAO_SPAWN_CHANCE = b.comment("Probabilidad (0-1) por intento de que aparezca un Mao salvaje cerca de un jugador (de día, en el Overworld).")
                .defineInRange("maoSpawnChance", 0.25, 0.0, 1.0);
        MAO_MAX_NEARBY = b.comment("Máximo de Maos salvajes en 128 bloques alrededor de cada jugador (así hay varios repartidos por el mundo).")
                .defineInRange("maoMaxNearby", 4, 1, 20);
        b.pop();

        b.push("assistant");
        SCAN_RADIUS = b.comment("Radio (en bloques) en el que Mao busca minerales cuando se lo pides.")
                .defineInRange("scanRadius", 32, 8, 64);
        BREAK_RADIUS = b.comment("Radio (en bloques) en el que Mao rompe bloques de un tipo cuando se lo pides.")
                .defineInRange("breakRadius", 16, 4, 32);
        BREAK_MAX = b.comment("Máximo de bloques que Mao rompe por orden.")
                .defineInRange("breakMaxBlocks", 32, 1, 256);
        FARM_RADIUS = b.comment("Radio (en bloques) en el que Mao cosecha y replanta cuando le dices que cultive.")
                .defineInRange("farmRadius", 10, 3, 24);
        STRUCT_RADIUS = b.comment("Radio (en chunks) en el que Mao busca estructuras ya generadas.")
                .defineInRange("structureRadiusChunks", 12, 2, 32);
        ALLOW_TELEPORT = b.comment("Permite que Mao te teletransporte a los sitios guardados (\"llévame a casa\").")
                .define("allowTeleport", true);
        ALLOW_BREAK = b.comment("Permite que Mao rompa bloques cuando se lo pides. Ponlo en false en servidores donde no quieras esto.")
                .define("allowBreakBlocks", true);
        ANNOUNCE_STRUCTURES = b.comment("Mao te avisa solo cuando ve una estructura nueva cerca.")
                .define("announceStructures", true);
        b.pop();

        b.push("mods");
        MOD_INTERACTIONS = b.comment("Mao reconoce criaturas, bloques y objetos de otros mods y convive con ellos. false = apaga todo este apartado.")
                .define("recognizeMods", true);
        FRIEND_ENTITIES = b.comment("Criaturas amigas de Mao: 'modid:*' (todas las de un mod) o 'modid:criatura'. Mao no las ataca, las defiende y las abraza. "
                        + "Las mascotas domesticadas por el mismo dueño siempre cuentan como amigas.")
                .defineList("friendEntities", List.of("flowys:*", "nutriamod:*"), o -> o instanceof String);
        PROTECT_FRIENDS = b.comment("Mao ataca a quien esté atacando a sus amigos.")
                .define("protectFriends", true);
        MOD_HUGS = b.comment("Mao va solito a abrazar a sus amigos cercanos (los cura un poco y sube tu Inocencia de vez en cuando).")
                .define("hugFriends", true);
        b.pop();

        b.push("ai");
        AI_ENABLED = b.comment("Activa la conversación con Mao mediante IA. Si es false o no hay clave, Mao usa respuestas sencillas sin IA.")
                .define("enabled", true);
        AI_PROVIDER = b.comment("'anthropic' (API de Claude) u 'openai' (cualquier API compatible con OpenAI: OpenAI, Ollama local, LM Studio, OpenRouter...).")
                .define("provider", "anthropic");
        AI_URL = b.comment("URL de la API. Vacío = por defecto (anthropic: https://api.anthropic.com/v1/messages, openai: http://localhost:11434/v1/chat/completions).")
                .define("apiUrl", "");
        AI_KEY = b.comment("Clave de la API. También puede ponerse en la variable de entorno MAO_API_KEY. NO subas este archivo a GitHub con tu clave.")
                .define("apiKey", "");
        AI_MODEL = b.comment("Modelo a usar.")
                .define("model", "claude-haiku-4-5-20251001");
        AI_MAX_TOKENS = b.defineInRange("maxTokens", 300, 50, 2000);
        AI_COOLDOWN = b.comment("Segundos mínimos entre mensajes de un mismo jugador a Mao.")
                .defineInRange("cooldownSeconds", 3, 0, 300);
        AI_TIMEOUT = b.defineInRange("timeoutSeconds", 30, 5, 120);
        b.pop();

        SPEC = b.build();
    }

    private MelonConfig() {}
}

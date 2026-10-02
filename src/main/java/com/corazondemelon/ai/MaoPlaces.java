package com.corazondemelon.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lugares que el jugador le pide guardar a Mao ("guarda este sitio como mi casa"). Se guardan en los datos del jugador. */
public final class MaoPlaces {
    public static final String KEY = "corazondemelon_places";
    public static final int MAX_PLACES = 40;

    private static final Set<String> LEADING = Set.of("mi", "mis", "el", "la", "los", "las", "un", "una", "de", "del", "a", "al");
    private static final Set<String> TRAILING = Set.of("por", "favor", "porfa", "ahora", "gracias", "porfavor");

    public record Place(String name, int x, int y, int z, String dim) {}

    private MaoPlaces() {}

    /** Minúsculas, sin tildes ni signos. */
    public static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Nombre de lugar limpio: "mi casa" -> "casa". Devuelve "" si no queda nada útil. */
    public static String cleanName(String raw) {
        List<String> t = new ArrayList<>(List.of(norm(raw).split(" ")));
        t.removeIf(String::isEmpty);
        while (!t.isEmpty() && LEADING.contains(t.get(0))) t.remove(0);
        while (!t.isEmpty() && TRAILING.contains(t.get(t.size() - 1))) t.remove(t.size() - 1);
        String name = String.join(" ", t);
        return name.length() > 24 ? name.substring(0, 24).trim() : name;
    }

    private static CompoundTag root(ServerPlayer p) {
        return p.getPersistentData().getCompound(KEY);
    }

    public static boolean save(ServerPlayer p, String key) {
        CompoundTag root = root(p);
        if (!root.contains(key) && root.size() >= MAX_PLACES) return false;
        CompoundTag t = new CompoundTag();
        t.putInt("x", p.getBlockX());
        t.putInt("y", p.getBlockY());
        t.putInt("z", p.getBlockZ());
        t.putString("dim", p.level().dimension().location().toString());
        root.put(key, t);
        p.getPersistentData().put(KEY, root);
        return true;
    }

    private static Place read(CompoundTag root, String key) {
        CompoundTag t = root.getCompound(key);
        return new Place(key, t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getString("dim"));
    }

    private static String findKey(CompoundTag root, String raw) {
        String key = cleanName(raw);
        if (key.isEmpty()) return null;
        if (root.contains(key)) return key;
        for (String k : root.getAllKeys()) {
            if (k.contains(key) || key.contains(k)) return k;
        }
        return null;
    }

    public static Place get(ServerPlayer p, String raw) {
        CompoundTag root = root(p);
        String k = findKey(root, raw);
        return k == null ? null : read(root, k);
    }

    public static boolean remove(ServerPlayer p, String raw) {
        CompoundTag root = root(p);
        String k = findKey(root, raw);
        if (k == null) return false;
        root.remove(k);
        p.getPersistentData().put(KEY, root);
        return true;
    }

    public static List<Place> list(ServerPlayer p) {
        CompoundTag root = root(p);
        List<Place> out = new ArrayList<>();
        for (String k : root.getAllKeys()) out.add(read(root, k));
        out.sort(Comparator.comparing(Place::name));
        return out;
    }
}

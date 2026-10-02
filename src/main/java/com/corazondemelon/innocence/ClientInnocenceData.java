package com.corazondemelon.innocence;

/** Copia en el cliente de la experiencia de Inocencia (solo para dibujar la barra). */
public final class ClientInnocenceData {
    public static volatile float xp = 0F;
    public static volatile long lastChange = 0L;

    private ClientInnocenceData() {}

    public static void update(float newXp) {
        if (newXp != xp) {
            lastChange = System.currentTimeMillis();
        }
        xp = newXp;
    }
}

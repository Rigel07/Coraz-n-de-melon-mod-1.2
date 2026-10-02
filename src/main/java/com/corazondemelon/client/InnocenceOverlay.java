package com.corazondemelon.client;

import com.corazondemelon.innocence.ClientInnocenceData;
import com.corazondemelon.innocence.Innocence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Barra de "Inocencia": aparece unos segundos al ganar experiencia o mientras mantienes la tecla (por defecto I). */
public final class InnocenceOverlay {
    private InnocenceOverlay() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;

        long since = System.currentTimeMillis() - ClientInnocenceData.lastChange;
        boolean show = ClientSetup.SHOW_BAR.isDown() || since < 7000L;
        if (!show) return;

        float xp = ClientInnocenceData.xp;
        int lvl = Innocence.levelForXp(xp);
        float start = Innocence.levelStart(lvl);
        float end = Innocence.levelEnd(lvl);
        float progress = lvl >= Innocence.MAX_LEVEL ? 1.0F : Math.max(0F, Math.min(1F, (xp - start) / (end - start)));

        int barW = 130;
        int barH = 8;
        int x = w / 2 - barW / 2;
        int y = 22;
        int fillW = (int) (barW * progress);
        int color = 0xFF000000 | Innocence.color(lvl);

        g.drawCenteredString(mc.font, "Inocencia · Nv " + lvl + " · " + Innocence.levelName(lvl), w / 2, y - 12, color);
        g.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xCC000000);
        g.fill(x, y, x + barW, y + barH, 0xFF3B2A3F);
        g.fill(x, y, x + fillW, y + barH, color);
        g.fill(x, y, x + fillW, y + 2, 0x66FFFFFF);

        String detail = lvl >= Innocence.MAX_LEVEL
                ? "¡Nivel máximo!"
                : String.format(java.util.Locale.ROOT, "%.1f / %.0f", xp, end);
        g.drawCenteredString(mc.font, detail, w / 2, y + barH + 3, 0xFFFFFFFF);
    }
}

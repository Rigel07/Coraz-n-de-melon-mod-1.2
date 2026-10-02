package com.corazondemelon.client;

import com.corazondemelon.MelonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Pantalla de ajustes de la IA de Mao. Se abre con la tecla K o desde Mods -> Corazón de Melon -> Config.
 * Permite pegar la clave de API (Ctrl+V) y elegir el proveedor sin tocar ningún archivo.
 */
public class MelonConfigScreen extends Screen {

    private record Preset(String name, String provider, String url, String model) {}

    private static final Preset[] PRESETS = {
            new Preset("Groq", "openai", "https://api.groq.com/openai/v1/chat/completions", "meta-llama/llama-4-scout-17b-16e-instruct"),
            new Preset("xAI (Grok)", "openai", "https://api.x.ai/v1/chat/completions", "grok-4.3"),
            new Preset("Anthropic (Claude)", "anthropic", "", "claude-haiku-4-5-20251001"),
            new Preset("Ollama (en tu PC)", "openai", "http://localhost:11434/v1/chat/completions", "llama3.1"),
            new Preset("Personalizado", null, null, null)
    };

    private final Screen parent;

    private EditBox keyBox;
    private EditBox urlBox;
    private EditBox modelBox;
    private Button presetButton;

    private int preset;
    private boolean reveal = false;
    private String currentProvider;

    private String keyText;
    private String urlText;
    private String modelText;
    private Component status = Component.empty();

    private int top;
    private int left;

    public MelonConfigScreen(Screen parent) {
        super(Component.literal("Ajustes de Mao (IA)"));
        this.parent = parent;
        this.currentProvider = MelonConfig.AI_PROVIDER.get();
        this.keyText = MelonConfig.AI_KEY.get();
        this.urlText = MelonConfig.AI_URL.get();
        this.modelText = MelonConfig.AI_MODEL.get();
        this.preset = PRESETS.length - 1;
        for (int i = 0; i < PRESETS.length; i++) {
            Preset p = PRESETS[i];
            if (p.url() != null && p.url().equals(this.urlText) && p.provider().equalsIgnoreCase(this.currentProvider)) {
                this.preset = i;
            }
        }
    }

    private Component presetLabel() {
        return Component.literal("Proveedor: " + PRESETS[preset].name());
    }

    @Override
    protected void init() {
        int w = 260;
        left = this.width / 2 - w / 2;
        top = Math.max(8, this.height / 2 - 105);

        presetButton = Button.builder(presetLabel(), b -> {
            captureValues();
            preset = (preset + 1) % PRESETS.length;
            Preset p = PRESETS[preset];
            if (p.url() != null) {
                currentProvider = p.provider();
                urlText = p.url();
                modelText = p.model();
                urlBox.setValue(urlText);
                modelBox.setValue(modelText);
            }
            b.setMessage(presetLabel());
        }).bounds(left, top + 16, w, 20).build();
        addRenderableWidget(presetButton);

        keyBox = new EditBox(this.font, left, top + 56, w - 70, 20, Component.literal("Clave de API"));
        keyBox.setMaxLength(1024);
        keyBox.setValue(keyText == null ? "" : keyText);
        keyBox.setFormatter((text, offset) ->
                FormattedCharSequence.forward(reveal ? text : "*".repeat(text.length()), Style.EMPTY));
        addRenderableWidget(keyBox);

        addRenderableWidget(Button.builder(Component.literal(reveal ? "Ocultar" : "Ver"), b -> {
            reveal = !reveal;
            b.setMessage(Component.literal(reveal ? "Ocultar" : "Ver"));
        }).bounds(left + w - 66, top + 56, 66, 20).build());

        urlBox = new EditBox(this.font, left, top + 96, w, 20, Component.literal("Dirección"));
        urlBox.setMaxLength(512);
        urlBox.setValue(urlText == null ? "" : urlText);
        addRenderableWidget(urlBox);

        modelBox = new EditBox(this.font, left, top + 136, w, 20, Component.literal("Modelo"));
        modelBox.setMaxLength(200);
        modelBox.setValue(modelText == null ? "" : modelText);
        addRenderableWidget(modelBox);

        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> save())
                .bounds(left, top + 168, 126, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                .bounds(left + 134, top + 168, 126, 20).build());

        setInitialFocus(keyBox);
    }

    private void captureValues() {
        if (keyBox != null) keyText = keyBox.getValue();
        if (urlBox != null) urlText = urlBox.getValue();
        if (modelBox != null) modelText = modelBox.getValue();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        captureValues();
        super.resize(mc, w, h);
    }

    @Override
    public void tick() {
        if (keyBox != null) keyBox.tick();
        if (urlBox != null) urlBox.tick();
        if (modelBox != null) modelBox.tick();
    }

    private static String sanitizeKey(String raw) {
        String k = raw == null ? "" : raw.trim();
        k = k.replaceFirst("(?i)^bearer\\s+", "");
        k = k.replaceAll("\\s+", "");
        k = k.replaceAll("^[\"']+|[\"']+$", "");
        return k;
    }

    private void save() {
        String key = sanitizeKey(keyBox.getValue());
        String url = urlBox.getValue().trim();
        String model = modelBox.getValue().trim();
        if (model.isEmpty()) {
            status = Component.literal("Falta el nombre del modelo.").withStyle(ChatFormatting.RED);
            return;
        }
        Preset p = PRESETS[preset];
        String provider = p.provider() != null ? p.provider() : currentProvider;
        try {
            MelonConfig.AI_KEY.set(key);
            MelonConfig.AI_URL.set(url);
            MelonConfig.AI_MODEL.set(model);
            MelonConfig.AI_PROVIDER.set(provider == null ? "openai" : provider);
            MelonConfig.AI_ENABLED.set(true);
            MelonConfig.SPEC.save();
            keyBox.setValue(key);
            status = key.isEmpty()
                    ? Component.literal("Guardado, pero la clave está vacía.").withStyle(ChatFormatting.YELLOW)
                    : Component.literal("¡Guardado! Ya puedes hablar con Mao.").withStyle(ChatFormatting.GREEN);
        } catch (Exception e) {
            status = Component.literal("No se pudo guardar: " + e.getMessage()).withStyle(ChatFormatting.RED);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, top, 0xFFFFFF);
        g.drawString(this.font, "Clave de API (pégala con Ctrl+V)", left, top + 44, 0xA0A0A0);
        g.drawString(this.font, "Dirección de la API", left, top + 84, 0xA0A0A0);
        g.drawString(this.font, "Modelo", left, top + 124, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
        if (!status.getString().isEmpty()) {
            g.drawCenteredString(this.font, status, this.width / 2, top + 194, 0xFFFFFF);
        }
        g.drawCenteredString(this.font, "Solo vale en tu mundo o LAN. En un servidor, edita config/corazondemelon-common.toml",
                this.width / 2, top + 208, 0x707070);
    }
}

package com.corazondemelon.client;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.entity.MaoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class MaoRenderer extends MobRenderer<MaoEntity, MaoModel> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(CorazonDeMelon.MOD_ID, "textures/entity/mao.png");

    public MaoRenderer(EntityRendererProvider.Context context) {
        super(context, new MaoModel(context.bakeLayer(MaoModel.LAYER)), 0.25F);
    }

    private static final ResourceLocation TEXTURE_GREEN = new ResourceLocation(CorazonDeMelon.MOD_ID, "textures/entity/mao_verde.png");
    private static final ResourceLocation TEXTURE_BLUE = new ResourceLocation(CorazonDeMelon.MOD_ID, "textures/entity/mao_azul.png");
    private static final ResourceLocation TEXTURE_PURPLE = new ResourceLocation(CorazonDeMelon.MOD_ID, "textures/entity/mao_violeta.png");

    @Override
    public ResourceLocation getTextureLocation(MaoEntity entity) {
        return switch (entity.getMaoColor()) {
            case 1 -> TEXTURE_GREEN;
            case 2 -> TEXTURE_BLUE;
            case 3 -> TEXTURE_PURPLE;
            default -> TEXTURE;
        };
    }
}

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

    @Override
    public ResourceLocation getTextureLocation(MaoEntity entity) {
        return TEXTURE;
    }
}

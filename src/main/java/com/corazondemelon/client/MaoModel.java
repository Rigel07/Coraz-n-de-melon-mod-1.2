package com.corazondemelon.client;

import com.corazondemelon.CorazonDeMelon;
import com.corazondemelon.entity.MaoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Modelo chibi de Mao (cabeza grande, alitas y aureola). Texturas 64x64. */
public class MaoModel extends EntityModel<MaoEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(CorazonDeMelon.MOD_ID, "mao"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart halo;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightWing;
    private final ModelPart leftWing;

    public MaoModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.halo = root.getChild("halo");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.rightWing = root.getChild("right_wing");
        this.leftWing = root.getChild("left_wing");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = mesh.getRoot();

        p.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, 17.0F, 0.0F));
        p.addOrReplaceChild("halo", CubeListBuilder.create().texOffs(0, 48)
                .addBox(-3.0F, -1.0F, -3.0F, 6.0F, 1.0F, 6.0F), PartPose.offset(0.0F, 8.0F, 0.0F));
        p.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-3.0F, 0.0F, -2.0F, 6.0F, 5.0F, 4.0F), PartPose.offset(0.0F, 17.0F, 0.0F));
        p.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(24, 16)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(-4.0F, 17.5F, 0.0F));
        p.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 16)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(4.0F, 17.5F, 0.0F));
        p.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(40, 16)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offset(-1.5F, 22.0F, 0.0F));
        p.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(48, 16)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offset(1.5F, 22.0F, 0.0F));
        p.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-8.0F, -2.0F, 0.0F, 8.0F, 10.0F, 1.0F), PartPose.offset(-1.0F, 18.0F, 2.0F));
        p.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(20, 32)
                .addBox(0.0F, -2.0F, 0.0F, 8.0F, 10.0F, 1.0F), PartPose.offset(1.0F, 18.0F, 2.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(MaoEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float deg = (float) Math.PI / 180F;
        this.head.yRot = netHeadYaw * deg;
        this.head.xRot = headPitch * deg;
        this.halo.yRot = ageInTicks * 0.05F;

        float flap = Mth.sin(ageInTicks * 0.9F) * 0.45F;
        this.rightWing.yRot = 0.35F + flap;
        this.leftWing.yRot = -0.35F - flap;

        float sway = Mth.sin(ageInTicks * 0.2F) * 0.08F;
        this.rightArm.zRot = 0.15F + sway;
        this.leftArm.zRot = -0.15F - sway;
        this.rightLeg.xRot = Mth.sin(ageInTicks * 0.15F) * 0.2F;
        this.leftLeg.xRot = -Mth.sin(ageInTicks * 0.15F) * 0.2F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}

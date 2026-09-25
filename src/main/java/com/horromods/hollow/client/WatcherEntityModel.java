package com.horromods.hollow.client;

import com.google.common.collect.ImmutableList;
import com.horromods.hollow.Hollow;
import com.horromods.hollow.entity.WatcherEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * A gaunt, stretched-out humanoid: thin limbs, long arms reaching past the
 * knees and a blank head with two pale eyes. Model space: ground is at
 * y=24, the top of the head reaches y=-18 (about 2.6 blocks tall).
 */
public class WatcherEntityModel extends EntityModel<WatcherEntity> {
    public static final EntityModelLayer LAYER =
            new EntityModelLayer(Identifier.of(Hollow.MOD_ID, "watcher"), "main");

    private final ModelPart head;
    private final ModelPart torso;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public WatcherEntityModel(ModelPart root) {
        this.head = root.getChild("head");
        this.torso = root.getChild("torso");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        var root = modelData.getRoot();
        root.addChild("head",
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0f, -8.0f, -4.0f, 8, 8, 8),
                ModelTransform.pivot(0.0f, -10.0f, 0.0f));
        root.addChild("torso",
                ModelPartBuilder.create().uv(0, 16).cuboid(-3.5f, -14.0f, -1.5f, 7, 14, 3),
                ModelTransform.pivot(0.0f, 4.0f, 0.0f));
        root.addChild("left_arm",
                ModelPartBuilder.create().uv(36, 16).cuboid(-1.0f, 0.0f, -1.0f, 2, 18, 2),
                ModelTransform.pivot(4.5f, -8.0f, 0.0f));
        root.addChild("right_arm",
                ModelPartBuilder.create().uv(44, 16).cuboid(-1.0f, 0.0f, -1.0f, 2, 18, 2),
                ModelTransform.pivot(-4.5f, -8.0f, 0.0f));
        root.addChild("left_leg",
                ModelPartBuilder.create().uv(20, 16).cuboid(-1.0f, -20.0f, -1.0f, 2, 20, 2),
                ModelTransform.pivot(2.0f, 24.0f, 0.0f));
        root.addChild("right_leg",
                ModelPartBuilder.create().uv(28, 16).cuboid(-1.0f, -20.0f, -1.0f, 2, 20, 2),
                ModelTransform.pivot(-2.0f, 24.0f, 0.0f));
        return new TexturedModelData(modelData, 64, 64);
    }

    @Override
    public void setAngles(WatcherEntity entity, float limbAngle, float limbDistance,
            float animationProgress, float headYaw, float headPitch) {
        float swing = MathHelper.cos(limbAngle) * limbDistance * 0.9f;
        this.leftLeg.pitch = swing;
        this.rightLeg.pitch = -swing;
        this.leftArm.pitch = -swing * 0.6f + MathHelper.sin(animationProgress * 0.06f) * 0.05f;
        this.rightArm.pitch = swing * 0.6f - MathHelper.sin(animationProgress * 0.06f) * 0.05f;

        this.head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
        this.head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
        // A faint, wrong head twitch while it idles.
        this.head.roll = MathHelper.sin(animationProgress * 0.03f) * 0.04f;
    }

    @Override
    public Iterable<ModelPart> getParts() {
        return ImmutableList.of(this.head, this.torso, this.leftArm, this.rightArm,
                this.leftLeg, this.rightLeg);
    }
}

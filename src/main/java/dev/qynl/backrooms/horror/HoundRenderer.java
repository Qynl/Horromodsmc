package dev.qynl.backrooms.horror;

import dev.qynl.backrooms.BackroomsMod;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Renders the Hound as a low, dark canine silhouette that billboards toward the camera, with two
 * faint red eyes. It is lit by the world, so in the dark you mostly see the eyes until it is close.
 */
public class HoundRenderer extends EntityRenderer<HoundEntity> {

    private static final Identifier TEXTURE = Identifier.of(BackroomsMod.MOD_ID, "textures/entity/hound.png");

    public HoundRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(HoundEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        matrices.push();
        matrices.multiply(dispatcher.getRotation());

        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));

        float w = 0.8f;
        float h = 1.0f;
        int a = 245;
        vc.vertex(m, -w, 0, 0).color(255, 255, 255, a).texture(0, 1).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, -w, h, 0).color(255, 255, 255, a).texture(0, 0).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, w, h, 0).color(255, 255, 255, a).texture(1, 0).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, w, 0, 0).color(255, 255, 255, a).texture(1, 1).light(light).normal(0.0f, 0.0f, 1.0f);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }
}

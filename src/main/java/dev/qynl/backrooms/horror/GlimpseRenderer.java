package dev.qynl.backrooms.horror;

import dev.qynl.backrooms.BackroomsMod;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Renders the glimpse as a faint, billboarded dark silhouette. It is intentionally under-lit and low
 * alpha so it only really registers in peripheral vision.
 */
public class GlimpseRenderer extends EntityRenderer<GlimpseEntity> {

    private static final Identifier TEXTURE = Identifier.of(BackroomsMod.MOD_ID, "textures/entity/glimpse.png");

    public GlimpseRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(GlimpseEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(GlimpseEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        matrices.push();
        matrices.multiply(dispatcher.getRotation());   // billboard toward the camera
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));

        float w = 0.35f;
        float h = 1.8f;
        int a = 110;
        vc.vertex(m, -w, 0, 0).color(10, 10, 12, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, -w, h, 0).color(10, 10, 12, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, w, h, 0).color(10, 10, 12, a).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, w, 0, 0).color(10, 10, 12, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0f, 0.0f, 1.0f);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }
}

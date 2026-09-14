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
 * Renders the Smiler as a billboarded, self-lit grin: two eyes and a crescent smile floating in the
 * dark. It is drawn at full brightness so the face is visible even in total blackness - which is the
 * whole horror of it.
 */
public class SmilerRenderer extends EntityRenderer<SmilerEntity> {

    private static final Identifier TEXTURE = Identifier.of(BackroomsMod.MOD_ID, "textures/entity/smiler.png");

    public SmilerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SmilerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        matrices.push();
        matrices.multiply(dispatcher.getRotation());

        float pulse = 0.92f + 0.08f * (float) Math.sin((entity.getId() % 5 + tickDelta) / 6.0);
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));

        float w = 0.7f * pulse;
        float h = 1.3f * pulse;
        float y0 = 0.3f;
        int a = 235;
        vc.vertex(m, -w, y0, 0).color(255, 255, 235, a).texture(0, 1).light(15728640).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, -w, y0 + h, 0).color(255, 255, 235, a).texture(0, 0).light(15728640).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, w, y0 + h, 0).color(255, 255, 235, a).texture(1, 0).light(15728640).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, w, y0, 0).color(255, 255, 235, a).texture(1, 1).light(15728640).normal(0.0f, 0.0f, 1.0f);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }
}

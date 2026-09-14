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
 * Renders the Listener as a tall, wrong-proportioned dark figure with two faint pinpricks of light where
 * eyes would be. It billboards toward the camera and sways slightly so it never quite looks static.
 */
public class ListenerRenderer extends EntityRenderer<ListenerEntity> {

    private static final Identifier TEXTURE = Identifier.of(BackroomsMod.MOD_ID, "textures/entity/listener.png");

    public ListenerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ListenerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        matrices.push();
        matrices.multiply(dispatcher.getRotation());

        float sway = (float) Math.sin((entity.getId() % 7 + tickDelta) / 9.0) * 0.03f;
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));

        float w = 0.5f + sway;
        float h = 2.6f;
        int a = 215;
        vc.vertex(m, -w, 0, 0).color(6, 6, 8, a).texture(0, 1).light(15728640).next();
        vc.vertex(m, -w, h, 0).color(6, 6, 8, a).texture(0, 0).light(15728640).next();
        vc.vertex(m, w, h, 0).color(6, 6, 8, a).texture(1, 0).light(15728640).next();
        vc.vertex(m, w, 0, 0).color(6, 6, 8, a).texture(1, 1).light(15728640).next();

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }
}

package dev.qynl.backrooms.hole;

import dev.qynl.backrooms.BackroomsMod;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Draws what is on the other side of the tear: an enormous, empty, pale blue-white space with no
 * horizon. Two crossed quads carry a soft vertical gradient; the pattern seed nudges the UVs and the
 * tint, and it re-seeds when the player looks away, so the view is never quite the same twice.
 */
public class RealityTearRenderer implements BlockEntityRenderer<RealityTearBlockEntity> {

    private static final Identifier VOID = Identifier.of(BackroomsMod.MOD_ID, "textures/block/void.png");

    public RealityTearRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(RealityTearBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light, int overlay) {
        long now = be.getWorld() != null ? be.getWorld().getTime() : 0;
        Random rnd = new Random(be.patternSeed(now));

        float uvShift = rnd.nextInt(64) / 512f;
        int tint = 235 + rnd.nextInt(20);
        float shimmer = 0.85f + 0.15f * (float) Math.sin((now + tickDelta) / 22.0);
        int alpha = (int) (235 * shimmer);

        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(VOID));

        quadZ(vc, m, overlay, light, uvShift, tint, alpha);
        quadX(vc, m, overlay, light, uvShift, tint, alpha);
    }

    private void quadZ(VertexConsumer vc, Matrix4f m, int overlay, int light, float uv, int tint, int alpha) {
        float a = 0.12f, b = 0.88f, y0 = 0.05f, y1 = 0.95f, c = 0.5f;
        vc.vertex(m, a, y0, c).color(tint, tint, 252, alpha).texture(uv, 1).overlay(overlay).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, a, y1, c).color(tint, tint, 252, alpha).texture(uv, 0).overlay(overlay).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, b, y1, c).color(tint, tint, 252, alpha).texture(1, 0).overlay(overlay).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, b, y0, c).color(tint, tint, 252, alpha).texture(1, 1).overlay(overlay).light(light).normal(0.0f, 0.0f, 1.0f);
    }

    private void quadX(VertexConsumer vc, Matrix4f m, int overlay, int light, float uv, int tint, int alpha) {
        float a = 0.12f, b = 0.88f, y0 = 0.05f, y1 = 0.95f, c = 0.5f;
        vc.vertex(m, c, y0, a).color(tint, tint, 252, alpha).texture(uv, 1).overlay(overlay).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, c, y1, a).color(tint, tint, 252, alpha).texture(uv, 0).overlay(overlay).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, c, y1, b).color(tint, tint, 252, alpha).texture(1, 0).overlay(overlay).light(light).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, c, y0, b).color(tint, tint, 252, alpha).texture(1, 1).overlay(overlay).light(light).normal(0.0f, 0.0f, 1.0f);
    }
}

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
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Renders the Skin-Stealer as a person-shaped billboard: a body and a head, drawn full-bright so it
 * reads as "another player" in the dark. While it stalks it looks almost normal; during the clack the
 * head rolls further and further to the left; once revealed it swaps to the raw, red-eyed thing
 * underneath.
 */
public class SkinStealerRenderer extends EntityRenderer<SkinStealerEntity> {

    private static final Identifier SKIN = Identifier.of(BackroomsMod.MOD_ID, "textures/entity/skin_stealer.png");
    private static final Identifier HUNT = Identifier.of(BackroomsMod.MOD_ID, "textures/entity/skin_stealer_hunt.png");

    public SkinStealerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(SkinStealerEntity entity) {
        return SKIN;
    }

    @Override
    public void render(SkinStealerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        boolean hunt = entity.skinState() == SkinStealerEntity.HUNT;
        Identifier tex = hunt ? HUNT : SKIN;

        matrices.push();
        matrices.multiply(dispatcher.getRotation());   // face the camera

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(tex));
        int full = 15728640;

        // Body.
        Matrix4f m = matrices.peek().getPositionMatrix();
        float bw = 0.42f;
        float bh = 1.35f;
        int r = hunt ? 120 : 226, g = hunt ? 96 : 208, b = hunt ? 92 : 186, a = 245;
        vc.vertex(m, -bw, 0, 0).color(r, g, b, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(full).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, -bw, bh, 0).color(r, g, b, a).texture(0, 0.35f).overlay(OverlayTexture.DEFAULT_UV).light(full).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, bw, bh, 0).color(r, g, b, a).texture(1, 0.35f).overlay(OverlayTexture.DEFAULT_UV).light(full).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(m, bw, 0, 0).color(r, g, b, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(full).normal(0.0f, 0.0f, 1.0f);

        // Head, rolled to the left by the clack.
        matrices.translate(0, bh, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.toRadians(entity.headTilt())));
        Matrix4f mh = matrices.peek().getPositionMatrix();
        float hw = 0.30f;
        float hh = 0.42f;
        int hr = hunt ? 200 : 232, hg = hunt ? 40 : 214, hb = hunt ? 40 : 192;
        vc.vertex(mh, -hw, 0, 0).color(hr, hg, hb, a).texture(0.25f, 0.35f).overlay(OverlayTexture.DEFAULT_UV).light(full).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(mh, -hw, hh, 0).color(hr, hg, hb, a).texture(0.25f, 0).overlay(OverlayTexture.DEFAULT_UV).light(full).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(mh, hw, hh, 0).color(hr, hg, hb, a).texture(0.75f, 0).overlay(OverlayTexture.DEFAULT_UV).light(full).normal(0.0f, 0.0f, 1.0f);
        vc.vertex(mh, hw, 0, 0).color(hr, hg, hb, a).texture(0.75f, 0.35f).overlay(OverlayTexture.DEFAULT_UV).light(full).normal(0.0f, 0.0f, 1.0f);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }
}

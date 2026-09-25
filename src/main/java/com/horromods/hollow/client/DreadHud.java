package com.horromods.hollow.client;

import com.horromods.hollow.Hollow;
import com.horromods.hollow.item.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderSystem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;

/**
 * Renders the Dread vignette — a slow, pulsing purple darkening at the edges
 * of the screen — and, while a Third Eye is held, a small Dread readout.
 * Also breathes smoke into the world at high Dread for extra dread.
 */
public final class DreadHud {
    private static final Identifier VIGNETTE =
            Identifier.of(Hollow.MOD_ID, "textures/gui/dread_vignette.png");
    private static final int DREAD_COLOR = 0xB08CFF;

    private DreadHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
        ClientTickEvents.END_CLIENT_TICK.register(DreadHud::onClientTick);
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.isCreative() || client.player.isSpectator()) {
            return;
        }
        float dread = ClientDread.get();

        if (dread > 1.0f) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 320.0) * 0.5 + 0.5);
            float alpha = Math.min(0.78f, (dread / 100.0f) * (0.55f + 0.25f * pulse));
            int width = context.getScaledWindowWidth();
            int height = context.getScaledWindowHeight();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            context.drawTexture(VIGNETTE, 0, 0, width, height, 0.0f, 0.0f, 256, 256, 256, 256);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        if (ModItems.holdsThirdEye(client.player)) {
            context.drawTextWithShadow(client.textRenderer,
                    "Dread " + (int) dread + "%", 4, 4, DREAD_COLOR);
        }
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        float dread = ClientDread.get();
        if (dread > 55.0f && client.player.age % 10 == 0 && client.world.random.nextInt(3) == 0) {
            client.world.addParticle(ParticleTypes.SMOKE,
                    client.player.getX() + (client.world.random.nextDouble() - 0.5) * 4.0,
                    client.player.getY() + client.world.random.nextDouble() * 2.0,
                    client.player.getZ() + (client.world.random.nextDouble() - 0.5) * 4.0,
                    0.0, 0.02, 0.0);
        }
    }
}

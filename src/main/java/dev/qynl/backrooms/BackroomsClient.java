package dev.qynl.backrooms;

import dev.qynl.backrooms.audio.AmbientDirector;
import dev.qynl.backrooms.hole.RealityTearRenderer;
import dev.qynl.backrooms.horror.GlimpseRenderer;
import dev.qynl.backrooms.registry.ModBlockEntities;
import dev.qynl.backrooms.registry.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class BackroomsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockEntityRendererRegistry.register(ModBlockEntities.REALITY_TEAR, RealityTearRenderer::new);
        EntityRendererRegistry.register(ModEntityTypes.GLIMPSE, GlimpseRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(AmbientDirector::onClientTick);
    }
}

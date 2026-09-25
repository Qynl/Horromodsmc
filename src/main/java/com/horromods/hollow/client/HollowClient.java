package com.horromods.hollow.client;

import com.horromods.hollow.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class HollowClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(WatcherEntityModel.LAYER,
                WatcherEntityModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.WATCHER, WatcherEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.APPARITION, ApparitionEntityRenderer::new);
    }
}

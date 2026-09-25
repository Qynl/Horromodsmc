package com.horromods.hollow.client;

import com.horromods.hollow.entity.ModEntities;
import com.horromods.hollow.network.DreadPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class HollowClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(WatcherEntityModel.LAYER,
                WatcherEntityModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.WATCHER, WatcherEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.APPARITION, ApparitionEntityRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(DreadPayload.ID,
                (payload, context) -> ClientDread.set(payload.dread()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientDread.set(0.0f));

        DreadHud.register();
    }
}

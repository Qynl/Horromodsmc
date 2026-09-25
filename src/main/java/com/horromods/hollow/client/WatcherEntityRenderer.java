package com.horromods.hollow.client;

import com.horromods.hollow.Hollow;
import com.horromods.hollow.entity.WatcherEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class WatcherEntityRenderer extends MobEntityRenderer<WatcherEntity, WatcherEntityModel> {
    public WatcherEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new WatcherEntityModel(context.getPart(WatcherEntityModel.LAYER)), 0.55f);
    }

    @Override
    public Identifier getTexture(WatcherEntity entity) {
        return Identifier.of(Hollow.MOD_ID,
                entity.isPale() ? "textures/entity/watcher_pale.png" : "textures/entity/watcher.png");
    }
}

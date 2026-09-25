package com.horromods.hollow.client;

import com.horromods.hollow.Hollow;
import com.horromods.hollow.entity.ApparitionEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the apparition with the Watcher's silhouette but a translucent,
 * smoke-grey texture so it reads as something that isn't really there.
 */
public class ApparitionEntityRenderer extends MobEntityRenderer<ApparitionEntity, WatcherEntityModel<ApparitionEntity>> {
    public ApparitionEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new WatcherEntityModel<>(context.getPart(WatcherEntityModel.LAYER)), 0.0f);
    }

    @Override
    public Identifier getTexture(ApparitionEntity entity) {
        return Identifier.of(Hollow.MOD_ID, "textures/entity/apparition.png");
    }

    @Nullable
    @Override
    protected RenderLayer getRenderLayer(ApparitionEntity entity, boolean showBody,
            boolean translucent, boolean showOutline) {
        return RenderLayer.getEntityTranslucent(this.getTexture(entity));
    }
}

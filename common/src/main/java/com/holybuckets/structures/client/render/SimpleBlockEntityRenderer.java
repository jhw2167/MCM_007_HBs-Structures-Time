package com.holybuckets.structures.client.render;

import com.holybuckets.foundation.block.entity.SimpleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;


public class SimpleBlockEntityRenderer implements BlockEntityRenderer<SimpleBlockEntity> {

    public SimpleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // You can use ctx for model loading, texture binding, etc.
    }

    @Override
    public void render(SimpleBlockEntity tileEntity, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer, int combinedLightIn, int combinedOverlayIn) {
        // Your rendering logic goes here
        // If using a baked model, you might not need to do anything here!
    }

}


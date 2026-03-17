
package com.holybuckets.structures.block.be;

import com.holybuckets.structures.Constants;
import com.holybuckets.structures.block.ModBlocks;
import net.blay09.mods.balm.api.DeferredObject;
import net.blay09.mods.balm.api.block.BalmBlockEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {
    public static DeferredObject<BlockEntityType<TemplateBlockEntity>> templateChest;

    public static void initialize(BalmBlockEntities blockEntities)
    {
        templateChest =  blockEntities
            .registerBlockEntity( id("template_chest"), TemplateBlockEntity::new,
                () -> new Block[]{ModBlocks.templateBlock} );


    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(Constants.MOD_ID, name);
    }
}

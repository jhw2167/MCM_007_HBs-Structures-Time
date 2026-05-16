package com.holybuckets.structures.mixin;

import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Exposes the template reference and getSettings from SinglePoolElement
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor {

    @Invoker("getTemplate")
    StructureTemplate invokeGetTemplate(StructureTemplateManager m);

    @Invoker("getSettings")
    StructurePlaceSettings invokeGetSettings(Rotation rotation, BoundingBox boundingBox, boolean offset);
}

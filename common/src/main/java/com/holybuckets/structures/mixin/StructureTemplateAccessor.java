package com.holybuckets.structures.mixin;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// Exposes entityInfoList for manual entity spawning from template NBT
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {

    @Accessor("entityInfoList")
    List<StructureTemplate.StructureEntityInfo> getEntityInfoList();
}
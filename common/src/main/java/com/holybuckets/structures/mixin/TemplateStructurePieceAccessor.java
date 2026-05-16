package com.holybuckets.structures.mixin;

import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the template and placeSettings from TemplateStructurePiece
@Mixin(TemplateStructurePiece.class)
public interface TemplateStructurePieceAccessor {

    @Accessor("template")
    StructureTemplate getTemplate();
}

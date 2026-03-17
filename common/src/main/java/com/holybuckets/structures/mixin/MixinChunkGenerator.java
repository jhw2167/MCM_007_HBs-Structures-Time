package com.holybuckets.structures.mixin;

import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.core.StructureConceptManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGenerator {

    private static final String CLASS_ID = "020";

    @Inject(
        method = "tryGenerateStructure",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onTryGenerateStructure(
        StructureSet.StructureSelectionEntry structureEntry,
        StructureManager structureManager,
        RegistryAccess registryAccess,
        RandomState randomState,
        StructureTemplateManager structureTemplateManager,
        long seed,
        ChunkAccess chunk,
        ChunkPos chunkPos,
        SectionPos sectionPos,
        CallbackInfo ci
    ) {
        StructureConceptManager.StructureGenerateContext ctx =
            new StructureConceptManager.StructureGenerateContext(
                structureEntry,
                structureManager,
                registryAccess,
                randomState,
                structureTemplateManager,
                seed,
                chunk,
                chunkPos,
                sectionPos,
                ci
            );

        StructureConceptManager.onTryGenerateStructure(ctx);
    }

}

package com.holybuckets.structures.mixin;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorInvoker {

    @Invoker("doFill")
    ChunkAccess invokeDoFill(Blender blender, StructureManager structureManager,
                             RandomState random, ChunkAccess chunk, int minCellY, int cellCountY);
}

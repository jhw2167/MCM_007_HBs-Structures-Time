package com.holybuckets.structures.mixin;

import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the pool element from PoolElementStructurePiece
@Mixin(PoolElementStructurePiece.class)
public interface PoolElementStructurePieceAccessor {

    @Accessor("element")
    StructurePoolElement getElement();
}

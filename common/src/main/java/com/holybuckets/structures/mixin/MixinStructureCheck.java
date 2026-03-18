package com.holybuckets.structures.mixin;

import com.holybuckets.structures.core.StructureConceptManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(StructureCheck.class)
public abstract class MixinStructureCheck {

    private static final String CLASS_ID = "021";

    @Inject(
        method = "onStructureLoad",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onStructureLoad(
        ChunkPos chunkPos,
        Map<Structure, StructureStart> structureStarts,
        CallbackInfo ci
    ) {
        StructureCheck that = (StructureCheck)(Object) this;
        Level level = that.serverLevel;
        StructureConceptManager.onStructureLoad(chunkPos, structureStarts, level  );
    }

}

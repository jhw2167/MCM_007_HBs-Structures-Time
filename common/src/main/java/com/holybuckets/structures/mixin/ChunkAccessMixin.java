package com.holybuckets.structures.mixin;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.structures.core.StructureConceptManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin {

    private static final String CLASS_ID = "022";

    @Shadow public abstract ChunkPos getPos();

    @Inject(method = "getAllStarts", at = @At("RETURN"), cancellable = true)
    private void onGetAllStarts(CallbackInfoReturnable<Map<Structure, StructureStart>> cir)
    {
        if( !StructureConceptManager.isManagedChunk(GeneralConfig.OVERWORLD, getPos())) return;
        StructureConceptManager manager = StructureConceptManager.get(GeneralConfig.OVERWORLD);
        Map<Structure, StructureStart> starts = cir.getReturnValue();
        ChunkPos chunkPos = getPos();

        Map<Structure, StructureStart> filtered = new HashMap<>();
        for (Structure structure : starts.keySet()) {
            if (manager.isStructureValidForStage(chunkPos, structure)) {
                filtered.put(structure, starts.get(structure));
            }
        }

        if (filtered != null) {
            cir.setReturnValue(Collections.unmodifiableMap(filtered));
        }
    }

    /*
    @Inject(method = "getStartForStructure", at = @At("RETURN"), cancellable = true)
    private void onGetStartForStructure(Structure structure, CallbackInfoReturnable<StructureStart> cir) {
        if( !StructureConceptManager.isManagedChunk(GeneralConfig.OVERWORLD, getPos())) return;
        StructureConceptManager manager = StructureConceptManager.get(GeneralConfig.OVERWORLD);
        if (!manager.isStructureValidForStage(getPos(), structure)) {
            cir.setReturnValue(StructureStart.INVALID_START);
        }
    }
    */

}

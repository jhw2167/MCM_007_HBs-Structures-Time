package com.holybuckets.structures.mixin;

import com.holybuckets.structures.core.StructureConceptManager;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.maps.StructureAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureManager.class)
public abstract class MixinStructureManager {

    private static final String CLASS_ID = "021";

    @Inject(
        method = "setStartForStructure",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onSetStartForStructure(
        SectionPos sectionPos,
        Structure structure,
        StructureStart structureStart,
        StructureAccess structureAccess,
        CallbackInfo ci
    ) {
        StructureConceptManager.StructureSetStartContext ctx =
            new StructureConceptManager.StructureSetStartContext(
                sectionPos,
                structure,
                structureStart,
                structureAccess,
                ci
            );

        StructureConceptManager.onSetStartForStructure(ctx);
    }

}

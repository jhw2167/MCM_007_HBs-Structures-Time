package com.holybuckets.structures.mixin;

import com.holybuckets.structures.core.StructureConceptManager;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.StructureAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureManager.class)
public class MixinStructureManager {

    @Shadow
    private LevelAccessor level;

    @Inject(method = "setStartForStructure", at = @At("HEAD"))
    private void onSetStartForStructure(
        SectionPos sectionPos,
        Structure structure,
        StructureStart structureStart,
        StructureAccess structureAccess,
        CallbackInfo ci
    ) {
        if (this.level.isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) this.level;

        StructureConceptManager.StructureSetStartContext ctx =
            new StructureConceptManager.StructureSetStartContext(
                sectionPos,
                structure,
                structureStart,
                structureAccess,
                serverLevel
            );

        StructureConceptManager.onSetStartForStructure(ctx);
    }
}

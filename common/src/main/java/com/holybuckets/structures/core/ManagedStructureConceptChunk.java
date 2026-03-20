package com.holybuckets.structures.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.foundation.modelInterface.IMangedChunkData;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.config.ModConfig;
import com.holybuckets.structures.config.model.StructureConcept;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.*;
import net. minecraft. world. level. levelgen.RandomSupport;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.holybuckets.structures.core.StructureConceptManager.StructureSetStartContext;

/**
 * Class: ManagedTimedStructureChunk
 * Description: Represents a single chunk that contains a structure that changes over time.
 */
public class ManagedStructureConceptChunk implements IMangedChunkData {

    private static final String CLASS_ID = "010";
    private static final String NBT_KEY_HEADER = "managedTimedStructureChunk";

    public static ModConfig MOD_CONFIG;
    public static GeneralConfig GENERAL_CONFIG;


    public static void registerManagedChunkData() {
        ManagedChunk.registerManagedChunkData(ManagedStructureConceptChunk.class,
            () -> new ManagedStructureConceptChunk(null));
    }

    /** Variables **/
    private ServerLevel level;
    private String id;
    private ChunkPos pos;
    private int stage;
    private StructureConcept structure;
    private StructureSetStartContext structureStartContext;
    private LevelChunk chunk;

    /** Constructors **/

    /** Default constructor - creates dummy node for deserialization */
    private ManagedStructureConceptChunk(ServerLevel level) {
        super();
        this.level = level;
        this.id = null;
        this.pos = null;
        this.stage = 0;
        this.structure = null;
    }

    /** Constructor with id for a chunk that may not be loaded yet */
    private ManagedStructureConceptChunk(ServerLevel level, ChunkPos cp) {
        this(level);
        this.id = HBUtil.ChunkUtil.getId(cp);
        this.pos = cp;
    }

    /** Full constructor with structure reference */
    public ManagedStructureConceptChunk(ServerLevel level, ChunkPos cp, StructureConcept concept) {
        this(level, cp);
        this.structure = concept;
    }

    /** Getters **/

    public String getId() {
        return id;
    }

    public ChunkPos getChunkPos() {
        return pos;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public int getstage() {
        return stage;
    }

    public ManagedChunk getParent() {
        return ManagedStructureConceptChunk.getParent(level, id);
    }

    /** Setters **/

    @Override
    public void setId(String id) {
        if (id == null) return;
        this.id = id;
        this.pos = ChunkUtil.getChunkPos(id);
    }

    @Override
    public void setLevel(LevelAccessor levelAccessor) {
        if (levelAccessor == null || levelAccessor.isClientSide()) return;
        this.level = (ServerLevel) levelAccessor;
    }


    /** IMangedChunkData Overrides **/

    @Override
    public ManagedStructureConceptChunk getStaticInstance(LevelAccessor level, String id) {
        if (id == null || level == null) return null;
        return ManagedStructureConceptChunk.getInstance(level, id);
    }

    @Override
    public boolean isInit(String subClass) {
        return subClass.equals(ManagedStructureConceptChunk.class.getName()) && this.id != null;
    }

    @Override
    public void handleChunkLoaded(ChunkLoadingEvent.Load event) {
        chunk = (LevelChunk) event.getChunk();
        level = (ServerLevel) event.getLevel();
        generateAllStructureStarts();
    }

    @Override
    public void handleChunkUnloaded(ChunkLoadingEvent.Unload event) {
        // No-op for now
    }

    //** CORE
    public boolean hasStructureInConcept(Structure s) {
        if(structure == null) return false;
        return structure.hasStructure(MOD_CONFIG.loc(s));
    }

    public boolean isStructureValidForStage(Structure s) {
    if(structure == null) return false;
        return structure.getStage(stage).is(MOD_CONFIG.loc(s));
    }


    /** Static Methods **/

    private static final ManagedStructureConceptChunk
    public static ManagedStructureConceptChunk getInstance(LevelAccessor levelAcc, String id)
    {
        if(levelAcc.isClientSide()) return null;
        ServerLevel level = (ServerLevel) levelAcc;
        ChunkPos pos = ChunkUtil.getChunkPos(id);
        ManagedStructureConceptChunk c = StructureConceptManager.getManagedChunk(level, pos);
        if(c != null) return c;

        ManagedChunk parent = getParent(level, id);
        if (parent == null) return new ManagedStructureConceptChunk(level, pos);

        c = (ManagedStructureConceptChunk) parent.getSubclass(ManagedStructureConceptChunk.class);
        if (c == null) return new ManagedStructureConceptChunk(level, pos);

        return c;
    }

    public static ManagedChunk getParent(LevelAccessor level, String id) {
        ManagedChunkUtility instance = ManagedChunkUtility.getInstance(level);
        return instance.getManagedChunk(id);
    }

    public void setStructureStartContext(StructureSetStartContext ctx, ResourceLocation sourceStructure, int stage) {
        this.structureStartContext = ctx;
        this.structure = MOD_CONFIG.getStructureConcept(sourceStructure);
        this.stage = stage;
    }

    //** CORE

    /**
     * For each stage, calls Structure::findValidGenerationPoint directly with a forced biome
     * predicate, then constructs and stores a StructureStart in the chunk's structureStarts map.
     * Goes one layer deeper than Structure::generate to bypass the outer validity guard.
     */
    public void generateAllStructureStarts()
    {
        if (level == null || structureStartContext == null || structure == null) return;

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        StructureTemplateManager templateManager = level.getServer().getStructureManager();
        RegistryAccess registryAccess = level.registryAccess();
        long seed = level.getSeed();

        for (int s = 1; s <= structure.getStages().size(); s++)
        {
            StructureConcept.StructureConceptStage conceptStage = structure.getStage(s);
            if (conceptStage == null || conceptStage.isEmpty()) continue;

            Structure mcStructure = MOD_CONFIG.structure(conceptStage.getStructureLoc());
            if (mcStructure == null) continue;

            if(mcStructure == ModConfig.EMPTY_STRUCT) {
                chunk.setStartForStructure(mcStructure, structureStartContext.structureStart);
                continue;
            }
            if(chunk.getStartForStructure(mcStructure) != null) {
                continue;
            }

            // holder -> true forces generation regardless of biome
            Structure.GenerationContext ctx = new Structure.GenerationContext(
                registryAccess, generator, generator.getBiomeSource(),
                randomState, templateManager, seed, pos, level, holder -> true
            );

            Optional<Structure.GenerationStub> stub = mcStructure.findValidGenerationPoint(ctx);
            if (stub.isEmpty()) continue;

            StructurePiecesBuilder piecesBuilder = stub.get().getPiecesBuilder();
            PiecesContainer pieces = piecesBuilder.build();
            StructureStart start = new StructureStart(mcStructure, pos, 0, pieces);

            chunk.setStartForStructure(mcStructure, start);
        }
    }

    /**
     * Places the structure for the given stage using the stored StructureStart context.
     * Mirrors the structure placement loop in ChunkGenerator::applyBiomeDecoration.
     */
    public void placeStagedStructure(int stage)
    {
        if(stage == this.stage) return;
        if (level == null || structureStartContext == null || structure == null) return;

        StructureConcept.StructureConceptStage conceptStage = structure.getStage(stage);
        if (conceptStage == null || conceptStage.isEmpty()) return;

        StructureStart structureStart = chunk.getStartForStructure(
            MOD_CONFIG.structure(conceptStage.getStructureLoc()));
        if (!structureStart.isValid()) return;

        // Replicate seed setup from applyBiomeDecoration ($$9, $$10)
        SectionPos sectionPos = SectionPos.of(pos, level.getMinSection());
        BlockPos origin = sectionPos.origin();

        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());

        // Feature seed: step ordinal ($$15) and feature index 0 ($$16)
        Structure mcStructure = structureStartContext.structure;
        random.setFeatureSeed(decorationSeed, 0, mcStructure.step().ordinal());

        // Iterate every chunk the structure's bounding box touches
        BoundingBox bbox = structureStart.getBoundingBox();
        int minCX = SectionPos.blockToSectionCoord(bbox.minX());
        int maxCX = SectionPos.blockToSectionCoord(bbox.maxX());
        int minCZ = SectionPos.blockToSectionCoord(bbox.minZ());
        int maxCZ = SectionPos.blockToSectionCoord(bbox.maxZ());

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {

                // Build 3x3 region; dz-outer/dx-inner puts (dx=0,dz=0) at index 4 = list.size()/2
                List<ChunkAccess> regionChunks = new ArrayList<>(9);
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        regionChunks.add(level.getChunk(cx + dx, cz + dz, ChunkStatus.STRUCTURE_STARTS));
                    }
                }

                WorldGenRegion region = new WorldGenRegion(level, regionChunks, ChunkStatus.FEATURES, 0);
                StructureManager regionManager = level.structureManager().forWorldGenRegion(region);

                // Writable area mirrors ChunkGenerator::getWritableArea
                ChunkPos cp = new ChunkPos(cx, cz);
                BoundingBox writableArea = new BoundingBox(
                    cp.getMinBlockX(), chunk.getMinBuildHeight() + 1, cp.getMinBlockZ(),
                    cp.getMaxBlockX(), chunk.getMaxBuildHeight() - 1, cp.getMaxBlockZ()
                );

                structureStart.placeInChunk(region, regionManager, level.getChunkSource().getGenerator(), random, writableArea, cp);
                chunk.setUnsaved(true);
            }
        }

        this.stage = stage;
    }





    /** Serialization **/

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", this.id);
        tag.putInt("stage", this.stage);

        if (structure != null) {
            tag.putString("structure", structure.getStructureConceptId());
        }

        //LoggerProject.logDebug(CLASS_ID + "001", "Serializing ManagedTimedStructureChunk: " + tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;

        this.id = tag.getString("id");
        this.pos = ChunkUtil.getChunkPos(this.id);
        this.stage = tag.getInt("stage");

        String conceptId = tag.getString("structure");
        if (conceptId != null && !conceptId.isEmpty()) {
            this.structure = MOD_CONFIG.getStructureConcept(conceptId);
        }
        StructureConceptManager.addManagedChunk(level, this);
    }

}

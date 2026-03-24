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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.PlaceCommand;
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

import java.util.*;

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


    static final ManagedStructureConceptChunk DEFAULT = new ManagedStructureConceptChunk();
    public static void registerManagedChunkData() {
        ManagedChunk.registerManagedChunkData(ManagedStructureConceptChunk.class,
            () -> new ManagedStructureConceptChunk());
    }

    /** Variables **/
    private ServerLevel level;
    private String id;
    private ChunkPos pos;
    private int stage;
    private StructureConcept structureConcept;
    private StructureSetStartContext structureStartContext;
    private LevelChunk chunk;
    private Map<Structure, StructureStart> structureStarts;

    /** Constructors **/

    /** Default constructor - creates dummy node for deserialization */
    private ManagedStructureConceptChunk() {
        super();

    }

    public ManagedStructureConceptChunk(ServerLevel level, ChunkPos cp, StructureSetStartContext ctx, int stage) {
        this();
        this.level = level;
        this.pos = cp;
        this.id = ChunkUtil.getId(cp);
        this.structureStartContext = ctx;
        ResourceLocation sourceStruct = MOD_CONFIG.loc(ctx.structure);
        this.structureConcept = MOD_CONFIG.getStructureConcept(sourceStruct);
        this.stage = stage;
        this.structureStarts = new HashMap<>();
        generateAllStructureStarts();
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
        if(this==DEFAULT) return;
        chunk = (LevelChunk) event.getChunk();
        level = (ServerLevel) event.getLevel();

        //Add all structureStarts to chunk if it doesnt have them
        for(Structure s : structureStarts.keySet()) {
            if(chunk.getStartForStructure(s) == null) {
                chunk.setStartForStructure(s, structureStarts.get(s));
            }
        }
    }

    @Override
    public void handleChunkUnloaded(ChunkLoadingEvent.Unload event) {
        if(this==DEFAULT) return;
    }

    //** CORE
    public boolean isSourceStructure(Structure s) {
        if(structureConcept == null) return false;
        if(s == null) return false;
        return structureConcept.getSourceStructure().equals(MOD_CONFIG.loc(s));
    }

    public boolean hasStructureInConcept(Structure s) {
        if(structureConcept == null) return false;
        return structureConcept.hasStructure(MOD_CONFIG.loc(s));
    }

    public boolean isStructureValidForStage(Structure s) {
    if(structureConcept == null) return false;
        return structureConcept.getStage(stage).is(MOD_CONFIG.loc(s));
    }


    /** Static Methods **/
    public static ManagedStructureConceptChunk getInstance(LevelAccessor levelAcc, String id) {
        ChunkPos cp = ChunkUtil.getChunkPos(id);
        return StructureConceptManager.get(levelAcc).getManagedChunks().getOrDefault(cp, DEFAULT);
    }

    public static void setInstance(LevelAccessor levelAcc, String id, ManagedStructureConceptChunk c) {
        ManagedChunk parent = ManagedChunkUtility.getManagedChunk(levelAcc, id);
        if (parent == null) return;
        parent.setSubclass(ManagedStructureConceptChunk.class, c);

    }

    public static ManagedChunk getParent(LevelAccessor level, String id) {
        ManagedChunkUtility instance = ManagedChunkUtility.getInstance(level);
        return instance.getManagedChunk(id);
    }

    //** CORE

    /**
     * For each stage, calls Structure::findValidGenerationPoint directly with a forced biome
     * predicate, then constructs and stores a StructureStart in the chunk's structureStarts map.
     * Goes one layer deeper than Structure::generate to bypass the outer validity guard.
     */
    public void generateAllStructureStarts()
    {
        if (level == null || structureStartContext == null || structureConcept == null) return;

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        StructureTemplateManager templateManager = level.getServer().getStructureManager();
        RegistryAccess registryAccess = level.registryAccess();
        long seed = level.getSeed();

        for (int s = 1; s <= structureConcept.getStages().size(); s++)
        {
            StructureConcept.StructureConceptStage conceptStage = structureConcept.getStage(s);
            if (conceptStage == null || conceptStage.isEmpty()) continue;

            Structure mcStructure = MOD_CONFIG.structure(conceptStage.getStructureLoc());
            if (mcStructure == null) continue;

            if(mcStructure == ModConfig.EMPTY_STRUCT) {
                structureStarts.put(mcStructure, structureStartContext.structureStart);
                continue;
            }
            if(structureStarts.get(mcStructure) != null) {
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

            structureStarts.put(mcStructure, start);
        }

        setInstance(level, id, this);
    }

    /**
     * Places the structure for the given stage using the stored StructureStart context.
     * Mirrors the structure placement loop in ChunkGenerator::applyBiomeDecoration.
     */
    public void placeStagedStructure(int stage)
    {
        if(stage < 1 || stage == this.stage) {this.stage = stage; return;}
        if (level == null || structureStartContext == null || structureConcept == null) return;
        if(!ManagedChunkUtility.isChunkFullyLoaded(level, id)) return;
        if(chunk==null)
            chunk = getParent().getCachedLevelChunk();
        if(!(chunk instanceof LevelChunk))
            return;

        StructureConcept.StructureConceptStage conceptStage = structureConcept.getStage(stage);
        if (conceptStage == null || conceptStage.isEmpty()) return;

        Structure structure = MOD_CONFIG.structure(conceptStage.getStructureLoc());
        StructureStart structureStart = chunk.getStartForStructure(structure);
        if(structureStart == null) return;
        if (!structureStart.isValid()) return;
        this.stage = stage;

        // Replicate seed setup from applyBiomeDecoration ($$9, $$10)
        BoundingBox box = structureStart.getBoundingBox();
        ChunkPos minPos = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos maxPos = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));

        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), box.minX(), box.minZ());
        random.setFeatureSeed(decorationSeed, 0, structure.step().ordinal());

        // Pass level directly so setBlock routes through ServerLevel → client notifications are sent
    ChunkPos.rangeClosed(minPos, maxPos).forEach(chunkPos -> {
        structureStart.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), random,
        new BoundingBox(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), level.getMaxBuildHeight(), chunkPos.getMaxBlockZ()), chunkPos);
    });



     /*
        try {
            PlaceCommand.placeStructure( GENERAL_CONFIG.getServer().createCommandSourceStack(), ref,
                structureStart.getChunkPos().getWorldPosition());
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "002", "Error placing structure: " + e.getMessage());
        }
        */


        chunk.setUnsaved(true);
    }



    /** Serialization **/

    @Override
    public CompoundTag serializeNBT() {
        if(this==DEFAULT) return new CompoundTag();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", this.id);
        tag.putInt("stage", this.stage);

        if (structureConcept != null) {
            tag.putString("structure", structureConcept.getStructureConceptId());
        }

        //LoggerProject.logDebug(CLASS_ID + "001", "Serializing ManagedTimedStructureChunk: " + tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if(this==DEFAULT) return;
        if (tag == null || tag.isEmpty()){
            return;
        }

        this.id = tag.getString("id");
        this.pos = ChunkUtil.getChunkPos(this.id);
        this.stage = tag.getInt("stage");

        String conceptId = tag.getString("structure");
        if (conceptId != null && !conceptId.isEmpty()) {
            this.structureConcept = MOD_CONFIG.getStructureConcept(conceptId);
        }
        StructureConceptManager.addManagedChunk(level, this);
    }

    public StructureConcept getStructureConcept() {
        return structureConcept;
    }
}

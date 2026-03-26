package com.holybuckets.structures.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.foundation.modelInterface.IMangedChunkData;
import com.holybuckets.structures.config.ModConfig;
import com.holybuckets.structures.config.model.StructureConcept;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.*;
import net. minecraft. world. level. levelgen.RandomSupport;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
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
import java.util.stream.Collectors;

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
    private boolean pendingUpgrade;

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
    private ProtoChunk protoChunk;
    private Map<Structure, StructureStart> structureStarts;
    private Structure currentStructure;
    private StructureStart currentStructureStart;
    private BoundingBox currentBox;
    private BoundingBox previousbox;

    private Set<ChunkPos> oldStructureArea = new HashSet<>(); //ChunkPos used by old area
    private List<ChunkPos> newStructureArea = new ArrayList<>(); //ChunkPos that will be utilized by new structure
    private Set<ChunkPos> chunksCompleteUpgrade = new HashSet<>(); //ChunkPos that need to be regenerated before structure can be placed




    /** Constructors **/

    /** Default constructor - creates dummy node for deserialization */
    private ManagedStructureConceptChunk() {
        super();
        this.structureStarts = new HashMap<>();
        this.oldStructureArea = new HashSet<>();
        this.newStructureArea = new ArrayList<>();
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
        generateAllStructureStarts();
    }

    //** Events **/
    public void onServerTick(ServerTickEvent event) {
        handleStructureUpgradeOnTick();
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

        //add all structure starts to chunks
        if(structureStarts != null && chunk != null) {
            for(Structure s : structureStarts.keySet()) {
                if(chunk.getAllStarts().containsKey(s)) continue;
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
    public void triggerStructureUpgrade(int stage)
    {
        //1. Check if Chunk is available
        if(stage < 1 || stage == this.stage) {this.stage = stage; return;}if (level == null || structureConcept == null) return;
        if(chunk==null) chunk = getParent().getCachedLevelChunk();
        if(!(chunk instanceof LevelChunk)) return;

        //2. Check if Structure is available
        StructureConcept.StructureConceptStage conceptStage = structureConcept.getStage(stage);
        if (conceptStage == null || conceptStage.isEmpty()) {
            this.stage = stage;
            return;
        }

        ResourceLocation strLoc = conceptStage.getStructureLoc();
        if(MOD_CONFIG.isSkipStructure(strLoc)) {
            this.stage = stage;
            return;
        }

        //Empty structures are processed to clear the land as well
        currentStructure = MOD_CONFIG.structure(conceptStage.getStructureLoc());
        currentStructureStart = chunk.getStartForStructure(currentStructure);

        if(currentStructureStart == null) return;
        if (!currentStructureStart.isValid()) return;


        //3. Obtain all old chunksPos for clearing
        ResourceLocation prevStructLoc = structureConcept.getStage(this.stage).getStructureLoc();
        oldStructureArea.clear();
        newStructureArea.clear();
        if(!MOD_CONFIG.isEmptyStructure(prevStructLoc))
        {
            Structure prevStructure = MOD_CONFIG.structure(prevStructLoc);
            StructureStart prevStructureStart = chunk.getStartForStructure(prevStructure);
            BoundingBox prevBox = prevStructureStart.getBoundingBox();
            ChunkPos prevMinPos = new ChunkPos(SectionPos.blockToSectionCoord(prevBox.minX()), SectionPos.blockToSectionCoord(prevBox.minZ()));
            ChunkPos prevMaxPos = new ChunkPos(SectionPos.blockToSectionCoord(prevBox.maxX()), SectionPos.blockToSectionCoord(prevBox.maxZ()));
            ChunkPos.rangeClosed(prevMinPos, prevMaxPos).forEach(oldStructureArea::add);
        }


        //4. Check if all chunks are available for chunk regeneration
        BoundingBox box = currentStructureStart.getBoundingBox();
        ChunkPos minPos = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos maxPos = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        previousbox = currentBox;
        currentBox = box;

        ChunkPos.rangeClosed(minPos, maxPos).forEach(newStructureArea::add);

        chunksCompleteUpgrade.clear();
        this.stage = stage;
        this.pendingUpgrade = true;

    }

    private void handleStructureUpgradeOnTick()
    {
        if(!pendingUpgrade) return;

        //1. Process newStructureArea for placing new structure
        for(ChunkPos newPos : newStructureArea)
        {
            if(chunksCompleteUpgrade.contains(newPos)) continue;
            boolean res = regenerateTerrain(newPos, false);
            if(res) {
                if( generateStructureInChunk(newPos) )
                    chunksCompleteUpgrade.add(newPos);
            }
            return; //one chunk at a time
        }

        //2. Process oldStructureArea for clearing
        for(ChunkPos oldPos : oldStructureArea) {
            if(chunksCompleteUpgrade.contains(oldPos)) continue;
            boolean res = regenerateTerrain(oldPos, true);
            if(res) chunksCompleteUpgrade.add(oldPos);
            return; //one chunk at a time
        }

        pendingUpgrade = false;

    }

    private boolean regenerateTerrain(ChunkPos pos, boolean applyDecoration)
    {
        if(previousbox == null) return true; //no terrain to regenerate
        ManagedChunkUtility util = ManagedChunkUtility.getInstance(level);
        if(util==null) return false;
        if(!util.isChunkFullyLoaded(pos)) return false;

        ProtoChunk proto = ChunkRegenerator.createProtoChunk(level, pos);

        BoundingBox structureRange = new BoundingBox(
            pos.getMinBlockX(), previousbox.minY(), pos.getMinBlockZ(),
            pos.getMaxBlockX(), previousbox.maxY(), pos.getMaxBlockZ()
        );

        List<String> localChunks = HBUtil.ChunkUtil.getLocalChunkIds(pos, 8);
        boolean allLoaded = localChunks.stream().allMatch(util::isChunkFullyLoaded);
        if(!allLoaded) return false;
        List<ChunkAccess> chunks = localChunks.stream()
            .map(id -> util.getManagedChunk(id).getCachedLevelChunk())
            .collect(Collectors.toList());

        return ChunkRegenerator.resetTerrain(proto, level, pos, structureRange, chunks, applyDecoration);
    }

    private boolean generateStructureInChunk(ChunkPos pos)
    {
        if(currentStructureStart == null) return false;
        if(chunk==null) chunk = getParent().getCachedLevelChunk();
        if(!(chunk instanceof LevelChunk)) return false;

        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), currentBox.minX(), currentBox.minZ());
        random.setFeatureSeed(decorationSeed, 0, currentStructure.step().ordinal());

        currentStructureStart.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), random,
            currentBox, pos);
        return true;
    }

    /*

        ChunkRegenerator.regenerateChunk(level, pos);

        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), box.minX(), box.minZ());
        random.setFeatureSeed(decorationSeed, 0, structure.step().ordinal());

        // Pass level directly so setBlock routes through ServerLevel → client notifications are sent
        ChunkPos.rangeClosed(minPos, maxPos).forEach(chunkPos -> {
            structureStart.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), random,
                new BoundingBox(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), level.getMaxBuildHeight(), chunkPos.getMaxBlockZ()), chunkPos);
        });

     */


    //Applies decoration step to the protochunk which also regenerates the structure -- need to hold onto the protochunk to use this
    private void applyChunkRedecoration(StructureStart structureStart) {
        if (level == null || structureStart == null) return;
        if(chunk==null)
            chunk = getParent().getCachedLevelChunk();
        if(!(chunk instanceof LevelChunk))
            return;

        /*
        generator.applyBiomeDecoration(region, protoChunk, level.structureManager().forWorldGenRegion(region));
        protoChunk.setStatus(ChunkStatus.FEATURES);
        */
    }



    /** Serialization **/

    @Override
    public CompoundTag serializeNBT() {
        if(this==DEFAULT || structureConcept==null) return new CompoundTag();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", this.id);

        if(this.pendingUpgrade) stage--;
        tag.putInt("stage", this.stage);

        tag.putString("structure", structureConcept.getStructureConceptId());

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

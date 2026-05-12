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
import com.holybuckets.structures.mixin.ChunkAccessAccessor;
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
    private LevelChunk chunk;
    private ProtoChunk protoChunk;
    private Map<Structure, StructureStart> structureStarts;
    private Structure currentStructure;
    private StructureStart currentStructureStart;
    private BoundingBox currentBox;
    private BoundingBox previousbox;

    private Set<ChunkPos> oldStructureArea = new HashSet<>(); //ChunkPos used by old area
    private List<ChunkPos> newStructureArea = new ArrayList<>(); //ChunkPos that will be utilized by new structure
    private Set<ChunkPos> chunksCompletedRefresh = new HashSet<>(); //ChunkPos succesfully refreshed terrain
    private Set<ChunkPos> chunksCompletedUpgrade = new HashSet<>(); //ChunkPos that successfully regenerated structure




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
        ResourceLocation sourceStruct = MOD_CONFIG.loc(ctx.structure);
        this.structureConcept = MOD_CONFIG.getStructureConcept(sourceStruct);
        this.stage = stage;
        generateAllStructureStarts(ctx.structureStart);
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
        if(structureStarts != null && chunk != null)
        {
            if(structureStarts.isEmpty() && !chunk.getAllStarts().isEmpty())
                structureStarts.putAll(chunk.getAllStarts());
            else if(chunk.getAllStarts().isEmpty() && !structureStarts.isEmpty())
                ((ChunkAccessAccessor) chunk).getRealStructureStarts().putAll(structureStarts);
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

    public StructureConcept getStructureConcept() {
        return structureConcept;
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
    public void generateAllStructureStarts(StructureStart sStart)
    {
        if (level == null || structureConcept == null) return;
        if(sStart==null) {
            Structure originalChunkStruct = MOD_CONFIG.structure(structureConcept.getSourceStructure());
            sStart = chunk.getAllStarts().get(originalChunkStruct);
        }
        structureStarts.put(MOD_CONFIG.structure(structureConcept.getSourceStructure()), sStart);

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
                structureStarts.put(mcStructure, sStart);
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
        if(stage < 1 || stage == this.stage) {this.stage = stage; return;}
        if(level == null || structureConcept == null || getParent()==null) return;
        if(chunk==null) chunk = getParent().getCachedLevelChunk();
        if(!(chunk instanceof LevelChunk)) return;

        //2. Check if Structure is available
        StructureConcept.StructureConceptStage conceptStage = structureConcept.getStage(stage);
        if (conceptStage == null) {
            this.stage = stage;
            return;
        }

        ResourceLocation strLoc = conceptStage.getStructureLoc();
        if(MOD_CONFIG.isSkipStructure(strLoc)) {
            this.stage = stage;
            return;
        }

        if(previousbox==null && MOD_CONFIG.isEmptyStructure(strLoc)) {
            this.stage = stage;
            return;
        }

        //Empty structures are processed to clear the land as well
        currentStructure = MOD_CONFIG.structure(conceptStage.getStructureLoc());
        currentStructureStart = chunk.getStartForStructure(currentStructure);
        if(currentStructureStart == null || !currentStructureStart.isValid()) {
            generateAllStructureStarts(null);
        }

        //3. Obtain all old chunksPos for clearing
        ResourceLocation prevStructLoc = structureConcept.getStage(this.stage).getStructureLoc();
        oldStructureArea.clear();
        newStructureArea.clear();
        if(!MOD_CONFIG.isEmptyStructure(prevStructLoc))
        {
            Structure prevStructure = MOD_CONFIG.structure(prevStructLoc);
            StructureStart prevStructureStart = chunk.getStartForStructure(prevStructure);
            BoundingBox prevBox = prevStructureStart.getBoundingBox();
            //copy the box but apply level.getMaxBuildHeight() as maxY
            prevBox = new BoundingBox(prevBox.minX(), prevBox.minY(), prevBox.minZ(),
                 prevBox.maxX(), level.getMaxBuildHeight(), prevBox.maxZ());

            ChunkPos prevMinPos = new ChunkPos(SectionPos.blockToSectionCoord(prevBox.minX()), SectionPos.blockToSectionCoord(prevBox.minZ()));
            ChunkPos prevMaxPos = new ChunkPos(SectionPos.blockToSectionCoord(prevBox.maxX()), SectionPos.blockToSectionCoord(prevBox.maxZ()));
            ChunkPos.rangeClosed(prevMinPos, prevMaxPos).forEach(oldStructureArea::add);
        }


        //4. Check if all chunks are available for chunk regeneration

        BoundingBox box  = previousbox;
        {
            BoundingBox structBox = currentStructureStart.getBoundingBox();
            if(box==null)
                box = structBox;
            else
                box = new BoundingBox(
                    Math.min(box.minX(), structBox.minX()), structBox.minY(), Math.min(box.minZ(), structBox.minZ()),
                    Math.max(box.maxX(), structBox.maxX()), structBox.maxY(), Math.max(box.maxZ(), structBox.maxZ())
                );
        }
        ChunkPos minPos = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos maxPos = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        previousbox = currentBox;
        currentBox = box;

        ChunkPos.rangeClosed(minPos, maxPos).forEach(newStructureArea::add);

        chunksCompletedRefresh.clear();
        chunksCompletedUpgrade.clear();
        this.stage = stage;
        this.pendingUpgrade = true;

    }

    //here
    private void handleStructureUpgradeOnTick()
    {
        if(!pendingUpgrade) return;

        // Phase 1: Regenerate base terrain for all affected chunks
        Set<ChunkPos> allAffectedChunks = new LinkedHashSet<>();
        allAffectedChunks.addAll(oldStructureArea);
        allAffectedChunks.addAll(newStructureArea);

        for (ChunkPos cp : allAffectedChunks) {
            if (chunksCompletedRefresh.contains(cp)) continue;
            boolean res = regenerateTerrain(cp);
            if (res) chunksCompletedRefresh.add(cp);
            return; // one chunk at a time
        }

        // Phase 2: Decorate + structure placement via applyBiomeDecoration
        applyDecoration(chunksCompletedRefresh.stream().toList());

        //3. Copy Data
        for(ChunkPos cp : chunksCompletedRefresh) {
            if (chunksCompletedUpgrade.contains(cp)) continue;
            int yMin = currentBox.minY();
            if(previousbox != null && oldStructureArea.contains(cp)) {
                yMin = Math.min(currentBox.minY(), previousbox.minY());
            }
            BoundingBox structureRange = new BoundingBox(
                cp.getMinBlockX(), yMin, cp.getMinBlockZ(),
                cp.getMaxBlockX(), currentBox.maxY(), cp.getMaxBlockZ()
            );
            ChunkRegenerator.copyChunk(level, cp, structureRange);
        }

        ChunkRegenerator.clearCache(allAffectedChunks);
        pendingUpgrade = false;
    }

    private static final int TERN_RANGE_ADJ_BLOCKS = 16; //expand rang by 16 on each side when apply decoration, to fill in trees and such
    private boolean regenerateTerrain(ChunkPos pos)
    {
        ManagedChunkUtility util = ManagedChunkUtility.getInstance(level);
        if(util==null) return false;
        if(!util.isChunkFullyLoaded(pos)) return false;

        ProtoChunk proto = ChunkRegenerator.createProtoChunk(level, pos);


        List<String> localChunks = HBUtil.ChunkUtil.getLocalChunkIds(pos, 8);
        boolean allLoaded = localChunks.stream().allMatch( id -> {
            return  util.isLoaded(id) && util.isChunkFullyLoaded(id) &&
                util.getManagedChunk(id).getCachedLevelChunk() instanceof LevelChunk;
        });
        if(!allLoaded) return false;
        List<ChunkAccess> chunks = localChunks.stream()
            .map(id -> util.getManagedChunk(id).getCachedLevelChunk())
            .collect(Collectors.toList());

        boolean res = ChunkRegenerator.resetTerrain(proto, level, pos, chunks);

        return res;
    }

    private void applyDecoration(List<ChunkPos> allAffectedChunks)
    {
        // Phase 2: Decorate all affected chunks in one batch
        List<ChunkPos> allAffected = new ArrayList<>(allAffectedChunks);

        ChunkRegenerator.applyDecorationBatch(level, allAffected, structureStarts);


        //ChunkRegenerator.applyDecorationAndCopy(level, pos, structureRange, structureStarts);
    }

    private boolean generateStructureInChunk(ChunkPos pos)
    {
        if(currentStructureStart == null) return false;
        if(chunk==null) chunk = getParent().getCachedLevelChunk();
        if(!(chunk instanceof LevelChunk)) return false;

        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), currentBox.minX(), currentBox.minZ());
        random.setFeatureSeed(decorationSeed, 0, currentStructure.step().ordinal());

        //define new bounding box just over the range of the specified chunk
        BoundingBox box = new BoundingBox(
            pos.getMinBlockX(), currentBox.minY(), pos.getMinBlockZ(),
            pos.getMaxBlockX(), currentBox.maxY(), pos.getMaxBlockZ()
        );

        ChunkPos.rangeClosed(pos, pos).forEach(chunkPos -> {
            currentStructureStart.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), random,
                box, pos);
        });

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

        if(this.pendingUpgrade) {
            tag.putInt("stage", this.stage-1);
        } else {
            tag.putInt("stage", this.stage);
        }


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

}

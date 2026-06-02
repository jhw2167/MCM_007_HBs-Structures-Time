package com.holybuckets.structures.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.console.Messager;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.foundation.modelInterface.IMangedChunkData;
import com.holybuckets.foundation.structure.StructureManager;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.StructuresOverTimeMain;
import com.holybuckets.structures.config.ModConfig;
import com.holybuckets.structures.config.model.StructureConcept;
import com.holybuckets.structures.mixin.SinglePoolElementAccessor;
import com.holybuckets.structures.mixin.StructureTemplateAccessor;
import com.holybuckets.structures.mixin.TemplateStructurePieceAccessor;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.holybuckets.structures.core.StructureConceptManager.StructureSetStartContext;

import static com.holybuckets.foundation.HBUtil.*;

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
    static final String DEFAULT_ID = "DEFAULT";
    private boolean pendingUpgrade;

    public static void registerManagedChunkData() {
        ManagedChunk.registerManagedChunkData(ManagedStructureConceptChunk.class,
            () -> new ManagedStructureConceptChunk());
    }

    /**
     * Variables
     **/
    private ServerLevel level;
    private String id;
    private ChunkPos pos;
    private int stage;
    private StructureConcept structureConcept;
    private LevelChunk chunk;
    private ProtoChunk protoChunk;
    private Map<Structure, StructureStart> structureStarts;
    private Map<ResourceLocation, BoundingBox> structureBoxes;
    private Structure currentStructure;
    private StructureStart currentStructureStart;
    private BoundingBox currentBox;
    private BoundingBox previousbox;

    private Set<ChunkPos> oldStructureArea; //ChunkPos used by old area
    private List<ChunkPos> newStructureArea; //ChunkPos that will be utilized by new structure
    private List<ChunkPos> affectedUpgradeChunks; //all chunks to be refreshed in the next upgrade
    private Set<ChunkPos> chunksCompletedRefresh; //ChunkPos succesfully refreshed terrain
    private Set<ChunkPos> chunksCompletedUpgrade; //ChunkPos that successfully regenerated structure

    private List<BlockPos> lootPositions; //to be used for copying loot contents
    private List<Entity> entities; //to be used for copying mobs

    private int countTotalWakeups;
    private int upgradeRejectedStatus;

    /** Constructors **/

    /**
     * Default constructor - creates dummy node for deserialization
     */
    private ManagedStructureConceptChunk() {
        super();
        this.id=DEFAULT_ID;
        this.structureStarts = new HashMap<>();
        this.oldStructureArea = new HashSet<>();
        this.newStructureArea = new ArrayList<>();

        this.affectedUpgradeChunks = new ArrayList<>();
        this.chunksCompletedRefresh = new HashSet<>();
        this.chunksCompletedUpgrade = new HashSet<>();

        this.structureBoxes = new HashMap<>();
        this.lootPositions = new ArrayList<>();
        this.entities = new ArrayList<>();

        this.upgradeRejectedStatus = -1;
    }

    public ManagedStructureConceptChunk(ServerLevel level, ChunkPos cp, StructureSetStartContext ctx, int stage) {
        this();
        this.level = level;
        this.pos = cp;
        this.id = ChunkUtil.getId(cp);
        this.chunk = ManagedChunkUtility.getInstance(level).getChunk(id,false);
        ResourceLocation sourceStruct = MOD_CONFIG.loc(ctx.structure);
        this.structureConcept = MOD_CONFIG.getStructureConcept(sourceStruct);
        this.stage = stage;
        generateAllStructureStarts(ctx.structureStart);

    }

    //** Events **/
    public void onServerTick(ServerTickEvent event) {
        if(chunk==null) this.chunk = getChunk();
        handleStructureUpgradeOnTick();
    }


    /**
     * Getters
     **/

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

    /**
     * Setters
     **/

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


    /**
     * IMangedChunkData Overrides
     **/

    //resolveStaticinstance, resolveData, staticInstance
    @Override
    public ManagedStructureConceptChunk resolveSubData(LevelAccessor level, String id, @Nullable IMangedChunkData data)
    {
        if (id == null || level == null) return null;
        ManagedStructureConceptChunk subData = (ManagedStructureConceptChunk) data;
        if(subData != null && subData.getStructureConcept()!=null) {
             //good, always prioritize serialized data
        } else {
            subData = ManagedStructureConceptChunk.getInstance(level, id);
        }
        if(subData!= null)
            StructureConceptManager.addManagedChunk((ServerLevel) level, subData);

        return subData;
    }

    @Override
    public boolean isInit(String subClass) {
        return subClass.equals(ManagedStructureConceptChunk.class.getName()) && this.id != null;
    }

    @Override
    public void handleChunkLoaded(ChunkLoadingEvent.Load event) {
        if (this.id.equals(DEFAULT_ID)) return;
        chunk = (LevelChunk) event.getChunk();
        level = (ServerLevel) event.getLevel();
    }

    @Override
    public void handleChunkUnloaded(ChunkLoadingEvent.Unload event) {
        if (this.id.equals(DEFAULT_ID)) return;
    }

    //** CORE
    public boolean isSourceStructure(Structure s) {
        if (structureConcept == null) return false;
        if (s == null) return false;
        return structureConcept.getSourceStructure().equals(MOD_CONFIG.loc(s));
    }

    public boolean hasStructureInConcept(Structure s) {
        if (structureConcept == null) return false;
        return structureConcept.hasStructure(MOD_CONFIG.loc(s));
    }

    public boolean isStructureValidForStage(Structure s) {
        if (structureConcept == null) return false;
        return structureConcept.getStage(stage).is(MOD_CONFIG.loc(s));
    }

    public StructureConcept getStructureConcept() {
        return structureConcept;
    }


    /**
     * Static Methods
     **/
    public static ManagedStructureConceptChunk getInstance(LevelAccessor levelAcc, String id) {
        ChunkPos cp = ChunkUtil.getChunkPos(id);
        if(StructureConceptManager.get(levelAcc) == null) return null;
        return StructureConceptManager.get(levelAcc).getManagedChunks().get(cp);
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
    public void generateAllStructureStarts(StructureStart sStart) {
        if (level == null || structureConcept == null) return;
        if (sStart == null) {
            Structure originalChunkStruct = MOD_CONFIG.structure(structureConcept.getSourceStructure());
            sStart = chunk.getAllStarts().get(originalChunkStruct);
        }
        structureStarts.put(MOD_CONFIG.structure(structureConcept.getSourceStructure()), sStart);

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        StructureTemplateManager templateManager = level.getServer().getStructureManager();
        RegistryAccess registryAccess = level.registryAccess();
        long seed = level.getSeed();
        // holder -> true forces generation regardless of biome
        Structure.GenerationContext ctx = new Structure.GenerationContext(
            registryAccess, generator, generator.getBiomeSource(),
            randomState, templateManager, seed, pos, level, holder -> true
        );

        for (int s = 0; s <= structureConcept.getStages().size(); s++)
        {
            StructureConcept.StructureConceptStage conceptStage = structureConcept.getStage(s);
            if (conceptStage == null || conceptStage.isEmpty()) continue;

            Structure mcStructure = MOD_CONFIG.structure(conceptStage.getStructureLoc());
            if (mcStructure == null) continue;

            if (mcStructure == ModConfig.EMPTY_STRUCT) {
                structureStarts.put(mcStructure, sStart);
                continue;
            }
            if (structureStarts.get(mcStructure) != null) {
                continue;
            }

            Optional<Structure.GenerationStub> stub = mcStructure.findValidGenerationPoint(ctx);
            if (stub.isEmpty()) continue;

            StructurePiecesBuilder piecesBuilder = stub.get().getPiecesBuilder();
            PiecesContainer pieces = piecesBuilder.build();
            StructureStart start = new StructureStart(mcStructure, pos, 0, pieces);

            structureStarts.put(mcStructure, start);
            if(s== stage) {
                currentStructure = mcStructure;
                currentStructureStart = start;
            }
        }

        if (structureStarts.isEmpty()) return;
        for (StructureConcept.StructureConceptStage stage : structureConcept.getStages()) {
            Structure s = MOD_CONFIG.structure(stage.getStructureLoc());
            StructureStart start = structureStarts.getOrDefault(s, sStart);
            BoundingBox bb = start.getBoundingBox();
            structureBoxes.put(stage.getStructureLoc(), bb);
        }

    }

    /**
     * If for any reason a structure upgrade was rejected,
     * reset the rejection status and allow the structure to attempt to upgrade again.
     */
    public void resetUpgradeRejectedStatus() {
        this.upgradeRejectedStatus = -1;
    }

    /** Enqueues a structure upgrade for next tick, giving a chance for a warning or to cancel **/
    private int nextStageQueued = -1;
    public void queueStructureUpgrade(int nextStage)
    {
        if(nextStage== stage) return;
        if(upgradeRejectedStatus>-1) return;
        if( testRejectStructureUpgrade() ) return;

        if(nextStage == nextStageQueued) {
            triggerStructureUpgrade(nextStage, false);
        } else {
            warnPlayersOfStructureUpgrade();
        }
        this.nextStageQueued = nextStage;
    }

    /**
     * Places the structure for the given stage using the stored StructureStart context.
     * Mirrors the structure placement loop in ChunkGenerator::applyBiomeDecoration.
     */
    public void triggerStructureUpgrade(int newStage, boolean force)
    {
        if(force) {
            this.upgradeRejectedStatus = -1;
        } else if(testRejectStructureUpgrade()) {
            return;
        }

        //1. Check if Chunk is available
        if (newStage < 1 || newStage == this.stage) {
            this.stage = newStage;
            return;
        }
        if (level == null || structureConcept == null || getParent() == null) return;
        if (chunk == null) chunk = getParent().getCachedLevelChunk();
        if (!(chunk instanceof LevelChunk)) return;

        //2. Check if Structure is available
        StructureConcept.StructureConceptStage newConcept = structureConcept.getStage(newStage);
        if (newConcept == null) {
            this.stage = newStage;
            return;
        }

        ResourceLocation strLoc = newConcept.getStructureLoc();
        if (MOD_CONFIG.isSkipStructure(strLoc)) {
            this.stage = newStage;
            return;
        }

        if (previousbox == null && MOD_CONFIG.isEmptyStructure(strLoc)) {
            this.stage = newStage;
            return;
        }

        //Empty structures are processed to clear the land as well
        currentStructure = MOD_CONFIG.structure(newConcept.getStructureLoc());


        //3. Obtain all old chunksPos for clearing
        ResourceLocation prevStructLoc = structureConcept.getStage(this.stage).getStructureLoc();
        oldStructureArea.clear();
        newStructureArea.clear();
        if (!MOD_CONFIG.isEmptyStructure(prevStructLoc)) {
            Structure prevStructure = MOD_CONFIG.structure(prevStructLoc);
            StructureStart prevStructureStart = structureStarts.get(prevStructure);
            BoundingBox prevBox = prevStructureStart.getBoundingBox();
            //copy the box but apply level.getMaxBuildHeight() as maxY
            prevBox = new BoundingBox(prevBox.minX(), prevBox.minY(), prevBox.minZ(),
                prevBox.maxX(), level.getMaxBuildHeight(), prevBox.maxZ());

            ChunkPos prevMinPos = new ChunkPos(SectionPos.blockToSectionCoord(prevBox.minX()), SectionPos.blockToSectionCoord(prevBox.minZ()));
            ChunkPos prevMaxPos = new ChunkPos(SectionPos.blockToSectionCoord(prevBox.maxX()), SectionPos.blockToSectionCoord(prevBox.maxZ()));
            ChunkPos.rangeClosed(prevMinPos, prevMaxPos).forEach(oldStructureArea::add);
        }


        //4. Check if all chunks are available for chunk regeneration

        BoundingBox box = previousbox;
        {
            BoundingBox structBox = structureBoxes.get(newConcept.getStructureLoc());
            if (box == null)
                box = structBox;
            else {
                //temp overrides
                int minY = Math.max(structBox.minY() - 32, level.getMinBuildHeight());
                int maxY = Math.min(structBox.maxY() + 32, level.getMaxBuildHeight());
                box = new BoundingBox(
                    Math.min(box.minX(), structBox.minX()), minY, Math.min(box.minZ(), structBox.minZ()),
                    Math.max(box.maxX(), structBox.maxX()), maxY, Math.max(box.maxZ(), structBox.maxZ())
                );
            }

        }
        ChunkPos minPos = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos maxPos = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        previousbox = currentBox;
        currentBox = box;

        ChunkPos.rangeClosed(minPos, maxPos).forEach(newStructureArea::add);
        affectedUpgradeChunks.addAll(oldStructureArea);
        affectedUpgradeChunks.addAll(newStructureArea);

        chunksCompletedRefresh.clear();
        chunksCompletedUpgrade.clear();
        this.stage = newStage;
        this.pendingUpgrade = true;
        upgradeRejectedStatus = -1;
    }


    //here
    enum UpgradePhase {TERRAIN, DECORATE, COPY, COPY_LOOT, COPY_MOBS, DONE, RESET}

    private UpgradePhase phase = UpgradePhase.TERRAIN;
    private int chunkIndex = 0;

    private static final int CHUNKS_PER_TICK = 4;

    private void handleStructureUpgradeOnTick()
    {
        if (!pendingUpgrade) return;

        switch (phase)
        {
            case TERRAIN:
                if (chunkIndex >= affectedUpgradeChunks.size()) {
                    phase = UpgradePhase.DECORATE;
                    chunkIndex = 0;
                    break;
                }
                int processed = 0;
                while (processed < CHUNKS_PER_TICK && chunkIndex < affectedUpgradeChunks.size()) {
                    ChunkPos cp = affectedUpgradeChunks.get(chunkIndex);
                    if (!regenerateTerrain(cp)) break; // retry next tick
                    chunkIndex++;
                    processed++;
                }
                break;

            case DECORATE:
                // One batch call — only touches proto chunks in RAM
                var starts = new HashMap();
                starts.put(currentStructure, structureStarts.get(currentStructure));
                try {
                    boolean success = ChunkRegenerator.applyDecorationBatch(level, affectedUpgradeChunks,
                 starts, currentBox);
                    if (success) {
                        phase = UpgradePhase.COPY;
                        chunkIndex = 0;
                    }
                } catch (Exception e) {
                    String msg = String.format("Decoration failed for structure upgrade in chunk %s at stage %s. Will retry next tick. Error: %s",
                        id, stage, e.getMessage());
                    LoggerProject.logWarning("012013", msg);
                    phase = UpgradePhase.RESET;
                }
                // else retry next tick (chunks not loaded)
                break;

            case COPY:
                if (chunkIndex >= affectedUpgradeChunks.size()) {
                    phase = UpgradePhase.COPY_LOOT;
                    break;
                }
                ChunkPos copyPos = affectedUpgradeChunks.get(chunkIndex);
                BoundingBox area = getAreaForChunk(copyPos); // full chunk or structure box
                ChunkRegenerator.copyChunk(level, copyPos, area, lootPositions);
                chunkIndex++;
                break;

            case COPY_LOOT:
                if(!structureConcept.getStage(stage).isIncludeLoot()) {
                    phase = UpgradePhase.COPY_MOBS; break;
                }
                //iterate over lootPos. If a chest exists there in the real world (level) chunk,
                //call ChunkRegenerator.copyLoot(Blockpos pos, LevelChunk chunk)
                for(BlockPos pos : lootPositions) {
                    ChunkRegenerator.copyLoot(level, pos);
                }
                phase = UpgradePhase.COPY_MOBS;
                break;

            case COPY_MOBS:
                if(!structureConcept.getStage(stage).isIncludeEntities()) {
                    //nothing
                } else {
                    StructureStart start = structureStarts.get(currentStructure);
                    List<StructurePiece> pieces = start.getPieces();
                    for(StructurePiece piece : pieces)
                    {
                        StructureTemplate temp;
                        StructurePlaceSettings placeSettings;
                        if(piece instanceof TemplateStructurePiece tsp) {
                            temp = ((TemplateStructurePieceAccessor) piece).getTemplate();
                            placeSettings = tsp.placeSettings();
                        } else if(piece instanceof PoolElementStructurePiece pesp) {
                            if(pesp.getElement() instanceof SinglePoolElement spe) {
                                StructureTemplateManager manager = level.getServer().getStructureManager();
                                temp = ((SinglePoolElementAccessor) spe).invokeGetTemplate(manager);
                                placeSettings = ((SinglePoolElementAccessor) spe).invokeGetSettings(
                                piece.getRotation(), piece.getBoundingBox(), true );
                            }
                                else continue;
                        } else {
                            continue;
                        }
                        if(temp==null || placeSettings==null ) continue;
                        List<StructureTemplate.StructureEntityInfo> ents = ((StructureTemplateAccessor) temp).getEntityInfoList();
                        for(StructureTemplate.StructureEntityInfo info : ents) {
                            EntityType.create(info.nbt, level).ifPresent(entity -> {
                                entity.setUUID(UUID.randomUUID());
                                entity.moveTo(piece.getLocatorPosition().offset(info.blockPos), 0, 0);
                                if (entity instanceof Mob mob) {
                                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(info.blockPos), MobSpawnType.STRUCTURE, null, null);
                                }

                                level.addFreshEntityWithPassengers(entity);
                            });

                        }

                    }
                }
                phase = UpgradePhase.DONE;
                break;

            case RESET:
                ChunkRegenerator.clearCache(new HashSet<>(affectedUpgradeChunks));
                lootPositions.clear();
                entities.clear();
                upgradeRejectedStatus = -1;
                phase = UpgradePhase.TERRAIN;
                chunkIndex = 0;
            break;


            case DONE:
                ChunkRegenerator.clearCache(new HashSet<>(affectedUpgradeChunks));
                lootPositions.clear();
                entities.clear();
                pendingUpgrade = false;
                upgradeRejectedStatus = -1;
                phase = UpgradePhase.TERRAIN;
                chunkIndex = 0;
                currentStructureStart = structureStarts.get(currentStructure);
                if(currentStructure != null && currentStructureStart != null) {
                    chunk.setStartForStructure(currentStructure, currentStructureStart);
                    chunk.addReferenceForStructure(currentStructure, currentStructureStart.getChunkPos().toLong());

                    StructureManager structureManager = StructureManager.get(level);
                    if(structureManager != null)
                        structureManager.processStructureLoad(currentStructure, currentStructureStart);
                }

                break;

        }
    }

    private BoundingBox getAreaForChunk(ChunkPos cp) {
        int minY = currentBox.minY();
        int maxY = currentBox.maxY();
        if (previousbox != null) {
            minY = previousbox.minY();
            maxY = previousbox.maxY();
        }

        return new BoundingBox(
            cp.getMinBlockX(), minY, cp.getMinBlockZ(),
            cp.getMaxBlockX(), maxY, cp.getMaxBlockZ()
        );

    }

    //needs memory leak help with cache

    private static final int TERN_RANGE_ADJ_BLOCKS = 16; //expand rang by 16 on each side when apply decoration, to fill in trees and such

    private boolean regenerateTerrain(ChunkPos pos) {
        ManagedChunkUtility util = ManagedChunkUtility.getInstance(level);
        if (util == null) return false;
        if (!util.isChunkFullyLoaded(pos)) return false;

        ProtoChunk proto = ChunkRegenerator.createProtoChunk(level, pos);


        List<String> localChunks = HBUtil.ChunkUtil.getLocalChunkIds(pos, 8);
        boolean allLoaded = localChunks.stream().allMatch(id -> {
            return util.isLoaded(id) && util.isChunkFullyLoaded(id) &&
                util.getManagedChunk(id).getCachedLevelChunk() instanceof LevelChunk;
        });
        if (!allLoaded) return false;
        List<ChunkAccess> chunks = localChunks.stream()
            .map(id -> util.getManagedChunk(id).getCachedLevelChunk())
            .collect(Collectors.toList());

        boolean res = ChunkRegenerator.resetTerrain(proto, level, pos, chunks);

        return res;
    }

    public void pauseUpgrades(boolean pause) {
        if(pause)
            this.upgradeRejectedStatus = 4;
        else
            this.upgradeRejectedStatus = -1;
    }

    public boolean testRejectStructureUpgrade()
    {
        try {
            testStructureForRejection();
            return false;
        } catch (StructureUpgradeRejectionException e) {
            if(getChunk()==null) return true;

            List<ServerPlayer> nearbyPlayers = HBUtil.PlayerUtil.getAllPlayersInChunkRange(getChunk(), 34);
            nearbyPlayers.forEach(p -> Messager.getInstance().sendChat(p, e.getMessage()));
            LoggerProject.logInfo(CLASS_ID + "003", "Structure upgrade rejected for chunk " + id + ": " + e.getMessage());
            return true;
        }
    }


    /**
     * Tests if the next structure upgrade should be rejected according to
     * structure concept limitations
     * @return
     */
    private void testStructureForRejection() throws StructureUpgradeRejectionException
    {
        if (structureConcept == null) throwUpgradeRejection(0);

        if( !ManagedChunkUtility.isChunkFullyLoaded(level, id) ) {
            throwUpgradeRejection(5);
        }

        //check spawn point
        Map<String, BlockPos> spawnPoints = StructureConceptManager.getPlayerSpawnPos();

        //Check if someone is spawning there
        if (structureConcept.isStopUpgradeIfSpawnpointSet()) {
            for (String key : spawnPoints.keySet())
            {
                String dimensionId = key.split("\\|")[0];
               if( !level.equals( LevelUtil.toLevel(LevelUtil.LevelNameSpace.SERVER, dimensionId) ) )
                    continue;
               if(currentBox == null) {
                    List<ChunkPos> localchunks = HBUtil.ChunkUtil.getLocalChunkPos(chunk.getPos(), 5);
                    if(localchunks.contains(ChunkUtil.getChunkPos(spawnPoints.get(key)))) {
                        throwUpgradeRejection(1);
                    }
               } else if (currentBox.isInside(spawnPoints.get(key))) {
                    throwUpgradeRejection(1);
                }
            }
        }

        //test all BlockEntities in all Chunks in newChunkArea if they are inside currentBox and if they are chests
        if (structureConcept.getStopUpgradeOnTotalChestCount() > -1) {
            if(currentBox != null) {

                AtomicInteger chestCount = new AtomicInteger();
                for(ChunkPos cp : newStructureArea) {
                    LevelChunk c = level.getChunk(cp.x, cp.z);
                    if (c == null) continue;
                    c.getBlockEntities().forEach((pos, be) -> {
                        if (currentBox.isInside(pos) && be instanceof ChestBlockEntity) {
                            chestCount.getAndIncrement();
                        }
                    });
                }

                if (chestCount.get() > structureConcept.getStopUpgradeOnTotalChestCount()) {
                    throwUpgradeRejection(2);
                }
            }
        }

        //check days spent in structure
        final int CONFIG_WAKEUPS = structureConcept.getStopUpgradeOnDaysSpentInStructure();
        if(CONFIG_WAKEUPS > 0 && countTotalWakeups >= CONFIG_WAKEUPS) {
            throwUpgradeRejection(3);
        }

    }

        private void throwUpgradeRejection(int rejectCase) throws StructureUpgradeRejectionException
        {
        String baseMessage = getStructureDetails() + "\n\nStructure upgrade rejected: ";
        String msg="";
        switch (rejectCase)
        {
            case 1:
            upgradeRejectedStatus = 1;
              msg = baseMessage + "an existing player spawn is set in structure.";
              throw new StructureUpgradeRejectionException(msg);
            case 2:
                upgradeRejectedStatus = 2;
              int chestCount = structureConcept.getStopUpgradeOnTotalChestCount();
              msg = baseMessage + " chest count exceeded. There are more than "+ chestCount + " chests so the player may be using it as a base.";
              throw new StructureUpgradeRejectionException(msg);
            case 3:
                upgradeRejectedStatus = 3;
              msg = baseMessage + " player has spent many days in structure. And may be using it as a base.";
              throw new StructureUpgradeRejectionException(msg);

            case 4:
              upgradeRejectedStatus = 4;
              msg = baseMessage + " command disabled structure upgrade.";
              throw new StructureUpgradeRejectionException(msg);
            case 5:
                upgradeRejectedStatus = -1;
                msg = baseMessage + " chunk is not fully loaded.";
                throw new StructureUpgradeRejectionException(msg);
            default:
                upgradeRejectedStatus = 0;
                msg = baseMessage + " no structure data found.";
                throw new StructureUpgradeRejectionException(msg);
        }
    }

        private class StructureUpgradeRejectionException extends Exception {
            public StructureUpgradeRejectionException(String message) {
                super(message);
            }
        }

    private void warnPlayersOfStructureUpgrade() {
        if (structureConcept == null || getChunk()==null) return;
        if(StructuresOverTimeMain.CONFIG.enableStructureUpgradeWarning) {
            List<ServerPlayer> nearbyPlayers = HBUtil.PlayerUtil.getAllPlayersInChunkRange(getChunk(), 34);
            String strDetails = getStructureDetails();
            nearbyPlayers.forEach(p -> Messager.getInstance().sendChat(p,
                "Nearby structure will be upgraded soon, use commands to stop upgrade:\n"
                 + strDetails));
        }
    }


    public Map<? extends Structure, StructureStart> getStructureStarts() {
        return structureStarts;
    }

    /** @return only starts for current stage */
    public Map<Structure, StructureStart> getCurrentStarts() {
        Map<Structure, StructureStart> structs = new HashMap<>();
        if(currentStructure != null && structureStarts.get(currentStructure) != null)
            structs.put(currentStructure, structureStarts.get(currentStructure));
        return structs;
    }

    //Generate a JSON formatted message with the name of the StructureConcept in this chunk, its position and stage
    //getDetails, details
    public String getStructureDetails() {
        if (structureConcept == null) return "No structure concept in this chunk.";
        return String.format("Structure Concept Id: %s\nPosition: %s",
            structureConcept.getStructureConceptId(),
            BlockUtil.positionToString( pos.getWorldPosition() ));
    }

    public String getStructureAdvancedDetails()
    {
        if (structureConcept == null) return "No structure concept in this chunk.";

        String rejectMessage = "";
        if(upgradeRejectedStatus > -1) {
            try {
                throwUpgradeRejection(upgradeRejectedStatus);
            } catch (StructureUpgradeRejectionException e) {
                rejectMessage = e.getMessage();
            }
        }

        String upgradeTrigger = "N/A";
        if(stage < this.getStructureConcept().getMaxStage() )
        {
            var nextStage = structureConcept.getStage(this.stage+1);
            if(nextStage != null) upgradeTrigger = nextStage.getUpgradeStructureTrigger();
        }

        String nextStructureId = "N/A";
        if(stage < this.getStructureConcept().getMaxStage() )
        {
            var nextStage = structureConcept.getStage(this.stage+1);
            if(nextStage != null) nextStructureId = nextStage.getStructureLoc().toString();
        }


        return String.format("Structure Concept Id: %s" +
         "\nChunk: %s" +
         "\nPosition: %s" +
         "\nCurrent Stage: %d" +
          "\nNext upgrade trigger: %s" +
          "\nNext structure: %s" +
          "\nUpgrade Rejection Status: %d" +
          "\nUpgrade Rejection Reason: %s",

            structureConcept.getStructureConceptId(),
            id,
            BlockUtil.positionToString( pos.getWorldPosition() ),
            stage,
            upgradeTrigger,
            nextStructureId,
            upgradeRejectedStatus,
            rejectMessage
        );
    }

    /**
     * Keep track of the number of times a player was woken up in bounds of this structure
     */
    public void onPlayersWakeUpEvent() {
        var list = HBUtil.PlayerUtil.getAllPlayersInChunkRange(getChunk(), 3);
        if(!list.isEmpty()) countTotalWakeups++;
    }

    private LevelChunk getChunk() {
        if (chunk != null) return chunk;
        if (getParent() != null) {
            chunk = getParent().getCachedLevelChunk();
            return chunk;
        }
        return null;
    }

    /**
     * Serialization
     **/
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (this.id.equals(DEFAULT_ID) || structureConcept == null) return tag;
        tag.putString("id", this.id);

        if (this.pendingUpgrade) {
            tag.putInt("stage", this.stage - 1);
        } else {
            tag.putInt("stage", this.stage);
        }

        tag.putInt("rejectedUpgrade", this.upgradeRejectedStatus);

        tag.putString("structure", structureConcept.getStructureConceptId());

        // Serialize structure starts using vanilla StructureStart.createTag
        if (structureStarts != null && !structureStarts.isEmpty()) {
            CompoundTag startsTag = new CompoundTag();
            net.minecraft.core.Registry<Structure> registry = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            // Build the serialization context
            StructurePieceSerializationContext ctx = StructurePieceSerializationContext.fromLevel(level);
            structureStarts.forEach((structure, start) -> {
                if (start != null) {
                    ResourceLocation loc = registry.getKey(structure);
                    if (loc != null) {
                        startsTag.put(loc.toString(), start.createTag(ctx, pos));
                    }
                }
            });
            if (!startsTag.isEmpty()) {
                tag.put("structureStarts", startsTag);
            }
        }

        //LoggerProject.logDebug(CLASS_ID + "001", "Serializing ManagedTimedStructureChunk: " + tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag)
    {
        if (tag == null || tag.isEmpty()) return;

        this.id = tag.getString("id");
        this.pos = ChunkUtil.getChunkPos(this.id);
        this.stage = tag.getInt("stage");

        String conceptId = tag.getString("structure");
        if (conceptId != null && !conceptId.isEmpty()) {
            this.structureConcept = MOD_CONFIG.getStructureConcept(conceptId);
        }

        this.upgradeRejectedStatus = -1;
        if(tag.contains("rejectedUpgrade")) {
            this.upgradeRejectedStatus = tag.getInt("rejectedUpgrade");
        }

        // Deserialize structure starts using vanilla StructureStart.loadStaticStart
        if (tag.contains("structureStarts")) {
            this.structureStarts = new HashMap<>();
            this.structureBoxes = new HashMap<>();
            CompoundTag startsTag = tag.getCompound("structureStarts");
            net.minecraft.core.Registry<Structure> registry = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            StructurePieceSerializationContext ctx = StructurePieceSerializationContext.fromLevel(level);
            for (String key : startsTag.getAllKeys()) {
                ResourceLocation loc = new ResourceLocation(key);
                Structure structure = registry.get(loc);
                if (structure != null) {
                    StructureStart start = StructureStart.loadStaticStart(
                        ctx,
                        startsTag.getCompound(key),
                        level.getSeed()
                    );
                    if (start != null) {
                        this.structureStarts.put(structure, start);
                        this.structureBoxes.put(loc, start.getBoundingBox());
                    }
                }
            }
        }
    }
}
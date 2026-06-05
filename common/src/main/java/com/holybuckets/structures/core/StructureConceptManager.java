package com.holybuckets.structures.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.DatastoreSaveEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.TickType;
import com.google.gson.JsonPrimitive;
import com.holybuckets.foundation.datastore.WorldSaveData;
import com.holybuckets.structures.Constants;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.config.ModConfig;
import com.holybuckets.structures.config.model.StructureConcept;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.blay09.mods.balm.api.event.LivingDeathEvent;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.PlayerChangedDimensionEvent;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.chunk.StructureAccess;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Class: StructureConceptManager
 * Description: Manages timed structures for a single ServerLevel.
 * Each instance is 1:1 with a ServerLevel. Tracks ManagedStructureConceptChunk
 * objects (max 1 per chunk) on chunk load/unload events.
 *
 * Currently initialized only for the Overworld, but designed to support
 * other dimensions in the future.
 */
public class StructureConceptManager {

    public static final String CLASS_ID = "011";

    private static final String KEY_GLOBAL_STAGE = "globalStage";
    private static final String KEY_PLAYER_SPAWN_POS = "playerSpawnPos";

    private final ServerLevel level;
    private final Registry<Structure> registry;
    private final Map<ChunkPos, ManagedStructureConceptChunk> managedChunks;
    private final Map<EntityType<?>, Set<ManagedStructureConceptChunk>> mobTrackingChunks = new HashMap<>();
    private static int globalStage=0;


    //** STATICS
    static Map<LevelAccessor, StructureConceptManager> MANAGERS = new HashMap<>();
    static ModConfig MOD_CONFIG;
    static GeneralConfig GENERAL_CONFIG;
    static Map<String, BlockPos> playerSpawnPos = new HashMap<>();
    static Map<StructureConcept, Integer> conceptStages = new HashMap<>();
    static final Map<StructureConcept, ChunkPos> uniqueStructureChunks = new HashMap<>(); //concept -> the one chunk allowed past uniqueStage
    static boolean pauseUpgrades = false;


    //** CONSTRUCTORS
    private StructureConceptManager(ServerLevel level) {
        this.level = level;
        this.registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        this.managedChunks = new HashMap<>();
        MANAGERS.put(level, this);
        LoggerProject.logInit("011000", StructureConceptManager.class.getName());
    }



    //** GETTERS **//
    public ServerLevel getLevel() {
        return level;
    }

    private ResourceLocation getStructureId(Structure s) {
        return registry.getKey(s);
    }

    public Map<ChunkPos, ManagedStructureConceptChunk> getManagedChunks() {
        return Collections.unmodifiableMap(managedChunks);
    }

    public ManagedStructureConceptChunk getManagedChunk(ChunkPos chunkId) {
        return managedChunks.get(chunkId);
    }

    public static ManagedStructureConceptChunk getManagedChunk(LevelAccessor level, ChunkPos cp) {
        StructureConceptManager manager = MANAGERS.get(level);
        if (manager == null) return null;
        return manager.managedChunks.get(cp);
    }

    @Nullable
    public static StructureConceptManager get(LevelAccessor level) {
        return MANAGERS.get(level);
    }

    public static void addManagedChunk(ServerLevel level, ManagedStructureConceptChunk managedStructureConceptChunk) {
        StructureConceptManager manager = MANAGERS.get(level);
        if (manager != null) {
            manager.managedChunks.put(managedStructureConceptChunk.getChunkPos(), managedStructureConceptChunk);
        }
    }

    public void removeManagedChunk(String chunkId) {
        managedChunks.remove(chunkId);
    }


    //** EVENT HANDLERS **//

    private void handleSetStartForStructure(StructureSetStartContext ctx) {
        if( MOD_CONFIG.isActiveStructure(getStructureId(ctx.structure)) ) {
            registerManagedChunk(ctx);
        }
    }

    private void handleChunkLoad(ChunkLoadingEvent.Load event) {
        ChunkPos chunkPos = event.getChunkPos();
        String chunkId = ChunkUtil.getId(chunkPos);

        // If this is a managed chunk, place the appropriate stage of the structure.
        ManagedStructureConceptChunk managedChunk = managedChunks.get(chunkPos);
        if (managedChunk != null) {
            managedChunk.handleChunkLoaded(event);
            String structId = managedChunk.getStructureConcept().getStructureConceptId();
            LoggerProject.logDebug("011032", "Chunk loaded: " + chunkId + " " + structId );
            this.refreshMobTrackingChunks();
        }
    }

    private static void handleDimensionChange(PlayerChangedDimensionEvent event)
    {
        for(StructureConcept concept : MOD_CONFIG.getConcepts()) {
            if(!conceptStages.containsKey(concept)) continue;
            int nextStageNum = conceptStages.get(concept) + 1;
            ResourceKey<Level> key = concept.getStructureUpgradeDimension(nextStageNum);
            if(key == null) continue;
            if(key.equals(event.getToDim()) || key.equals(event.getFromDim())) {
                upgradeMe(nextStageNum-1, concept);
            }
        }

    }



    //** CORE
    public boolean isManagedChunk(ChunkPos chunkPos) {
        return managedChunks.containsKey(chunkPos);
    }
    /** Returns true if the structure at this chunk position is managed and should be hidden from vanilla.
    * //blacklisted
    *   */
    public boolean isStructureValidForStage(ChunkPos chunkPos, Structure structure) {
        ManagedStructureConceptChunk chunk = managedChunks.get(chunkPos);
        if(chunk == null) {
            return MOD_CONFIG.isBlacklisted(structure);
        }
        if(chunk.hasStructureInConcept(structure))
            return chunk.isStructureValidForStage(structure);
        if(chunk.isSourceStructure(structure)) return false;

        return true;
    }

    public void setManagerStage(int stage) {
        if(stage < 0) return;
        var ketSet = new HashSet<>(conceptStages.keySet());
        for(StructureConcept concept : ketSet) {
            pendingStageUpgrades.put(concept, stage);
        }
    }

    public static void setGlobalStage(Level level, int stage) {
        StructureConceptManager manager = MANAGERS.get(level);
        if (manager != null) {
            manager.setManagerStage(stage);
        }
        globalStage = stage;
    }

    public void pauseOrResumeChunkUpgrades(ChunkPos chunkPos, boolean pauseIfTrue) {
        ManagedStructureConceptChunk chunk = managedChunks.get(chunkPos);
        if(chunk != null) {
            chunk.pauseUpgrades(pauseIfTrue);
        }
    }

    public static void setPauseUpgrades(boolean pause) {
        pauseUpgrades = pause;
    }


    /**
     * Registers a new ManagedStructureConceptChunk for the given chunk id.
     * Only one ManagedStructureConceptChunk per chunk is allowed.
     *
     * @return the existing or newly registered ManagedStructureConceptChunk
     */
    public ManagedStructureConceptChunk registerManagedChunk(StructureSetStartContext ctx)
    {
        ChunkPos cp = ctx.sectionPos.chunk();
        if (managedChunks.containsKey(cp)) {
             return managedChunks.get(cp);
        }
        ManagedStructureConceptChunk managed = new ManagedStructureConceptChunk(
        level, cp, ctx, globalStage);
        managedChunks.put(cp, managed);


        LoggerProject.logDebug("011020", "Registered timed structure chunk: " + cp);
        return managed;
    }

    //** UPGRADES

    /**
     * Fires every few 1000 ticks, assesses structure upgrade conditions and queues upgrades
     *
     */
    private void monitorStructureUpgradesOnTick()
    {
        long daylight = level.getDayTime() % 24000;
        for(ManagedStructureConceptChunk chunk : managedChunks.values()) {
            StructureConcept concept = chunk.getStructureConcept();
            if(concept == null) continue;
            //checkEntityTriggers(chunk);
            checkDaylightTriggers(concept, daylight);
        }

       checkPendingUpgrades();

        if(pauseUpgrades) return;

        for(ManagedStructureConceptChunk chunk : managedChunks.values())
        {
            StructureConcept concept = chunk.getStructureConcept();
            if(!conceptStages.containsKey(concept)) continue;
            int stage = conceptStages.get(concept);

            int target = stage;
            int uniqueStage = concept.getUniqueStage();
            if(uniqueStage >= 0 && stage > uniqueStage) {
                ChunkPos claim = uniqueStructureChunks.get(concept);
                if(claim == null) {
                    claim = chunk.getChunkPos();
                    uniqueStructureChunks.put(concept, claim);
                }
                if(!claim.equals(chunk.getChunkPos())) target = uniqueStage; //non-unique chunks cap at uniqueStage
            }

            chunk.queueStructureUpgrade(target);
        }
    }

    private static void checkPendingUpgrades()
    {
        //Iterate over all pending stage upgrades
        Set<StructureConcept> conceptsCopy = new HashSet<>(pendingStageUpgrades.keySet());
        for(StructureConcept concept : conceptsCopy)
        {
            conceptStages.put(concept, pendingStageUpgrades.get(concept));
            int nextStage = pendingStageUpgrades.remove(concept) + 1;
            setNextUpgradeTrigger(concept, nextStage);
        }

        if(!conceptsCopy.isEmpty()) {
            for(StructureConceptManager m : MANAGERS.values()) m.refreshMobTrackingChunks();
        }
    }

    private static void setNextUpgradeTrigger(StructureConcept concept, int nextStage)
    {
        if(concept.getStructureUpgradeItem(nextStage) != null)
        {
            EventRegistrar.getInstance().runtimeOnPlayerHasItem(
                concept.getStructureUpgradeItem(nextStage),
                (e) -> upgradeMe(nextStage-1, concept));
        }
        else if(concept.getStructureUpgradeDays(nextStage) != null) {
            daysSinceUpgrade.put(concept, 0);
        }
        else {
            //dimensions handled per event
            //mob count handled on tick
            //mob death handled on death event
            //daylight handled on tick
        }
    }

    public static void upgradeSructureConcept(StructureConcept concept) {
        if(!conceptStages.containsKey(concept)) return;
        int stage = conceptStages.get(concept);
        upgradeMe(stage, concept);
    }

    //** UPGRADE TRIGGERS

    static final Map<StructureConcept, Integer> daysSinceUpgrade = new HashMap<>();
    static final Map<StructureConcept, Integer> pendingStageUpgrades = new HashMap<>();

    //rebuilds the kill-tracking index from every managed chunk's next-stage mob criteria
    private void refreshMobTrackingChunks()
    {

        mobTrackingChunks.clear();
        Map<StructureConcept, EntityType> applicableConcepts = new HashMap<>();
        for(var concept : conceptStages.keySet() )
        {
            int nextStage = conceptStages.get(concept) + 1;
            Map<EntityType<?>, Integer> killed = concept.getStructureUpgradeMobsKilled(nextStage);
            if(killed == null) continue;
            EntityType<?> mob = null;
            for(EntityType<?> type : killed.keySet()) {
                mobTrackingChunks.put(type, new HashSet<>());
                mob = type;
            }
            applicableConcepts.put(concept, mob);
        }
        for(ManagedStructureConceptChunk chunk : managedChunks.values()) {
            var concept = chunk.getStructureConcept();
            if( applicableConcepts.get(concept) == null) continue;
                mobTrackingChunks.get(applicableConcepts.get(concept)).add(chunk);
        }
    }


    private static void checkDaylightTriggers(StructureConcept c, long dayTime)
    {
        Long threshold = c.getStructureUpgradeDayCycle(conceptStages.get(c)+1);
        if(threshold != null && dayTime >= threshold) {
            upgradeMe(conceptStages.get(c), c);
        }
    }


    //evaluates the next-stage entity conditions for a chunk and queues a concept upgrade if met
    private static void checkEntityTriggers(ManagedStructureConceptChunk chunk)
    {
        StructureConcept concept = chunk.getStructureConcept();
        Integer stageObj = conceptStages.get(concept);
        int stage = stageObj;
        Map<EntityType<?>, Integer> killed = concept.getStructureUpgradeMobsKilled(stage + 1);
        if(killed != null) {
            for(Map.Entry<EntityType<?>, Integer> e : killed.entrySet()) {
                if(chunk.testKillLocalEntities(e.getKey(), e.getValue())) { upgradeMe(stage, concept); return; }
            }
        }
        Map<EntityType<?>, Integer> total = concept.getStructureUpgradeTotalEntities(stage + 1);
        if(total != null) {
            for(Map.Entry<EntityType<?>, Integer> e : total.entrySet()) {
                if(chunk.testCountLocalEntities(e.getKey(), e.getValue())) { upgradeMe(stage, concept); return; }
            }
        }
    }

    //increments local kill tallies on every living death within a tracked structure area
    private static void handleEntityDeath(LivingDeathEvent event)
    {
        LivingEntity dead = event.getEntity();
        if(dead == null) return;
        StructureConceptManager manager = MANAGERS.get(dead.level());
        if(manager == null) return;
        EntityType<?> type = dead.getType();
        Set<ManagedStructureConceptChunk> chunks = manager.mobTrackingChunks.get(type);
        if(chunks == null || chunks.isEmpty()) return;
        BlockPos deathPos = dead.blockPosition();
        for(ManagedStructureConceptChunk chunk : chunks) {
            if(chunk.isInEntityArea(deathPos)) chunk.onEntityKilledInArea(type);
        }
    }

    private static void upgradeMe(int effectiveStage, StructureConcept concept) {
        int stageNo = conceptStages.getOrDefault(concept, 0);
        if(stageNo != effectiveStage || pendingStageUpgrades.containsKey(concept)) return;
        pendingStageUpgrades.put(concept, stageNo+1);
    }


    //** DATA WRITING

    private static void load(DataStore ds)
    {
        WorldSaveData worldData = ds.getOrCreateWorldSaveData(Constants.MOD_ID);
        JsonElement stageEl = worldData.get(KEY_GLOBAL_STAGE);
        globalStage = (stageEl != null) ? stageEl.getAsInt() : 0;

        JsonElement spawnPosEl = worldData.get(KEY_PLAYER_SPAWN_POS);
        if(spawnPosEl != null && spawnPosEl.isJsonObject()) {
            JsonObject spawnPosObj = spawnPosEl.getAsJsonObject();
            for(Map.Entry<String, JsonElement> entry : spawnPosObj.entrySet()) {
                String dimAndName = entry.getKey();
                BlockPos bp = HBUtil.BlockUtil.stringToBlockPos(entry.getValue().getAsString());
                playerSpawnPos.put(dimAndName, bp);
            }
        }

        //load structure concepts and stages out of "conceptStages"
        for(StructureConcept concept : MOD_CONFIG.getConcepts()) {
            conceptStages.put(concept, 0);
        }
        JsonElement conceptStagesEl = worldData.get("conceptStages");
        if(conceptStagesEl != null && conceptStagesEl.isJsonObject())
        {

            JsonObject conceptStagesObj = conceptStagesEl.getAsJsonObject();
            for(Map.Entry<String, JsonElement> entry : conceptStagesObj.entrySet()) {
                String conceptId = entry.getKey();
                int stage = entry.getValue().getAsInt();
                StructureConcept concept = MOD_CONFIG.getStructureConcept(conceptId);
                if(concept != null) {
                    conceptStages.put(concept, stage);
                }
            }
        }
        for(var concept : conceptStages.keySet()) {
            setNextUpgradeTrigger(concept, conceptStages.get(concept)+1);
        }

        LoggerProject.logDebug("011001", "Loaded globalStage: " + globalStage);
        StringBuilder conceptStagesLog = new StringBuilder("Loaded concept stages:\n");
        for(Map.Entry<StructureConcept, Integer> entry : conceptStages.entrySet()) {
            String id = entry.getKey().getStructureConceptId();
            conceptStagesLog.append(id).append(": ").append(entry.getValue()).append("\n");
        }
        LoggerProject.logDebug("011009", conceptStagesLog.toString());

        //load daysSince upgrade out of "daysSinceUpgrade"
        JsonElement daysSinceUpgradeEl = worldData.get("daysSinceUpgrade");
        if(daysSinceUpgradeEl != null && daysSinceUpgradeEl.isJsonObject()) {
            JsonObject daysSinceUpgradeObj = daysSinceUpgradeEl.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : daysSinceUpgradeObj.entrySet()) {
                String conceptId = entry.getKey();
                int days = entry.getValue().getAsInt();
                StructureConcept concept = MOD_CONFIG.getStructureConcept(conceptId);
                if (concept != null) {
                    daysSinceUpgrade.put(concept, days);
                }
            }
        }

        //load the unique structure claim per concept
        JsonElement uniqueEl = worldData.get("uniqueStructureChunks");
        if(uniqueEl != null && uniqueEl.isJsonObject()) {
            for(Map.Entry<String, JsonElement> entry : uniqueEl.getAsJsonObject().entrySet()) {
                StructureConcept concept = MOD_CONFIG.getStructureConcept(entry.getKey());
                if(concept != null) {
                    uniqueStructureChunks.put(concept, ChunkUtil.getChunkPos(entry.getValue().getAsString()));
                }
            }
        }

        for(StructureConceptManager m : MANAGERS.values()) m.refreshMobTrackingChunks();
    }

    private static void save(DataStore ds)
    {
        WorldSaveData worldData = ds.getOrCreateWorldSaveData(Constants.MOD_ID);
        worldData.addProperty(KEY_GLOBAL_STAGE, new JsonPrimitive(globalStage));
        LoggerProject.logDebug("011002", "Saved globalStage: " + globalStage);

        //Save all entries in playerSpawnPos as a json object with "key": "blockpos"
        JsonObject spawnPosObj = new JsonObject();
        for(Map.Entry<String, BlockPos> entry : playerSpawnPos.entrySet()) {
            String bp = HBUtil.BlockUtil.positionToString(entry.getValue());
            spawnPosObj.addProperty(entry.getKey(), bp);
        }
        worldData.addProperty(KEY_PLAYER_SPAWN_POS, spawnPosObj);

        //save all concept ids vs stage no
        JsonObject conceptStagesObj = new JsonObject();
        for (Map.Entry<StructureConcept, Integer> entry : conceptStages.entrySet()) {
            String conceptId = entry.getKey().getStructureConceptId();
            int stage = entry.getValue();
            conceptStagesObj.addProperty(conceptId, stage);
        }
        worldData.addProperty("conceptStages", conceptStagesObj);

        //save all the days since last upgrade for each concept
        JsonObject daysSinceUpgradeObj = new JsonObject();
        for (Map.Entry<StructureConcept, Integer> entry : daysSinceUpgrade.entrySet()) {
            String conceptId = entry.getKey().getStructureConceptId();
            int days = entry.getValue();
            daysSinceUpgradeObj.addProperty(conceptId, days);
        }
        worldData.addProperty("daysSinceUpgrade", daysSinceUpgradeObj);

        //save the unique structure claim per concept
        JsonObject uniqueObj = new JsonObject();
        for (Map.Entry<StructureConcept, ChunkPos> entry : uniqueStructureChunks.entrySet()) {
            uniqueObj.addProperty(entry.getKey().getStructureConceptId(), ChunkUtil.getId(entry.getValue()));
        }
        worldData.addProperty("uniqueStructureChunks", uniqueObj);
    }


    //** STATICS:

    public static boolean isManagedChunk(ServerLevel level, ChunkPos pos) {
        if(level.isClientSide()) return false;
        StructureConceptManager manager = MANAGERS.get(level);
        if (manager == null) return false;
        return manager.managedChunks.containsKey(pos);
    }

    public static Map<String, BlockPos> getPlayerSpawnPos() {
        return playerSpawnPos;
    }


    //** EVENTS

    public static void init(EventRegistrar reg) {
        reg.registerOnBeforeServerStarted(StructureConceptManager::onServerStart);
        reg.registerOnServerStopped(StructureConceptManager::onServerStopped);
        reg.registerOnLevelLoad(StructureConceptManager::onLevelLoad, EventPriority.High);
        reg.registerOnChunkLoad(StructureConceptManager::onChunkLoadEvent);
        //reg.registerOnChunkUnload(StructureConceptManager::onChunkUnloadEvent);
        reg.registerOnServerTick(TickType.ON_120_TICKS, StructureConceptManager::on1200Ticks);
        //reg.registerOnServerTick(TickType.ON_6000_TICKS, StructureConceptManager::on6000Ticks);
        reg.registerOnServerTick(TickType.ON_SINGLE_TICK, StructureConceptManager::onServerTick);
        reg.registerOnDataSave(StructureConceptManager::onDataSave);
        reg.registerOnDailyTick(GeneralConfig.OVERWORLD_LOC, StructureConceptManager::onDailyTick);
        reg.registerOnPlayerChangedDimension(StructureConceptManager::handleDimensionChange);
        Balm.getEvents().onEvent(LivingDeathEvent.class, StructureConceptManager::handleEntityDeath);

        ManagedStructureConceptChunk.registerManagedChunkData();
    }

    private static void onServerStart(ServerStartingEvent event) {
        MANAGERS.clear();
        GENERAL_CONFIG = GeneralConfig.getInstance();
        MOD_CONFIG = ModConfig.getInstance();
        ManagedStructureConceptChunk.GENERAL_CONFIG = GeneralConfig.getInstance();
        ManagedStructureConceptChunk.MOD_CONFIG = ModConfig.getInstance();

    }

    private static void onServerStopped(ServerStoppedEvent event) {
        for(var manager : MANAGERS.values()) {
            manager.managedChunks.clear();
            manager.mobTrackingChunks.clear();
        }
        StructureConceptManager.conceptStages.clear();
        StructureConceptManager.pendingStageUpgrades.clear();
        StructureConceptManager.daysSinceUpgrade.clear();
        StructureConceptManager.uniqueStructureChunks.clear();

        StructureConceptManager.MANAGERS.clear();
    }



    private static void onLevelLoad(LevelLoadingEvent.Load event) {
        if(true) return;
        if (event.getLevel().isClientSide()) return;

        if(MANAGERS.containsKey(event.getLevel())) return;
        MANAGERS.put(event.getLevel(), new StructureConceptManager((ServerLevel) event.getLevel()));

        if (event.getLevel()!=GENERAL_CONFIG.OVERWORLD) return;
        StructureConceptManager.load(GeneralConfig.getInstance().getDataStore());
    }


    private static void onChunkLoadEvent(ChunkLoadingEvent.Load event) {
        if (event.getLevel().isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        if (!serverLevel.dimension().equals(Level.OVERWORLD)) return;

        StructureConceptManager manager = MANAGERS.get(serverLevel);
        if (manager != null && manager.isManagedChunk(event.getChunkPos())) {
            manager.handleChunkLoad(event);
        }
    }

    private static void on1200Ticks(ServerTickEvent event) {
        //track player spawn points

        for(ServerPlayer player : HBUtil.PlayerUtil.getAllPlayers()) {
            String dim = HBUtil.LevelUtil.toLevelId(player.level());
            String name = HBUtil.PlayerUtil.getId(player);
            if(player.getRespawnPosition() == null)
                playerSpawnPos.remove(dim+"|"+name);
            else
                playerSpawnPos.put(dim+"|"+name, player.getRespawnPosition());
        }

        for(StructureConceptManager manager : MANAGERS.values()) {
            manager.monitorStructureUpgradesOnTick();
        }
    }

    private static void onServerTick(ServerTickEvent event) {
        for(StructureConceptManager manager : MANAGERS.values()) {
            manager.managedChunks.values().forEach(chunk -> chunk.onServerTick(event));
        }
    }


    private static void onDataSave(DatastoreSaveEvent event) {
            StructureConceptManager.save(event.getDataStore());
    }

    private static void onDailyTick(ServerTickEvent.DailyTickEvent event) {
        for(StructureConceptManager manager : MANAGERS.values()) {
            manager.managedChunks.values().forEach(chunk -> chunk.onPlayersWakeUpEvent());
        }
        var daysKeys = new HashSet<>(daysSinceUpgrade.keySet());
        for(StructureConcept c : daysKeys) {
            int s = conceptStages.get(c);
            if( c.getStructureUpgradeDays(s+1) == null) continue;
            boolean upgrade = c.getStructureUpgradeDays(s+1) >= daysSinceUpgrade.get(c);
            if(upgrade) {
                upgradeMe(s, c); daysSinceUpgrade.remove(c);
            } else {
                daysSinceUpgrade.put(c, daysSinceUpgrade.get(c) + 1);
            }
        }
    }

    public static void onTryGenerateStructure(StructureGenerateContext ctx) {
        for (Map.Entry<LevelAccessor, StructureConceptManager> entry : MANAGERS.entrySet()) {
            LevelAccessor levelAccessor = entry.getKey();
            if (levelAccessor instanceof ServerLevel serverLevel) {
                //entry.getValue().handleTryGenerateStructure(ctx);
                return;
            }
        }
    }

    public static void onSetStartForStructure(StructureSetStartContext ctx) {
        StructureConceptManager manager = MANAGERS.get(ctx.serverLevel);
        if (manager != null) {
            manager.handleSetStartForStructure(ctx);
        }
    }

    public static void onStructureLoad(ChunkPos chunkPos, Map<Structure, StructureStart> structureStarts) {
        for (Map.Entry<LevelAccessor, StructureConceptManager> entry : MANAGERS.entrySet()) {
                //entry.getValue().handleStructureLoad(chunkPos, structureStarts);
        }
    }

    /**
     * Gets structure starts from ManagedChunk before ManagedChunk had access to levelChunk
     * @param chunkPos
     * @return
     */
    public Map<? extends Structure, StructureStart> getInitialStarts(ChunkPos chunkPos) {
        if(!managedChunks.containsKey(chunkPos)) return Collections.emptyMap();
        return managedChunks.get(chunkPos).getCurrentStarts();
    }


    //** INNER CLASSSES **/

    /**
     * Holds all parameters passed from the MixinChunkGenerator injection,
     * providing easy public access to each value.
     */
    public static class StructureGenerateContext {
        public final StructureSet.StructureSelectionEntry structureEntry;
        public final StructureManager structureManager;
        public final RegistryAccess registryAccess;
        public final RandomState randomState;
        public final StructureTemplateManager structureTemplateManager;
        public final long seed;
        public final ChunkAccess chunk;
        public final ChunkPos chunkPos;
        public final SectionPos sectionPos;

        public StructureGenerateContext(
            StructureSet.StructureSelectionEntry structureEntry,
            StructureManager structureManager,
            RegistryAccess registryAccess,
            RandomState randomState,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            SectionPos sectionPos
        ) {
            this.structureEntry = structureEntry;
            this.structureManager = structureManager;
            this.registryAccess = registryAccess;
            this.randomState = randomState;
            this.structureTemplateManager = structureTemplateManager;
            this.seed = seed;
            this.chunk = chunk;
            this.chunkPos = chunkPos;
            this.sectionPos = sectionPos;
        }
    }

    /**
     * Holds all parameters passed from the MixinStructureManager injection,
     * providing easy public access to each value.
     */
    public static class StructureSetStartContext {
        public final SectionPos sectionPos;
        public final Structure structure;
        public final StructureStart structureStart;
        public final StructureAccess structureAccess;
        public final ServerLevel serverLevel;

        public StructureSetStartContext(
            SectionPos sectionPos,
            Structure structure,
            StructureStart structureStart,
            StructureAccess structureAccess,
            ServerLevel serverLevel
        ) {
            this.sectionPos = sectionPos;
            this.structure = structure;
            this.structureStart = structureStart;
            this.structureAccess = structureAccess;
            this.serverLevel = serverLevel;
        }
    }
}
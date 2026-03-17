package com.holybuckets.structures.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.DatastoreSaveEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.TickType;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.config.ModConfig;
import com.holybuckets.structures.core.model.ManagedStructureConceptChunk;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.*;

/**
 * Class: TimedStructureManager
 * Description: Manages timed structures for a single ServerLevel.
 * Each instance is 1:1 with a ServerLevel. Tracks ManagedTimedStructureChunk
 * objects (max 1 per chunk) on chunk load/unload events.
 *
 * Currently initialized only for the Overworld, but designed to support
 * other dimensions in the future.
 */
public class StructureConceptManager {

    public static final String CLASS_ID = "011";

    /** Static manager map and state **/
    private static Map<LevelAccessor, StructureConceptManager> MANAGERS = new HashMap<>();

    /** Instance fields **/
    private final ServerLevel level;
    private final Map<String, ManagedStructureConceptChunk> managedChunks;

    /** Constructor **/
    private StructureConceptManager(ServerLevel level) {
        this.level = level;
        this.managedChunks = new HashMap<>();
        MANAGERS.put(level, this);
        LoggerProject.logInit(CLASS_ID + "000", StructureConceptManager.class.getName());
    }

    /** Getters **/

    public ServerLevel getLevel() {
        return level;
    }

    public Map<String, ManagedStructureConceptChunk> getManagedChunks() {
        return Collections.unmodifiableMap(managedChunks);
    }

    public ManagedStructureConceptChunk getManagedChunk(String chunkId) {
        return managedChunks.get(chunkId);
    }

    public static StructureConceptManager get(LevelAccessor level) {
        return MANAGERS.get(level);
    }

    /** Instance event handlers **/

    private void onChunkLoad(ChunkAccess chunk) {
        String chunkId = ChunkUtil.getId(chunk.getPos());

        // Only track chunks that already have a ManagedTimedStructureChunk registered
        if (managedChunks.containsKey(chunkId)) {
            ManagedStructureConceptChunk managed = managedChunks.get(chunkId);
            managed.setLevel(level);
            LoggerProject.logDebug(CLASS_ID + "010",
                "Chunk loaded with timed structure: " + chunkId);
        }
    }

    private void onChunkUnload(ChunkAccess chunk) {
        String chunkId = ChunkUtil.getId(chunk.getPos());

        if (managedChunks.containsKey(chunkId)) {
            LoggerProject.logDebug(CLASS_ID + "011",
                "Chunk unloaded with timed structure: " + chunkId);
        }
    }

    /**
     * Registers a new ManagedTimedStructureChunk for the given chunk id.
     * Only one ManagedTimedStructureChunk per chunk is allowed.
     * @return the existing or newly registered ManagedTimedStructureChunk
     */
    public ManagedStructureConceptChunk registerManagedChunk(ManagedStructureConceptChunk managed) {
        String chunkId = managed.getId();
        if (managedChunks.containsKey(chunkId)) {
            return managedChunks.get(chunkId);
        }
        managedChunks.put(chunkId, managed);
        LoggerProject.logDebug(CLASS_ID + "020",
            "Registered timed structure chunk: " + chunkId);
        return managed;
    }

    public void removeManagedChunk(String chunkId) {
        managedChunks.remove(chunkId);
    }

    /** Daily tick handler - called once per in-game day **/
    private void onDailyTick() {
        // TODO: Implement daily progression logic
    }

    /** Persistence **/

    private void load(DataStore ds) {
        // TODO: Load managed timed structure chunks from datastore
    }

    private void save(DataStore ds) {
        // TODO: Save managed timed structure chunks to datastore
    }

    /** Static initialization - subscribes static methods to events **/
    public static void init(EventRegistrar reg)
    {
        reg.registerOnBeforeServerStarted(StructureConceptManager::onServerStart);
        reg.registerOnLevelLoad(StructureConceptManager::onLevelLoad, EventPriority.High);
        reg.registerOnChunkLoad(StructureConceptManager::onChunkLoadEvent);
        reg.registerOnChunkUnload(StructureConceptManager::onChunkUnloadEvent);
        // Daily tick: ON_24000_TICKS is ideal (1 MC day), update if TickType supports it
        reg.registerOnServerTick(TickType.ON_1200_TICKS, StructureConceptManager::onDailyTickEvent);
        reg.registerOnDataSave(StructureConceptManager::onDataSave);

        ManagedStructureConceptChunk.registerManagedChunkData();
    }

    /** Static event handlers **/

    private static void onServerStart(ServerStartingEvent event) {
        MANAGERS.clear();
        ManagedStructureConceptChunk.GENERAL_CONFIG = GeneralConfig.getInstance();
        ManagedStructureConceptChunk.MOD_CONFIG = ModConfig.getInstance();
    }

    private static void onLevelLoad(LevelLoadingEvent.Load event) {
        if (event.getLevel().isClientSide()) return;

        // Only initialize for the Overworld for now
        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        if (!serverLevel.dimension().equals(Level.OVERWORLD)) return;

        StructureConceptManager manager = new StructureConceptManager(serverLevel);
        manager.load(GeneralConfig.getInstance().getDataStore());
    }

    private static void onChunkLoadEvent(ChunkLoadingEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        StructureConceptManager manager = MANAGERS.get(event.getLevel());
        if (manager != null) {
            manager.onChunkLoad(event.getChunk());
        }
    }

    private static void onChunkUnloadEvent(ChunkLoadingEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        StructureConceptManager manager = MANAGERS.get(event.getLevel());
        if (manager != null) {
            manager.onChunkUnload(event.getChunk());
        }
    }

    private static void onDailyTickEvent(ServerTickEvent event) {
        for (StructureConceptManager manager : MANAGERS.values()) {
            manager.onDailyTick();
        }
    }

    private static void onDataSave(DatastoreSaveEvent event) {
        for (StructureConceptManager manager : MANAGERS.values()) {
            manager.save(event.getDataStore());
        }
    }
}

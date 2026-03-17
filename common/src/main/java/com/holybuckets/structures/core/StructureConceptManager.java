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
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.saveddata.maps.StructureAccess;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    // -------------------------------------------------------------------------
    // Public static inner class: StructureGenerateContext
    // -------------------------------------------------------------------------

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
        public final CallbackInfo ci;

        public StructureGenerateContext(
            StructureSet.StructureSelectionEntry structureEntry,
            StructureManager structureManager,
            RegistryAccess registryAccess,
            RandomState randomState,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            SectionPos sectionPos,
            CallbackInfo ci
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
            this.ci = ci;
        }
    }

    // -------------------------------------------------------------------------
    // Public static inner class: StructureSetStartContext
    // -------------------------------------------------------------------------

    /**
     * Holds all parameters passed from the MixinStructureManager injection,
     * providing easy public access to each value.
     */
    public static class StructureSetStartContext {
        public final SectionPos sectionPos;
        public final Structure structure;
        public final StructureStart structureStart;
        public final StructureAccess structureAccess;
        public final CallbackInfo ci;

        public StructureSetStartContext(
            SectionPos sectionPos,
            Structure structure,
            StructureStart structureStart,
            StructureAccess structureAccess,
            CallbackInfo ci
        ) {
            this.sectionPos = sectionPos;
            this.structure = structure;
            this.structureStart = structureStart;
            this.structureAccess = structureAccess;
            this.ci = ci;
        }
    }

    // -------------------------------------------------------------------------
    // Static entry point from MixinChunkGenerator
    // -------------------------------------------------------------------------

    /**
     * Test the spawning structure to determine if we need to track it
     * by matching the context's registryAccess to each managed ServerLevel's
     * registryAccess, then delegates to the instance handler.
     */
    public static void onTryGenerateStructure(StructureGenerateContext ctx) {
        for (Map.Entry<LevelAccessor, StructureConceptManager> entry : MANAGERS.entrySet()) {
            LevelAccessor levelAccessor = entry.getKey();
            if (levelAccessor instanceof ServerLevel serverLevel) {
                if (serverLevel.registryAccess() == ctx.registryAccess) {
                    entry.getValue().handleTryGenerateStructure(ctx);
                    return;
                }
            }
        }
    }

    /**
     * Handles the tryGenerateStructure event for this manager's level.
     */
    private void handleTryGenerateStructure(StructureGenerateContext ctx) {
        logStructurePlacement(ctx);
        // TODO: Add further structure concept logic here
    }

    /**
     * Logs the structure that is attempting to be placed.
     */
    private void logStructurePlacement(StructureGenerateContext ctx) {
        String structureName = "unknown";
        if (ctx.structureEntry != null && ctx.structureEntry.structure() != null) {
            structureName = ctx.structureEntry.structure().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
        }
        LoggerProject.logDebug(CLASS_ID + "030",
            "tryGenerateStructure called for structure: " + structureName
            + " at chunk: " + ctx.chunkPos);
    }

    // -------------------------------------------------------------------------
    // Static entry point from MixinStructureManager
    // -------------------------------------------------------------------------

    /**
     * Called from MixinStructureManager when setStartForStructure is invoked.
     * Iterates all managed ServerLevels and delegates to the instance handler.
     */
    public static void onSetStartForStructure(StructureSetStartContext ctx) {
        for (Map.Entry<LevelAccessor, StructureConceptManager> entry : MANAGERS.entrySet()) {
            LevelAccessor levelAccessor = entry.getKey();
            if (levelAccessor instanceof ServerLevel) {
                entry.getValue().handleSetStartForStructure(ctx);
                return;
            }
        }
    }

    /**
     * Handles the setStartForStructure event for this manager's level.
     */
    private void handleSetStartForStructure(StructureSetStartContext ctx) {
        logStructureSetStart(ctx);
        // TODO: Add further structure concept logic here
    }

    /**
     * Logs the structure start being set.
     */
    private void logStructureSetStart(StructureSetStartContext ctx) {
        String structureName = "unknown";
        if (ctx.structure != null) {
            structureName = ctx.structure.getClass().getSimpleName();
        }
        LoggerProject.logDebug(CLASS_ID + "031",
            "setStartForStructure called for structure: " + structureName
            + " at sectionPos: " + ctx.sectionPos);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Instance event handlers
    // -------------------------------------------------------------------------

    private void onChunkLoad(ChunkAccess chunk) {
        String chunkId = ChunkUtil.getId(chunk.getPos());

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
     * Registers a new ManagedStructureConceptChunk for the given chunk id.
     * Only one ManagedStructureConceptChunk per chunk is allowed.
     * @return the existing or newly registered ManagedStructureConceptChunk
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

    // -------------------------------------------------------------------------
    // Daily tick handler
    // -------------------------------------------------------------------------

    private void onDailyTick() {
        // TODO: Implement daily progression logic
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void load(DataStore ds) {
        // TODO: Load managed timed structure chunks from datastore
    }

    private void save(DataStore ds) {
        // TODO: Save managed timed structure chunks to datastore
    }

    // -------------------------------------------------------------------------
    // Static initialization
    // -------------------------------------------------------------------------

    /** Static initialization - subscribes static methods to events **/
    public static void init(EventRegistrar reg) {
        reg.registerOnBeforeServerStarted(StructureConceptManager::onServerStart);
        reg.registerOnLevelLoad(StructureConceptManager::onLevelLoad, EventPriority.High);
        reg.registerOnChunkLoad(StructureConceptManager::onChunkLoadEvent);
        reg.registerOnChunkUnload(StructureConceptManager::onChunkUnloadEvent);
        reg.registerOnServerTick(TickType.ON_1200_TICKS, StructureConceptManager::onDailyTickEvent);
        reg.registerOnDataSave(StructureConceptManager::onDataSave);

        ManagedStructureConceptChunk.registerManagedChunkData();
    }

    // -------------------------------------------------------------------------
    // Static event handlers
    // -------------------------------------------------------------------------

    private static void onServerStart(ServerStartingEvent event) {
        MANAGERS.clear();
        ManagedStructureConceptChunk.GENERAL_CONFIG = GeneralConfig.getInstance();
        ManagedStructureConceptChunk.MOD_CONFIG = ModConfig.getInstance();
    }

    private static void onLevelLoad(LevelLoadingEvent.Load event) {
        if (event.getLevel().isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        if (!serverLevel.dimension().equals(Level.OVERWORLD)) return;

        StructureConceptManager manager = new StructureConceptManager(serverLevel);
        manager.load(GeneralConfig.getInstance().getDataStore());
    }

    private static void onChunkLoadEvent(ChunkLoadingEvent.Load event) {
        if (event.getLevel().isClientS
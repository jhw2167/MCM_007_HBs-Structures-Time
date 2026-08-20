package com.holybuckets.structures.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.structures.LoggerProject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import com.holybuckets.structures.mixin.ChunkAccessAccessor;
import com.holybuckets.structures.mixin.StructureManagerAccessor;
import net.minecraft.world.RandomSequences;
import java.nio.file.Files;
import java.nio.file.Path;



public class ChunkRegenerator {

    private static final String CLASS_ID = "012";


    private static Map<ChunkPos, ChunkAccess> CHUNK_CACHE = new ConcurrentHashMap<>();

    //positions claimed by an in-flight regeneration, present before their chunk exists
    private static final Set<ChunkPos> CACHE_KEYS = ConcurrentHashMap.newKeySet();

    public static void clearCache(Set<ChunkPos> toClear) {
        CHUNK_CACHE.keySet().removeAll(toClear);
        CACHE_KEYS.removeAll(toClear);
    }

    public static void fillCache(Map<ChunkPos, ChunkAccess> chunks) {
        CHUNK_CACHE.putAll(chunks);
        CACHE_KEYS.addAll(chunks.keySet());
    }

    public static void copyChunk(ServerLevel level, ChunkPos pos, BoundingBox area,
    List<BlockPos> lootPos) {
        ChunkAccess proto = CHUNK_CACHE.get(pos);
        if (proto == null) return;

        LevelChunk live = ManagedChunkUtility.getManagedChunk(level, pos).getCachedLevelChunk();
        copySections(proto, live, area);
        live.setUnsaved(true);
        //notifyClients(level, live, area);

        //save all the lootable entities for players to loot
        lootPos.addAll(proto.getBlockEntitiesPos().stream()
            .filter(area::isInside)
            .toList());
    }

    // Copies block state data from the scratch ProtoChunk into the live LevelChunk, bounded by region.
    private static void copySections(ChunkAccess source, LevelChunk target, BoundingBox region) {
        int minSection = target.getMinSection();
        int maxSection = target.getMaxSection();
        Level level = target.getLevel();

        for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
            int blockMinY = SectionPos.sectionToBlockCoord(sectionY);
            int blockMaxY = blockMinY + 15;

            if (blockMaxY < region.minY() || blockMinY > region.maxY()) continue;

            int index = target.getSectionIndexFromSectionY(sectionY);
            LevelChunkSection sourceSection = source.getSection(index);
            LevelChunkSection targetSection = target.getSection(index);

            if (sourceSection.hasOnlyAir() && targetSection.hasOnlyAir()) continue;
            copyBlockStates(sourceSection, targetSection, target.getPos(), sectionY, region, target.getLevel());

            // Mark affected sections dirty in the light engine so it recomputes
            SectionPos sectionPos = SectionPos.of(target.getPos(), sectionY);
            //level.getLightEngine().updateSectionStatus(sectionPos, false);

        }

        //get all block entities in protochunk and copy them to the live chunk
        for (BlockPos bePos : source.getBlockEntitiesPos()) {
            if (!region.isInside(bePos)) continue;
            BlockEntity protoBe = source.getBlockEntity(bePos);
            if (protoBe == null) continue;
            level.setBlockEntity(protoBe);
        }


        // Also flag sky + block light as needing a full re-check for this chunk
        level.getChunkSource().getLightEngine().propagateLightSources(target.getPos());
    }

    // Per-block copy within a section, respecting the bounding box.
    private static void copyBlockStates(LevelChunkSection source, LevelChunkSection target,
                                        ChunkPos chunkPos, int sectionY, BoundingBox region,
                                        Level realWorld) {
        int baseX = chunkPos.getMinBlockX();
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = chunkPos.getMinBlockZ();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int worldX = baseX + x;
                    int worldY = baseY + y;
                    int worldZ = baseZ + z;

                    if(!region.isInside(worldX, worldY, worldZ)) continue;
                    BlockState state = source.getBlockState(x, y, z);
                    if(state.equals(target.getBlockState(x, y, z))) continue;

                    BlockPos bp = new BlockPos(worldX, worldY, worldZ);
                    if( realWorld.getBlockEntity(bp) != null ) {
                       realWorld.removeBlockEntity(bp);
                    }

                    realWorld.setBlock(bp, state, Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS);
                    //target.setBlockState(x, y, z, state);
                }
            }
        }
    }

    /**
     * Copies chest loot for structures for any block entity in the passed position.
     * Attempts to copy using NBT save and load
     * @param level
     * @param pos
     */
    public static void copyLoot(ServerLevel level, BlockPos pos) {
        ChunkAccess proto = CHUNK_CACHE.get(new ChunkPos(pos));
        if (proto == null) return;
        BlockEntity oldBe = proto.getBlockEntity(pos);
        BlockEntity newBe = level.getBlockEntity(pos);
        if (oldBe == null || newBe==null) return;
        net.minecraft.core.HolderLookup.Provider registries = level.registryAccess();
        CompoundTag itemData = oldBe.saveWithFullMetadata(registries);
        newBe.loadWithComponents(itemData, registries);
    }

    /**
     * Copy Mobs - copy the entities and all their properties from the protochunk to the live chunk.
     * @param level
     * @param chunk
     * @param region
     */
    private static void notifyClients(ServerLevel level, LevelChunk chunk, BoundingBox region) {
        level.getChunkSource().getLightEngine().propagateLightSources(chunk.getPos());

        ClientboundForgetLevelChunkPacket forget =
            new ClientboundForgetLevelChunkPacket(chunk.getPos());

        HBUtil.PlayerUtil.getAllPlayers().forEach(player -> {
            player.connection.send(forget);
            player.connection.send(new ClientboundLevelChunkWithLightPacket(
                chunk, level.getLightEngine(), null, null
            ));
        });
    }


    /** SERVER LEVEL COPY BASED WORLD REGEN **/

    private static final String DIM_LEVEL_NAME = "HBStructuresTempGen";
    private static final long GEN_TIMEOUT_NANOS = 30L * 1_000_000_000L;

    private static ServerLevel VIRTUAL_LEVEL = null;
    private static LevelStorageSource.LevelStorageAccess DUMMY_SESSION = null;
    private static Path DUMMY_TEMP_DIR = null;
    private static MinecraftServer DUMMY_SERVER = null;

    private static final ChunkProgressListener DUMMY_LISTENER = new ChunkProgressListener() {
        @Override public void updateSpawnPos(ChunkPos center) { }
        @Override public void onStatusChange(ChunkPos pos, ChunkStatus status) { }
        @Override public void start() { }
        @Override public void stop() { }
    };


    /**
     * Creates virtual server level identical to posted level for virtual terrain regen
     * Necessarily creates and deletes directory as well
     * Fails if any chunk fails to load - later retries.
     */
    private static ServerLevel getOrCreateDummyLevel(ServerLevel level, MinecraftServer server)
    {
        if (VIRTUAL_LEVEL != null && DUMMY_SERVER == server) return VIRTUAL_LEVEL;
        if (VIRTUAL_LEVEL != null) shutdownDummyLevel();

        try {
            DUMMY_TEMP_DIR = Files.createTempDirectory("HBStructuresWorldGen");
            LevelStorageSource storage = LevelStorageSource.createDefault(DUMMY_TEMP_DIR);
            DUMMY_SESSION = storage.createAccess(DIM_LEVEL_NAME);

            PrimaryLevelData overworldData = (PrimaryLevelData) server.getWorldData().overworldData();
            WorldOptions worldOptions = overworldData.worldGenOptions();
            long seed = worldOptions.seed();

            LevelStem stem = new LevelStem(
                level.dimensionTypeRegistration(),
                level.getChunkSource().getGenerator()
            );

            VIRTUAL_LEVEL = new ServerLevel(
                server,
                Util.backgroundExecutor(),
                DUMMY_SESSION,
                overworldData,
                level.dimension(),
                stem,
                DUMMY_LISTENER,
                level.isDebug(),
                seed,
                List.of(),
                false,
                new RandomSequences(seed)
            );
            DUMMY_SERVER = server;

            LoggerProject.logInfo(CLASS_ID + "024", "Created scratch generation level at " + DUMMY_TEMP_DIR);
            return VIRTUAL_LEVEL;
        }
        catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "025",
                "Failed to create scratch generation level: " + e.getMessage());
            e.printStackTrace();
            shutdownDummyLevel();
            return null;
        }
    }


    public static void shutdownDummyLevel()
    {
        ServerLevel dummy = VIRTUAL_LEVEL;
        LevelStorageSource.LevelStorageAccess session = DUMMY_SESSION;
        Path dir = DUMMY_TEMP_DIR;

        VIRTUAL_LEVEL = null;
        DUMMY_SESSION = null;
        DUMMY_TEMP_DIR = null;
        DUMMY_SERVER = null;

        if (dummy != null) {
            try { dummy.close(); }
            catch (Exception e) { LoggerProject.logWarning(CLASS_ID + "026",
                "Failed to close scratch level: " + e.getMessage()); }
        }
        if (session != null) {
            try { session.close(); }
            catch (Exception e) { LoggerProject.logWarning(CLASS_ID + "027",
                "Failed to close scratch session: " + e.getMessage()); }
        }
        deleteTempDir(dir);
    }



    private static final int CREATE_REFERENCES_RADIUS = 8;

    //padded set from the most recent submitTerrainRegen, reused by the swap in wave 2
    private static Set<ChunkPos> PADDED_TARGETS = new LinkedHashSet<>();


    /**
     * Replaces the generated start with the one the managed chunk wants for its stage,
     * then rewrites the StructureCheck presence cache so downstream reads agree.
     */
    private static void swapStructureStarts(ServerLevel realLevel, ServerLevel dummyLevel,
                                            Collection<ChunkPos> positions)
    {
        StructureConceptManager manager = StructureConceptManager.get(realLevel);
        if (manager == null) return;

        ChunkGenerator generator = dummyLevel.getChunkSource().getGenerator();
        RandomState randomState = dummyLevel.getChunkSource().randomState();
        StructureTemplateManager templates = dummyLevel.getServer().getStructureManager();
        RegistryAccess registryAccess = dummyLevel.registryAccess();
        StructureCheck check =
            ((StructureManagerAccessor) dummyLevel.structureManager()).getStructureCheck();
        long seed = dummyLevel.getSeed();

        for (ChunkPos cp : positions)
        {
            if(!manager.isManagedChunk(cp)) continue;
            ChunkAccess chunk = CHUNK_CACHE.get(cp);
            if (chunk == null) continue;

            Map<Structure, StructureStart> starts =
                ((ChunkAccessAccessor) chunk).getRealStructureStarts();

            starts.keySet().removeIf(s -> !manager.isStructureValidForStage(cp, s));

            for (Map.Entry<? extends Structure, StructureStart> e : manager.getInitialStarts(cp).entrySet())
            {
                Structure wanted = e.getKey();
                StructureStart generated = wanted.generate(
                    registryAccess, generator, generator.getBiomeSource(), randomState,
                    templates, seed, cp, 0, dummyLevel, biome -> true);
                if (!generated.isValid()) generated = e.getValue();
                chunk.setStartForStructure(wanted, generated);
            }

            check.onStructureLoad(cp, chunk.getAllStarts());
        }
    }

    /** ASYNC BATCH API - caller owns the future list and drives it each tick **/

    private static final long REGEN_CHUNKS_TIMEOUT = 3_000_000L;

    /**
     * Submits regen futures to regenerates Terrain up to STRUCTURE_STARTS step
     * call onCheckTask() to flush server chunk cache util each is finish.
     */
    public static List<CompletableFuture<ChunkAccess>> submitTerrainRegen(ServerLevel level, Collection<ChunkPos> targets)
    {
        if (level == null || targets == null || targets.isEmpty()) return List.of();
        MinecraftServer server = level.getServer();
        if (server == null) return List.of();

        ServerLevel dummyLevel = getOrCreateDummyLevel(level, server);
        if (dummyLevel == null) return List.of();

        PADDED_TARGETS = padded(targets, CREATE_REFERENCES_RADIUS);
        //keys must be claimed before any future is submitted so getCachedManager resolves during generation
        CACHE_KEYS.addAll(PADDED_TARGETS);
        return submitChunkLoadTasks(dummyLevel, PADDED_TARGETS, ChunkStatus.STRUCTURE_STARTS);
    }

    /**
     * Regens chunks to FEATURES step
     * must call onCheckTask() to flush server chunk cache util each chunk is finished
     */
    public static List<CompletableFuture<ChunkAccess>> submitStructureRegen(ServerLevel realLevel,
                                                                            Collection<ChunkPos> targets)
    {
        if (VIRTUAL_LEVEL == null || targets == null || targets.isEmpty()) return List.of();
        Set<ChunkPos> padded = PADDED_TARGETS.isEmpty()
            ? padded(targets, CREATE_REFERENCES_RADIUS) : PADDED_TARGETS;
        swapStructureStarts(realLevel, VIRTUAL_LEVEL, padded);
        return submitChunkLoadTasks(VIRTUAL_LEVEL, targets, ChunkStatus.FEATURES);
    }


    public static void checkTask()
    {
        if (VIRTUAL_LEVEL == null) return;
        ServerChunkCache source = VIRTUAL_LEVEL.getChunkSource();
        long deadline = System.nanoTime() + REGEN_CHUNKS_TIMEOUT;
        while (System.nanoTime() < deadline) {
            if (!source.pollTask()) return;
        }
    }

    public static boolean allComplete(List<CompletableFuture<ChunkAccess>> futures) {
        return futures != null && !futures.isEmpty() && futures.stream().allMatch(CompletableFuture::isDone);
    }

    public static boolean anyFailed(List<CompletableFuture<ChunkAccess>> futures) {
        return futures != null && futures.stream().anyMatch(f -> f.isDone() && f.getNow(null) == null);
    }

    public static int completedCount(List<CompletableFuture<ChunkAccess>> futures) {
        if (futures == null) return 0;
        return (int) futures.stream().filter(CompletableFuture::isDone).count();
    }

    //bounding rect expanded by radius; identical to per-target dilation for a contiguous area
    private static Set<ChunkPos> padded(Collection<ChunkPos> targets, int radius) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkPos cp : targets) {
            minX = Math.min(minX, cp.x); maxX = Math.max(maxX, cp.x);
            minZ = Math.min(minZ, cp.z); maxZ = Math.max(maxZ, cp.z);
        }
        if (minX > maxX) return new LinkedHashSet<>();

        Set<ChunkPos> out = new LinkedHashSet<>(targets);
        for (int x = minX - radius; x <= maxX + radius; x++) {
            for (int z = minZ - radius; z <= maxZ + radius; z++) {
                out.add(new ChunkPos(x, z));
            }
        }
        return out;
    }

    private static void awaitChunks(ServerChunkCache source, List<CompletableFuture<ChunkAccess>> futures)
    {
        //ServerChunkCache.pollTask() is public and delegates into the private mainThreadProcessor
        long deadline = System.nanoTime() + GEN_TIMEOUT_NANOS;
        while (!futures.stream().allMatch(CompletableFuture::isDone))
        {
            if (futures.stream().anyMatch(f -> f.isDone() && f.getNow(null)==null)) break;
            if (System.nanoTime() > deadline) {
                LoggerProject.logError(CLASS_ID + "023", "Scratch level generation timed out");
                break;
            }
            if (!source.pollTask()) Thread.yield();
        }
    }

    public static void harvest(List<CompletableFuture<ChunkAccess>> futures) {
        for (CompletableFuture<ChunkAccess> future : futures) {
            ChunkAccess chunk = future.getNow(null);
            if (chunk == null) continue;
            CHUNK_CACHE.put(chunk.getPos(), chunk);
            CACHE_KEYS.add(chunk.getPos());
        }
    }

    private static List<CompletableFuture<ChunkAccess>> submitChunkLoadTasks(ServerLevel scratch,
        Collection<ChunkPos> targets, ChunkStatus status)
    {
        List<CompletableFuture<ChunkAccess>> futures = new ArrayList<>(targets.size());
        for (ChunkPos cp : targets) {
            futures.add(scratch.getChunkSource()
                .getChunkFuture(cp.x, cp.z, status, true)
                .thenApply(result -> result.orElse(null)));
        }
        return futures;
    }


    public static boolean cacheContains(ChunkAccess me) {
        return CACHE_KEYS.contains(me.getPos());
    }


    //region files may still be handle-locked on windows, so retry before giving up
    private static void deleteTempDir(Path dir) {
        if (dir == null) return;
        for (int attempt = 0; attempt < 5; attempt++) {
            if (!Files.exists(dir)) return;
            try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                });
            } catch (Exception ignored) { }
            if (!Files.exists(dir)) return;
            try { Thread.sleep(100L * (attempt + 1)); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
        if (Files.exists(dir)) {
            dir.toFile().deleteOnExit();
            LoggerProject.logWarning(CLASS_ID + "022",
                "Could not delete scratch level dir, deferred to JVM exit: " + dir);
        }
    }


    public static void finish() {
        PADDED_TARGETS = new LinkedHashSet<>();
        shutdownDummyLevel();
        clearCache(new HashSet<>(CACHE_KEYS));
    }


    public static Level getVirtualLevel() {
        return VIRTUAL_LEVEL;
    }
}

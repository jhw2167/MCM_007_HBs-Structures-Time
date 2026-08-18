package com.holybuckets.structures.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.mixin.NoiseBasedChunkGeneratorInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.RandomSequences;
import java.nio.file.Files;
import java.nio.file.Path;



public class ChunkRegenerator {

    private static final String CLASS_ID = "012";


    private static Map<ChunkPos, ChunkAccess> CHUNK_CACHE = new HashMap<>();

    public static void clearCache(Set<ChunkPos> toClear) {
        CHUNK_CACHE.keySet().removeAll(toClear);
    }

    public static void fillCache(Map<ChunkPos, ChunkAccess> chunks) {
        CHUNK_CACHE.putAll(chunks);
    }

    public static void copyChunk(ServerLevel level, ChunkPos pos, BoundingBox area,
    List<BlockPos> lootPos) {
        ChunkAccess proto = CHUNK_CACHE.get(pos);
        if (proto == null) return;

        LevelChunk live = ManagedChunkUtility.getManagedChunk(level, pos).getCachedLevelChunk();
        copySections(proto, live, area);
        live.setUnsaved(true);
        notifyClients(level, live, area);

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
            level.getLightEngine().updateSectionStatus(sectionPos, false);

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

    private static ServerLevel DUMMY_LEVEL = null;
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
     * Simulates terrain gen over the specified area returns a map of chunks
     */
    public static Map<ChunkPos, ChunkAccess> regenerateArea(ServerLevel level, Collection<ChunkPos> targets)
    {
        if (level == null || targets == null || targets.isEmpty()) return Collections.emptyMap();
        MinecraftServer server = level.getServer();
        if (server == null) return Collections.emptyMap();

        ServerLevel dummyLevel = getOrCreateDummyLevel(level, server);
        if (dummyLevel == null) return Collections.emptyMap();

        try {
            return regenChunks(level, dummyLevel, targets);
        }
        catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "020",
                "Scratch level regeneration failed: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }




    /**
     * Creates virtual server level identical to posted level for virtual terrain regen
     * Necessarily creates and deletes directory as well
     * Fails if any chunk fails to load - later retries.
     */
    private static ServerLevel getOrCreateDummyLevel(ServerLevel level, MinecraftServer server)
    {
        if (DUMMY_LEVEL != null && DUMMY_SERVER == server) return DUMMY_LEVEL;
        if (DUMMY_LEVEL != null) shutdownDummyLevel();

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

            DUMMY_LEVEL = new ServerLevel(
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
            return DUMMY_LEVEL;
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
        ServerLevel dummy = DUMMY_LEVEL;
        LevelStorageSource.LevelStorageAccess session = DUMMY_SESSION;
        Path dir = DUMMY_TEMP_DIR;

        DUMMY_LEVEL = null;
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



    private static Map<ChunkPos, ChunkAccess> regenChunks(ServerLevel realLevel,
                                                          ServerLevel dummyLevel, Collection<ChunkPos> targets)
    {
        List<CompletableFuture<ChunkAccess>> futures = submitChunkLoadTasks(dummyLevel, targets);

        //ServerChunkCache.pollTask() is public and delegates into the private mainThreadProcessor
        ServerChunkCache source = dummyLevel.getChunkSource();
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

        CHUNK_CACHE.clear();
        for (CompletableFuture<ChunkAccess> future : futures) {
            ChunkAccess chunk = future.getNow(null);
            if (chunk == null) continue;
            CHUNK_CACHE.put(chunk.getPos(), chunk);
        }

        return CHUNK_CACHE;
    }

    private static List<CompletableFuture<ChunkAccess>> submitChunkLoadTasks(ServerLevel scratch,
        Collection<ChunkPos> targets)
    {
        List<CompletableFuture<ChunkAccess>> futures = new ArrayList<>(targets.size());
        for (ChunkPos cp : targets) {
            CHUNK_CACHE.put(cp, null);
            futures.add(scratch.getChunkSource()
                .getChunkFuture(cp.x, cp.z, ChunkStatus.FEATURES, true)
                .thenApply(result -> result.orElse(null)));
        }
        return futures;
    }

    public static boolean cacheContains(ChunkAccess me) {
        return CHUNK_CACHE.containsKey(me.getPos());
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
        shutdownDummyLevel();
        clearCache(CHUNK_CACHE.keySet());
    }


    public static Level getVirtualLevel() {
        return DUMMY_LEVEL;
    }
}

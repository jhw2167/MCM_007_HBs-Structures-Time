package com.holybuckets.structures.core.model;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.foundation.modelInterface.IMangedChunkData;
import com.holybuckets.structures.LoggerProject;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;

/**
 * Class: ManagedTimedStructureChunk
 * Description: Represents a single chunk that contains a timed structure.
 * Each instance is 1:1 with a ManagedChunk and registers as a managed chunk subclass.
 *
 * Tracks the current stage of progression for the structure in this chunk
 * and holds a reference to the Structure registry entry.
 */
public class ManagedTimedStructureChunk implements IMangedChunkData {

    private static final String CLASS_ID = "010";
    private static final String NBT_KEY_HEADER = "managedTimedStructureChunk";

    public static void registerManagedChunkData() {
        ManagedChunk.registerManagedChunkData(ManagedTimedStructureChunk.class,
            () -> new ManagedTimedStructureChunk(null));
    }

    /** Variables **/
    private LevelAccessor level;
    private String id;
    private ChunkPos pos;
    private int progressionStage;
    private Holder<Structure> structureHolder;

    /** Constructors **/

    /** Default constructor - creates dummy node for deserialization */
    private ManagedTimedStructureChunk(LevelAccessor level) {
        super();
        this.level = level;
        this.id = null;
        this.pos = null;
        this.progressionStage = 0;
        this.structureHolder = null;
    }

    /** Constructor with id for a chunk that may not be loaded yet */
    private ManagedTimedStructureChunk(LevelAccessor level, String id) {
        this(level);
        this.setId(id);
        this.pos = ChunkUtil.getChunkPos(id);
    }

    /** Full constructor with structure reference */
    public ManagedTimedStructureChunk(LevelAccessor level, String id, Holder<Structure> structureHolder) {
        this(level, id);
        this.structureHolder = structureHolder;
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

    public int getProgressionStage() {
        return progressionStage;
    }

    public Holder<Structure> getStructureHolder() {
        return structureHolder;
    }

    @Nullable
    public ResourceLocation getStructureLocation() {
        if (structureHolder == null) return null;
        return structureHolder.unwrapKey()
            .map(ResourceKey::location)
            .orElse(null);
    }

    public ManagedChunk getParent() {
        return ManagedTimedStructureChunk.getParent(level, id);
    }

    /** Setters **/

    @Override
    public void setId(String id) {
        if (id == null) return;
        this.id = id;
        this.pos = ChunkUtil.getChunkPos(id);
    }

    @Override
    public void setLevel(LevelAccessor level) {
        this.level = level;
    }

    public void setProgressionStage(int stage) {
        this.progressionStage = stage;
    }

    public void setStructureHolder(Holder<Structure> holder) {
        this.structureHolder = holder;
    }

    /** IMangedChunkData Overrides **/

    @Override
    public ManagedTimedStructureChunk getStaticInstance(LevelAccessor level, String id) {
        if (id == null || level == null) return null;
        return ManagedTimedStructureChunk.getInstance(level, id);
    }

    @Override
    public boolean isInit(String subClass) {
        return subClass.equals(ManagedTimedStructureChunk.class.getName()) && this.id != null;
    }

    @Override
    public void handleChunkLoaded(ChunkLoadingEvent.Load event) {
        this.level = event.getLevel();
        this.pos = event.getChunkPos();
    }

    @Override
    public void handleChunkUnloaded(ChunkLoadingEvent.Unload event) {
        // No-op for now
    }

    /** Serialization **/

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", this.id);
        tag.putInt("progressionStage", this.progressionStage);

        ResourceLocation structureLoc = getStructureLocation();
        if (structureLoc != null) {
            tag.putString("structure", structureLoc.toString());
        } else {
            tag.putString("structure", "");
        }

        LoggerProject.logDebug(CLASS_ID + "001", "Serializing ManagedTimedStructureChunk: " + tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;

        this.pos = ChunkUtil.getChunkPos(this.id);
        this.progressionStage = tag.getInt("progressionStage");

        String structureStr = tag.getString("structure");
        if (structureStr != null && !structureStr.isEmpty()) {
            // Structure holder will be resolved by the manager after deserialization
            // since we need registry access which requires a ServerLevel
            LoggerProject.logDebug(CLASS_ID + "002",
                "Deserialized ManagedTimedStructureChunk " + this.id
                + " with structure: " + structureStr + " stage: " + this.progressionStage);
        }
    }

    /** Static Methods **/

    public static ManagedTimedStructureChunk getInstance(LevelAccessor level, String id) {
        ManagedChunk parent = getParent(level, id);
        if (parent == null)
            return new ManagedTimedStructureChunk(level, id);

        ManagedTimedStructureChunk c = (ManagedTimedStructureChunk) parent.getSubclass(ManagedTimedStructureChunk.class);
        if (c == null)
            return new ManagedTimedStructureChunk(level, id);

        return c;
    }

    public static ManagedChunk getParent(LevelAccessor level, String id) {
        ManagedChunkUtility instance = ManagedChunkUtility.getInstance(level);
        return instance.getManagedChunk(id);
    }
}

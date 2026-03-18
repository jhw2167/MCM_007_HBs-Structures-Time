package com.holybuckets.structures.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil.ChunkUtil;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.foundation.modelInterface.IMangedChunkData;
import com.holybuckets.structures.config.ModConfig;
import com.holybuckets.structures.config.model.StructureConcept;
import net.blay09.mods.balm.api.event.ChunkLoadingEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;

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


    public static void registerManagedChunkData() {
        ManagedChunk.registerManagedChunkData(ManagedStructureConceptChunk.class,
            () -> new ManagedStructureConceptChunk(null));
    }

    /** Variables **/
    private ServerLevel level;
    private String id;
    private ChunkPos pos;
    private int stage;
    private StructureConcept structure;
    private StructureSetStartContext structureStartContext;

    /** Constructors **/

    /** Default constructor - creates dummy node for deserialization */
    private ManagedStructureConceptChunk(ServerLevel level) {
        super();
        this.level = level;
        this.id = null;
        this.pos = null;
        this.stage = 0;
        this.structure = null;
    }

    /** Constructor with id for a chunk that may not be loaded yet */
    private ManagedStructureConceptChunk(ServerLevel level, String id) {
        this(level);
        this.setId(id);
        this.pos = ChunkUtil.getChunkPos(id);
    }

    /** Full constructor with structure reference */
    public ManagedStructureConceptChunk(ServerLevel level, String id, StructureConcept concept) {
        this(level, id);
        this.structure = concept;
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

    public void setStage(int stage) {
        this.stage = stage;
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
        tag.putInt("stage", this.stage);

        if (structure != null) {
            tag.putString("structure", structure.getStructureConceptId());
        }

        //LoggerProject.logDebug(CLASS_ID + "001", "Serializing ManagedTimedStructureChunk: " + tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;

        this.pos = ChunkUtil.getChunkPos(this.id);
        this.stage = tag.getInt("stage");

        String conceptId = tag.getString("structure");
        if (conceptId != null && !conceptId.isEmpty()) {
            this.structure = MOD_CONFIG.getStructureConcept(conceptId);
        }
    }

    /** Static Methods **/

    public static ManagedStructureConceptChunk getInstance(LevelAccessor levelAcc, String id)
    {
        if(levelAcc.isClientSide()) return null;
        ServerLevel level = (ServerLevel) levelAcc;
        ManagedChunk parent = getParent(level, id);
        if (parent == null)
            return new ManagedStructureConceptChunk(level, id);

        ManagedStructureConceptChunk c = (ManagedStructureConceptChunk) parent.getSubclass(ManagedStructureConceptChunk.class);
        if (c == null)
            return new ManagedStructureConceptChunk(level, id);

        return c;
    }

    public static ManagedChunk getParent(LevelAccessor level, String id) {
        ManagedChunkUtility instance = ManagedChunkUtility.getInstance(level);
        return instance.getManagedChunk(id);
    }

    public void setStructureStartContext(StructureSetStartContext ctx, ResourceLocation sourceStructure) {
        this.structureStartContext = ctx;
        this.structure = MOD_CONFIG.getStructureConcept(sourceStructure);
    }
}

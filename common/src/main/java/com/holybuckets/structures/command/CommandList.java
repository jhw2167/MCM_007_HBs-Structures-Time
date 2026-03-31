package com.holybuckets.structures.command;

//Project imports

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.CommandRegistry;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.core.ChunkRegenerator;
import com.holybuckets.structures.core.StructureConceptManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;

import java.util.List;

public class CommandList {

    public static final String CLASS_ID = "033";
    private static final String PREFIX = "hbStructures";

    public static void register() {
        //CommandRegistry.register(LocateClusters::noArgs);
        //CommandRegistry.register(LocateClusters::limitCount);
        //CommandRegistry.register(LocateClusters::limitCountSpecifyBlockType);
        CommandRegistry.register(SetGlobalStage::withStageArg);
        CommandRegistry.register(RegenerateChunk::noArgs);
    }

    //1. Locate Clusters
    private static class LocateClusters
    {
        // Register the base command with no arguments
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locateClusters")
                    .executes(context -> execute(context.getSource(), -1, null)) // Default case (no args)
                );

        }

        // Register command with count argument
        private static LiteralArgumentBuilder<CommandSourceStack> limitCount() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locateClusters")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int count = IntegerArgumentType.getInteger(context, "count");
                            return execute(context.getSource(), count, null);
                        })
                    )
            );
        }

        // Register command with both count and blockType OR just blockType
        private static LiteralArgumentBuilder<CommandSourceStack> limitCountSpecifyBlockType() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locateClusters")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .then(Commands.argument("blockType", StringArgumentType.string())
                            .executes(context -> {
                                int count = IntegerArgumentType.getInteger(context, "count");
                                String blockType = StringArgumentType.getString(context, "blockType");
                                return execute(context.getSource(), count, blockType);
                            })
                        )
                    )
                    .then(Commands.argument("blockType", StringArgumentType.string())
                        .executes(context -> {
                            String blockType = StringArgumentType.getString(context, "blockType");
                            return execute(context.getSource(), -1, blockType);
                        })
                    )
            );
        }


        private static int execute(CommandSourceStack source, int count, String blockType)
        {
            LoggerProject.logDebug("010001", "Locate Clusters Command");
            return 0;
        }


    }
    //END COMMAND


    //2. Set Global Stage
    private static class SetGlobalStage
    {
        private static final int MIN_STAGE = 0;
        private static final int MAX_STAGE = 16;

        // Register command with stage argument
        private static LiteralArgumentBuilder<CommandSourceStack> withStageArg() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("setGlobalStage")
                    .then(Commands.argument("stage", IntegerArgumentType.integer())
                        .executes(context -> {
                            int stage = IntegerArgumentType.getInteger(context, "stage");
                            return execute(context.getSource(), stage);
                        })
                    )
                );
        }

        private static int execute(CommandSourceStack source, int stage)
        {
            LoggerProject.logDebug("010002", "Set Global Stage Command");

            if (stage < MIN_STAGE || stage > MAX_STAGE) {
                source.sendFailure(Component.literal(
                    "Invalid stage: " + stage + ". Stage must be between " + MIN_STAGE + " and " + MAX_STAGE + " (inclusive)."
                ));
                return 0;
            }

            StructureConceptManager.setGlobalStage(GeneralConfig.OVERWORLD, stage);
            source.sendSuccess(() -> Component.literal("Global stage set to " + stage + "."), true);
            return 1;
        }

    }
    //END COMMAND


    //3. Regenerate Chunk
    private static class RegenerateChunk
    {
        // Regenerates terrain for the chunk the player is standing in
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("regenerateChunk")
                    .executes(context -> execute(context.getSource()))
                );
        }

        private static int execute(CommandSourceStack source)
        {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.literal("This command must be run by a player."));
                return 0;
            }

            ServerLevel level = player.serverLevel();
            BlockPos playerPos = player.blockPosition();
            ChunkPos chunkPos = new ChunkPos(playerPos);

            source.sendSuccess(() -> Component.literal("Regenerating chunk at " + chunkPos + "..."), true);

            try {
                ProtoChunk chunk = ChunkRegenerator.createProtoChunk(level, chunkPos);
                boolean success = ChunkRegenerator.regenerateChunk(chunk, level, chunkPos);
                if (success) {
                    source.sendSuccess(() -> Component.literal("Chunk " + chunkPos + " regenerated."), true);
                    return 1;
                } else {
                    source.sendFailure(Component.literal("Chunk regeneration failed at " + chunkPos + "."));
                    return 0;
                }
            } catch (Exception e) {
                source.sendFailure(Component.literal("Error: " + e.getMessage()));
                LoggerProject.logError(CLASS_ID + "003", "Regenerate chunk command failed: " + e.getMessage());
                return 0;
            }
        }
    }
    //END COMMAND


}
//END CLASS COMMANDLIST

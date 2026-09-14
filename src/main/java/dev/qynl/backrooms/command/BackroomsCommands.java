package dev.qynl.backrooms.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.qynl.backrooms.hole.HoleEntry;
import dev.qynl.backrooms.level.BackroomsLevels;
import dev.qynl.backrooms.level.LevelTheme;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.IntegerArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/**
 * Operator commands for getting around the Backrooms - handy in creative and for testing.
 * <pre>
 *   /backrooms go &lt;level&gt;   descend straight to a level (0..MAX_LEVEL)
 *   /backrooms escape       surface back in the Overworld
 *   /backrooms here         report which level you are standing in
 * </pre>
 */
public final class BackroomsCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("backrooms")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("go")
                                .then(CommandManager.argument("level",
                                        IntegerArgumentType.integer(0, HoleEntry.MAX_LEVEL))
                                        .executes(BackroomsCommands::go)))
                        .then(CommandManager.literal("escape").executes(BackroomsCommands::escape))
                        .then(CommandManager.literal("here").executes(BackroomsCommands::here))));
    }

    private static int go(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        int level = IntegerArgumentType.getInteger(ctx, "level");
        HoleEntry.goToLevel(player, level);
        ctx.getSource().sendFeedback(() -> Text.literal("\u00a77The walls close in... Level " + level + "."), false);
        return 1;
    }

    private static int escape(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        HoleEntry.escapeToOverworld(player);
        ctx.getSource().sendFeedback(() -> Text.literal("\u00a7aYou surface back in the Overworld."), false);
        return 1;
    }

    private static int here(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        RegistryKey<World> key = player.getWorld().getRegistryKey();
        String name = null;
        for (int i = 0; i <= HoleEntry.MAX_LEVEL; i++) {
            LevelTheme theme = BackroomsLevels.get(i);
            if (theme != null && theme.dimensionKey().equals(key)) {
                name = theme.name();
                break;
            }
        }
        final String said = name != null ? "\u00a77You are in " + name + "." : "\u00a77You are not in the Backrooms.";
        ctx.getSource().sendFeedback(() -> Text.literal(said), false);
        return 1;
    }

    private BackroomsCommands() {
    }
}

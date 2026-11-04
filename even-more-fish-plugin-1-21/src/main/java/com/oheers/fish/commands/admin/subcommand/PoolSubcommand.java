package com.oheers.fish.commands.admin.subcommand;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oheers.fish.fishing.pool.PoolCommands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class PoolSubcommand {
    public LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal("pool")
            .requires(source -> source.getSender().hasPermission(PoolCommands.PERMISSION))
            .executes(ctx -> { PoolCommands.help(ctx.getSource().getSender()); return 1; })
            .then(Commands.literal("list").executes(ctx -> PoolCommands.list(ctx.getSource().getSender())))
            .then(Commands.literal("info").then(Commands.argument("id", StringArgumentType.word())
                .executes(ctx -> PoolCommands.info(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word())
                .executes(ctx -> PoolCommands.remove(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("create").then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 64))
                    .executes(ctx -> create(ctx, null, null))
                    .then(Commands.argument("speed-percent", DoubleArgumentType.doubleArg(0, 85))
                        .executes(ctx -> create(ctx, DoubleArgumentType.getDouble(ctx, "speed-percent"), null))
                        .then(Commands.argument("rare-multiplier", DoubleArgumentType.doubleArg(1, 10))
                            .executes(ctx -> create(ctx, DoubleArgumentType.getDouble(ctx, "speed-percent"),
                                DoubleArgumentType.getDouble(ctx, "rare-multiplier"))))))));
    }

    private int create(CommandContext<CommandSourceStack> ctx, Double speed, Double multiplier) {
        return PoolCommands.create(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id"),
            DoubleArgumentType.getDouble(ctx, "radius"), speed, multiplier);
    }
}

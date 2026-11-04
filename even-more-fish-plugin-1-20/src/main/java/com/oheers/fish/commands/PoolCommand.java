package com.oheers.fish.commands;

import com.oheers.fish.fishing.pool.PoolCommands;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.DoubleArgument;
import dev.jorel.commandapi.arguments.StringArgument;

final class PoolCommand {
    private PoolCommand() {}

    static CommandAPICommand create() {
        return new CommandAPICommand("pool")
            .withPermission(PoolCommands.PERMISSION)
            .executes(info -> { PoolCommands.help(info.sender()); })
            .withSubcommands(
                new CommandAPICommand("create")
                    .withArguments(new StringArgument("id"), new DoubleArgument("radius", 1, 64),
                        new DoubleArgument("speed-percent", 0, 85).setOptional(true),
                        new DoubleArgument("rare-multiplier", 1, 10).setOptional(true))
                    .executes((sender, args) -> {
                        PoolCommands.create(sender, (String) args.get("id"), (Double) args.get("radius"),
                            (Double) args.getOptional("speed-percent").orElse(null),
                            (Double) args.getOptional("rare-multiplier").orElse(null));
                    }),
                new CommandAPICommand("remove").withArguments(new StringArgument("id"))
                    .executes((sender, args) -> { PoolCommands.remove(sender, (String) args.get("id")); }),
                new CommandAPICommand("list").executes(info -> { PoolCommands.list(info.sender()); }),
                new CommandAPICommand("info").withArguments(new StringArgument("id"))
                    .executes((sender, args) -> { PoolCommands.info(sender, (String) args.get("id")); })
            );
    }
}

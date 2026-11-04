package com.oheers.fish.commands.admin.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oheers.fish.FishUtils;
import com.oheers.fish.commands.BrigCommandUtils;
import com.oheers.fish.commands.CommandUtils;
import com.oheers.fish.commands.arguments.CustomRodArgument;
import com.oheers.fish.fishing.rods.CustomRod;
import com.oheers.fish.fishing.rods.RodManager;
import com.oheers.fish.messages.ConfigMessage;
import com.oheers.fish.messages.EMFSingleMessage;
import com.oheers.fish.messages.abstracted.EMFMessage;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class CustomRodSubcommand {

    private final String name;

    public CustomRodSubcommand(@NotNull String name) {
        this.name = name;
    }

    public LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal(name)
            .then(
                Commands.literal("give")
                    .then(
                        Commands.argument("rod", new CustomRodArgument())
                            .executes(ctx -> executeGive(ctx, false))
                            .then(
                                Commands.argument("targets", ArgumentTypes.players())
                                    .executes(ctx -> executeGive(ctx, true))
                            )
                    )
            )
            .then(
                Commands.literal("list")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        var rods = RodManager.getInstance().getItemMap();
                        EMFSingleMessage.fromString("<gold>===== DANH SÁCH CẦN CÂU CUSTOM (" + rods.size() + ") =====").send(sender);
                        for (CustomRod rod : rods.values()) {
                            String statsStr = "<gray>• <yellow>" + rod.getId() + "<gray>: Luck: <gold>+" + String.format("%.0f", rod.getLuckModifier() * 100) + "%</gold>, Lure: <aqua>+" + String.format("%.0f", rod.getLureModifier() * 100) + "%</aqua>, x2: <light_purple>" + String.format("%.0f", rod.getDoubleCatchChance() * 100) + "%</light_purple>";
                            EMFSingleMessage.fromString(statsStr).send(sender);
                        }
                        return 1;
                    })
            )
            .then(
                Commands.literal("inspect")
                    .executes(ctx -> {
                        Player player = BrigCommandUtils.requirePlayer(ctx);
                        ItemStack handItem = player.getInventory().getItemInMainHand();
                        CustomRod rod = RodManager.getInstance().getRod(handItem);
                        if (rod == null) {
                            EMFSingleMessage.fromString("<red>Vật phẩm trên tay không phải là Cần Câu Custom của EvenMoreFish.").send(player);
                            return 1;
                        }

                        EMFSingleMessage.fromString("<gold>===== THÔNG TIN CẦN CÂU: <yellow>" + rod.getId() + " <gold>=====").send(player);
                        EMFSingleMessage.fromString("<gray> ○ <white>May mắn (Luck bonus): <yellow>+" + String.format("%.0f", rod.getLuckModifier() * 100) + "%").send(player);
                        EMFSingleMessage.fromString("<gray> ○ <white>Tốc độ cắn câu (Lure): <aqua>+" + String.format("%.0f", rod.getLureModifier() * 100) + "%").send(player);
                        EMFSingleMessage.fromString("<gray> ○ <white>Tỉ lệ x2 cá (Double Catch): <light_purple>+" + String.format("%.0f", rod.getDoubleCatchChance() * 100) + "%").send(player);
                        EMFSingleMessage.fromString("<gray> ○ <white>Hệ số EXP: <green>x" + String.format("%.1f", rod.getExpMultiplier())).send(player);
                        EMFSingleMessage.fromString("<gray> ○ <white>Không thể hỏng (Unbreakable): <white>" + rod.isUnbreakable()).send(player);
                        EMFSingleMessage.fromString("<gray> ○ <white>Cho phép gắn mồi (Baits): <white>" + rod.isBaitAllowed()).send(player);
                        return 1;
                    })
            )
            .then(
                // Direct fallback: /emf admin custom-rod <rod> [targets]
                Commands.argument("rod", new CustomRodArgument())
                    .executes(ctx -> executeGive(ctx, false))
                    .then(
                        Commands.argument("targets", ArgumentTypes.players())
                            .executes(ctx -> executeGive(ctx, true))
                    )
            );
    }

    private int executeGive(@NotNull CommandContext<CommandSourceStack> ctx, boolean allowConsole) throws CommandSyntaxException {
        CommandSender sender = allowConsole ? ctx.getSource().getSender() : BrigCommandUtils.requirePlayer(ctx);
        CustomRod rod = ctx.getArgument("rod", CustomRod.class);
        PlayerSelectorArgumentResolver targets = BrigCommandUtils.getArgumentOrNull(ctx, "targets", PlayerSelectorArgumentResolver.class);
        return executeGive(
            sender,
            rod,
            BrigCommandUtils.resolvePlayers(ctx, targets)
        );
    }

    private int executeGive(@NotNull CommandSender sender, @NotNull CustomRod rod, @NotNull List<Player> targets) throws CommandSyntaxException {
        if (targets.isEmpty()) {
            if (!(sender instanceof Player player)) {
                throw BrigCommandUtils.ERROR_NO_PLAYERS.create();
            }
            targets = List.of(player);
        }

        ItemStack rodItem = rod.create();

        for (Player player : targets) {
            FishUtils.giveItems(List.of(rodItem), player);
        }

        EMFMessage giveMessage = ConfigMessage.ADMIN_CUSTOM_ROD_GIVEN.getMessage();
        giveMessage.setVariable("{player}", CommandUtils.getPlayersVariable(targets));

        giveMessage.send(sender);
        return 1;
    }

}

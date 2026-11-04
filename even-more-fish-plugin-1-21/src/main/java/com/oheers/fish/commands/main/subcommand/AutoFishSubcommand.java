package com.oheers.fish.commands.main.subcommand;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.autofish.AutoFishMenuManager;
import com.oheers.fish.autofish.AutoFishProfile;
import com.oheers.fish.autofish.AutoFishingService;
import com.oheers.fish.commands.BrigCommandUtils;
import com.oheers.fish.commands.arguments.EMFPlayerArgument;
import com.oheers.fish.permissions.AdminPerms;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnstableApiUsage")
public class AutoFishSubcommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final String name;

    public AutoFishSubcommand(@NotNull String name) {
        this.name = name;
    }

    public LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal(name)
            .executes(ctx -> {
                Player player = BrigCommandUtils.requirePlayer(ctx);
                AutoFishMenuManager.getInstance().open(player);
                return 1;
            })
            .then(
                Commands.literal("toggle")
                    .executes(ctx -> {
                        Player player = BrigCommandUtils.requirePlayer(ctx);
                        AutoFishingService service = EvenMoreFish.getInstance().getAutoFishing();
                        if (service != null) {
                            service.toggle(player);
                        }
                        return 1;
                    })
            )
            .then(
                Commands.literal("give")
                    .requires(stack -> stack.getSender().hasPermission(AdminPerms.ADMIN))
                    .then(
                        Commands.argument("target", new EMFPlayerArgument())
                            .then(
                                Commands.argument("minutes", IntegerArgumentType.integer(1, 525600))
                                    .executes(ctx -> {
                                        CommandSender sender = ctx.getSource().getSender();
                                        Player target = ctx.getArgument("target", Player.class);
                                        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                        giveTime(sender, target, minutes);
                                        return 1;
                                    })
                            )
                    )
            );
    }

    private void giveTime(@NotNull CommandSender sender, @NotNull Player target, int minutes) {
        AutoFishingService service = EvenMoreFish.getInstance().getAutoFishing();
        if (service == null) {
            return;
        }
        AutoFishProfile profile = service.getProfile(target.getUniqueId());
        long addedSeconds = minutes * 60L;
        profile.addSeconds(addedSeconds);
        service.getDataStore().saveIfDirtyAsync();

        String formattedAdded = AutoFishProfile.formatDuration(addedSeconds);
        String formattedTotal = AutoFishProfile.formatDuration(profile.getRemainingSeconds());
        sender.sendMessage(MM.deserialize(
            "<dark_gray>[<aqua>⚡</aqua>]</dark_gray> <green>Đã cộng <yellow>+" + formattedAdded + "</yellow> thời gian Auto-Fishing cho <white>"
                + target.getName() + "</white>! <gray>(Tổng: <aqua>" + formattedTotal + "</aqua>)</gray>"
        ));
        if (!sender.equals(target)) {
            target.sendMessage(MM.deserialize(
                "<dark_gray>[<aqua>⚡</aqua>]</dark_gray> <green>Bạn vừa nhận được <yellow>+" + formattedAdded
                    + "</yellow> thời gian Auto-Fishing! <gray>(Tổng: <aqua>" + formattedTotal + "</aqua>)</gray>"
            ));
        }
    }
}

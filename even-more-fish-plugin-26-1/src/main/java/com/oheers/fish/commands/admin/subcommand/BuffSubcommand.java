package com.oheers.fish.commands.admin.subcommand;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oheers.fish.commands.BrigCommandUtils;
import com.oheers.fish.competition.buff.CompetitionBuff;
import com.oheers.fish.competition.buff.CompetitionBuffManager;
import com.oheers.fish.messages.EMFSingleMessage;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class BuffSubcommand {

    private final String name;

    public BuffSubcommand(@NotNull String name) {
        this.name = name;
    }

    public LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal(name)
            .then(view())
            .then(give())
            .then(clear());
    }

    private ArgumentBuilder<CommandSourceStack, ?> view() {
        return Commands.literal("view")
            .executes(ctx -> {
                Player player = BrigCommandUtils.requirePlayer(ctx);
                showBuffInfo(ctx.getSource().getSender(), player);
                return 1;
            })
            .then(
                Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                        List<Player> players = BrigCommandUtils.resolvePlayers(ctx, resolver);
                        if (players.isEmpty()) {
                            EMFSingleMessage.fromString("<red>Không tìm thấy người chơi.").send(ctx.getSource().getSender());
                            return 1;
                        }
                        showBuffInfo(ctx.getSource().getSender(), players.get(0));
                        return 1;
                    })
            );
    }

    private void showBuffInfo(CommandSender sender, Player target) {
        CompetitionBuff buff = CompetitionBuffManager.getInstance().getBuff(target.getUniqueId());
        if (buff == null) {
            EMFSingleMessage.fromString("<yellow>Người chơi <gold>" + target.getName() + "</gold> hiện không có buff cuộc thi nào.").send(sender);
            return;
        }

        EMFSingleMessage.fromString("<gold>===== THÔNG TIN BUFF: <yellow>" + target.getName() + " <gold>=====").send(sender);
        EMFSingleMessage.fromString("<gray> ○ <white>Thứ hạng đạt: <gold>Top " + buff.getRank()).send(sender);
        EMFSingleMessage.fromString("<gray> ○ <white>May mắn (Luck): <yellow>+" + String.format("%.0f", buff.getLuckBonus() * 100) + "%").send(sender);
        EMFSingleMessage.fromString("<gray> ○ <white>Tốc độ cắn câu (Lure): <aqua>+" + String.format("%.0f", buff.getLureBonus() * 100) + "%").send(sender);
        EMFSingleMessage.fromString("<gray> ○ <white>Nhân đôi cá (Double Catch): <light_purple>+" + String.format("%.0f", buff.getDoubleCatchChance() * 100) + "%").send(sender);
        EMFSingleMessage.fromString("<gray> ○ <white>Hệ số EXP: <green>x" + String.format("%.1f", buff.getExpMultiplier())).send(sender);
        EMFSingleMessage.fromString("<gray><i>Buff sẽ tự động xóa khi đợt cuộc thi tiếp theo bắt đầu.</i>").send(sender);
    }

    private ArgumentBuilder<CommandSourceStack, ?> give() {
        return Commands.literal("give")
            .then(
                Commands.argument("target", ArgumentTypes.player())
                    .then(
                        Commands.argument("luck", DoubleArgumentType.doubleArg(0.0))
                            .then(
                                Commands.argument("lure", DoubleArgumentType.doubleArg(0.0))
                                    .executes(ctx -> {
                                        PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                                        List<Player> players = BrigCommandUtils.resolvePlayers(ctx, resolver);
                                        if (players.isEmpty()) return 1;
                                        double luck = ctx.getArgument("luck", Double.class);
                                        double lure = ctx.getArgument("lure", Double.class);
                                        giveBuff(ctx.getSource().getSender(), players.get(0), luck, lure, 0.0, 1.0);
                                        return 1;
                                    })
                                    .then(
                                        Commands.argument("doubleChance", DoubleArgumentType.doubleArg(0.0, 1.0))
                                            .executes(ctx -> {
                                                PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                                                List<Player> players = BrigCommandUtils.resolvePlayers(ctx, resolver);
                                                if (players.isEmpty()) return 1;
                                                double luck = ctx.getArgument("luck", Double.class);
                                                double lure = ctx.getArgument("lure", Double.class);
                                                double doubleChance = ctx.getArgument("doubleChance", Double.class);
                                                giveBuff(ctx.getSource().getSender(), players.get(0), luck, lure, doubleChance, 1.0);
                                                return 1;
                                            })
                                            .then(
                                                Commands.argument("expMultiplier", DoubleArgumentType.doubleArg(1.0))
                                                    .executes(ctx -> {
                                                        PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                                                        List<Player> players = BrigCommandUtils.resolvePlayers(ctx, resolver);
                                                        if (players.isEmpty()) return 1;
                                                        double luck = ctx.getArgument("luck", Double.class);
                                                        double lure = ctx.getArgument("lure", Double.class);
                                                        double doubleChance = ctx.getArgument("doubleChance", Double.class);
                                                        double expMultiplier = ctx.getArgument("expMultiplier", Double.class);
                                                        giveBuff(ctx.getSource().getSender(), players.get(0), luck, lure, doubleChance, expMultiplier);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                    )
            );
    }

    private void giveBuff(CommandSender sender, Player target, double luck, double lure, double doubleChance, double exp) {
        CompetitionBuff buff = new CompetitionBuff(1, luck, lure, doubleChance, exp);
        CompetitionBuffManager.getInstance().setBuff(target.getUniqueId(), buff);
        EMFSingleMessage.fromString("<green>Đã cấp buff cuộc thi thành công cho <gold>" + target.getName() + "</gold>.").send(sender);
        EMFSingleMessage.fromString("<gold>★ <yellow>Quản trị viên đã cấp cho bạn <green>Buff Cuộc Thi</green>!").send(target);
    }

    private ArgumentBuilder<CommandSourceStack, ?> clear() {
        return Commands.literal("clear")
            .then(
                Commands.literal("all")
                    .executes(ctx -> {
                        CompetitionBuffManager.getInstance().clearAllBuffs();
                        EMFSingleMessage.fromString("<green>Đã xóa toàn bộ buff cuộc thi của tất cả người chơi.").send(ctx.getSource().getSender());
                        return 1;
                    })
            )
            .then(
                Commands.argument("target", ArgumentTypes.player())
                    .executes(ctx -> {
                        PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                        List<Player> players = BrigCommandUtils.resolvePlayers(ctx, resolver);
                        if (players.isEmpty()) return 1;
                        Player target = players.get(0);
                        CompetitionBuffManager.getInstance().clearBuff(target.getUniqueId());
                        EMFSingleMessage.fromString("<green>Đã xóa buff cuộc thi của <gold>" + target.getName() + "</gold>.").send(ctx.getSource().getSender());
                        return 1;
                    })
            );
    }

}

package com.oheers.fish.commands.admin.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oheers.fish.commands.BrigCommandUtils;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.CompetitionEntry;
import com.oheers.fish.competition.CompetitionType;
import com.oheers.fish.effects.AnimationManager;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.messages.EMFSingleMessage;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class EffectSubcommand {

    private final String name;

    public EffectSubcommand(@NotNull String name) {
        this.name = name;
    }

    public LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal(name)
            .then(
                Commands.literal("start")
                    .executes(ctx -> {
                        Competition comp = new Competition(10, CompetitionType.LARGEST_FISH);
                        AnimationManager.getInstance().playCompetitionStart(comp);
                        EMFSingleMessage.fromString("<green>Đang phát hiệu ứng Bắt đầu cuộc thi...").send(ctx.getSource().getSender());
                        return 1;
                    })
            )
            .then(
                Commands.literal("end")
                    .executes(ctx -> {
                        Player player = BrigCommandUtils.requirePlayer(ctx);
                        Competition comp = new Competition(10, CompetitionType.LARGEST_FISH);
                        CompetitionEntry entry = new CompetitionEntry(player.getUniqueId(), null, CompetitionType.LARGEST_FISH);
                        AnimationManager.getInstance().playCompetitionEnd(comp, List.of(entry));
                        EMFSingleMessage.fromString("<green>Đang phát hiệu ứng Kết thúc & Vinh danh quán quân...").send(player);
                        return 1;
                    })
            )
            .then(
                Commands.literal("rare_catch")
                    .executes(ctx -> {
                        Player player = BrigCommandUtils.requirePlayer(ctx);
                        Rarity sampleRarity = FishManager.getInstance().getRarityMap().values().stream().findFirst().orElse(null);
                        if (sampleRarity != null && !sampleRarity.getFishList().isEmpty()) {
                            Fish sampleFish = sampleRarity.getFishList().get(0);
                            AnimationManager.getInstance().playRareFishCatch(player, sampleFish);
                        } else {
                            EMFSingleMessage.fromString("<yellow>Không tìm thấy cá mẫu để phát hiệu ứng.").send(player);
                        }
                        return 1;
                    })
            )
            .then(
                Commands.literal("double_catch")
                    .executes(ctx -> {
                        Player player = BrigCommandUtils.requirePlayer(ctx);
                        Rarity sampleRarity = FishManager.getInstance().getRarityMap().values().stream().findFirst().orElse(null);
                        if (sampleRarity != null && !sampleRarity.getFishList().isEmpty()) {
                            Fish sampleFish = sampleRarity.getFishList().get(0);
                            AnimationManager.getInstance().playDoubleFishAnimation(player, sampleFish);
                        }
                        return 1;
                    })
            )
            .then(
                Commands.literal("hook_bite")
                    .executes(ctx -> {
                        Player player = BrigCommandUtils.requirePlayer(ctx);
                        AnimationManager.getInstance().playHookBiteParticles(player.getLocation().add(0, 0.5, 0));
                        EMFSingleMessage.fromString("<green>Đang phát hiệu ứng phao câu sóng nước...").send(player);
                        return 1;
                    })
            );
    }

}

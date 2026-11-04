package com.oheers.fish.commands.main.subcommand;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.simulator.SimulatorMessages;
import com.oheers.fish.fishing.simulator.presentation.UiProfile;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/** Self-service visual presets. No privilege to switch production Simulator on. */
@SuppressWarnings("UnstableApiUsage")
public final class HudSubcommand {
    private static final String PERMISSION="emf.simulator.preferences";
    public LiteralArgumentBuilder<CommandSourceStack> get() {
        LiteralArgumentBuilder<CommandSourceStack> root=Commands.literal("hud")
            .requires(source->source.getSender().hasPermission(PERMISSION))
            .executes(ctx->show(ctx.getSource().getSender()));
        for(UiProfile profile:UiProfile.values()) {
            root.then(Commands.literal(profile.name().toLowerCase(java.util.Locale.ROOT).replace('_','-'))
                .executes(ctx->set(ctx.getSource().getSender(),profile)));
        }
        return root;
    }
    private static int show(CommandSender sender) {
        if(!(sender instanceof Player player)) {
            SimulatorMessages.send(sender,"here-player-only");return 0;
        }
        var module=EvenMoreFish.getInstance().getSimulatorModule();
        module.prefetchHud(player.getUniqueId());
        if(!module.openHudGui(player))
            SimulatorMessages.send(player,"presentation-current",Map.of("profile",module.ownHudProfile(player.getUniqueId()).name()));
        return 1;
    }
    private static int set(CommandSender sender,UiProfile profile) {
        if(!(sender instanceof Player player)) {
            SimulatorMessages.send(sender,"here-player-only");return 0;
        }
        EvenMoreFish.getInstance().getSimulatorModule().setOwnHudProfile(player.getUniqueId(),profile);
        SimulatorMessages.send(player,"presentation-changed",Map.of("profile",profile.name()));
        return 1;
    }
}

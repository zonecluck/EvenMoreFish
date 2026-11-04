package com.oheers.fish.commands.admin.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.simulator.SimulatorSettings;
import com.oheers.fish.fishing.simulator.SimulatorMessages;
import com.oheers.fish.fishing.simulator.SimulatorModule;
import com.oheers.fish.fishing.simulator.encounter.RareEncounterDirector;
import com.oheers.fish.fishing.simulator.zones.SimulatorZoneResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Player;

import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public final class SimulatorSubcommand {
    private static final String STATUS = "emf.admin.simulator.status";
    private static final String ZONE = "emf.admin.simulator.zone";
    private static final String HERE = "emf.admin.simulator.inspect";
    private static final String RELOAD = "emf.admin.simulator.reload";
    private static final String MANAGE = "emf.admin.simulator.manage";

    public LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal("simulator")
            .executes(ctx -> { SimulatorMessages.send(ctx.getSource().getSender(), "help"); return 1; })
            .then(Commands.literal("status")
                .executes(ctx -> allowed(ctx.getSource().getSender(), STATUS) ? status(ctx.getSource().getSender()) : 0))
            .then(Commands.literal("doctor")
                .executes(ctx -> {
                    CommandSender sender=ctx.getSource().getSender();
                    if(!allowed(sender,STATUS))return 0;
                    return doctor(sender);
                }))
            .then(Commands.literal("trace")
                .executes(ctx -> {
                    var sender = ctx.getSource().getSender();
                    if (!allowed(sender, STATUS)) return 0;
                    var rows = module().recentTrace();
                    SimulatorMessages.send(sender, "trace-header", Map.of("count", Integer.toString(rows.size())));
                    rows.stream().skip(Math.max(0, rows.size() - 15)).forEach(row ->
                        SimulatorMessages.send(sender, "trace-row", Map.of("row", row)));
                    return 1;
                }))
            .then(Commands.literal("zone")
                .then(Commands.literal("list")
                    .executes(ctx -> list(ctx.getSource().getSender(), 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> list(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("info")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, suggestions) -> {
                            SimulatorSettings config = module().settings();
                            if (config != null) config.zones().forEach(zone -> suggestions.suggest(zone.id()));
                            return suggestions.buildFuture();
                        })
                        .executes(ctx -> info(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id"))))))
            .then(Commands.literal("here")
                .executes(ctx -> here(ctx.getSource().getSender())))
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    if (!allowed(ctx.getSource().getSender(), RELOAD)) return 0;
                    SimulatorMessages.send(ctx.getSource().getSender(),
                        module().reload() ? "reload-ok" : "reload-failed");
                    return 1;
                }))
            .then(Commands.literal("encounter")
                .then(Commands.literal("preview")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx,suggestions)->{
                            for(var kind:RareEncounterDirector.Kind.values())suggestions.suggest(kind.name().toLowerCase(java.util.Locale.ROOT));
                            return suggestions.buildFuture();
                        })
                        .executes(ctx -> encounterPreview(ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx,"type"))))))
            .then(Commands.literal("cinematic")
                .then(Commands.literal("preview")
                    .then(Commands.argument("preset", StringArgumentType.word())
                        .suggests((ctx,suggestions)->{
                            for(String tier:java.util.List.of("common","rare","epic","legendary")) suggestions.suggest(tier);
                            return suggestions.buildFuture();
                        })
                        .executes(ctx -> cinematicPreview(ctx.getSource().getSender(),
                            StringArgumentType.getString(ctx,"preset")))))
                .then(Commands.literal("stop")
                    .executes(ctx -> cinematicStop(ctx.getSource().getSender()))))
            .then(Commands.literal("effects")
                .then(Commands.literal("on")
                    .executes(ctx -> ownEffects(ctx.getSource().getSender(),true)))
                .then(Commands.literal("off")
                    .executes(ctx -> ownEffects(ctx.getSource().getSender(),false))))
            .then(Commands.literal("off")
                .executes(ctx -> {
                    if (!allowed(ctx.getSource().getSender(), MANAGE)) return 0;
                    module().setEmergencyOff(true);
                    SimulatorMessages.send(ctx.getSource().getSender(), "emergency-off");
                    return 1;
                }))
            .then(Commands.literal("on")
                .executes(ctx -> {
                    if (!allowed(ctx.getSource().getSender(), MANAGE)) return 0;
                    module().setEmergencyOff(false);
                    SimulatorMessages.send(ctx.getSource().getSender(), "emergency-on");
                    return 1;
                }));
    }

    private static int encounterPreview(CommandSender sender,String requested){
        if(!allowed(sender,"emf.admin.simulator.preview"))return 0;
        if(!(sender instanceof Player player)){
            SimulatorMessages.send(sender,"here-player-only");return 0;
        }
        RareEncounterDirector.Kind kind;
        try {kind=RareEncounterDirector.Kind.valueOf(requested.toUpperCase(java.util.Locale.ROOT));}
        catch(IllegalArgumentException error){SimulatorMessages.send(sender,"encounter-invalid");return 0;}
        boolean shown=module().previewOwnEncounter(player,kind);
        SimulatorMessages.send(sender,shown?"encounter-preview-ok":"encounter-preview-unavailable",
            Map.of("type",kind.name()));
        return shown?1:0;
    }

    private static int cinematicPreview(CommandSender sender,String tier){
        if(!allowed(sender,"emf.admin.simulator.preview"))return 0;
        if(!(sender instanceof Player player)){
            SimulatorMessages.send(sender,"here-player-only");return 0;
        }
        if(!java.util.List.of("common","rare","epic","legendary").contains(tier.toLowerCase(java.util.Locale.ROOT))){
            SimulatorMessages.send(sender,"cinematic-preview-invalid");return 0;
        }
        var hit=player.rayTraceBlocks(12,FluidCollisionMode.ALWAYS);
        if(hit==null || hit.getHitBlock()==null || hit.getHitBlock().getType()!=Material.WATER){
            SimulatorMessages.send(sender,"cinematic-preview-water");return 0;
        }
        var water=hit.getHitBlock().getLocation().add(.5,.6,.5);
        boolean started=module().premiumCinematicPreview(player,water,tier);
        SimulatorMessages.send(sender,started?"cinematic-preview-started":"cinematic-preview-unavailable",
            Map.of("preset",tier));
        return started?1:0;
    }
    private static int cinematicStop(CommandSender sender){
        if(!allowed(sender,"emf.admin.simulator.preview"))return 0;
        if(!(sender instanceof Player player)){
            SimulatorMessages.send(sender,"here-player-only");return 0;
        }
        module().stopOwnCinematic(player.getUniqueId());
        SimulatorMessages.send(sender,"cinematic-preview-stopped");return 1;
    }

    private static int ownEffects(CommandSender sender, boolean enabled) {
        if (!allowed(sender, "emf.admin.simulator.preview")) return 0;
        if (!(sender instanceof Player player)) {
            SimulatorMessages.send(sender, "here-player-only");return 0;
        }
        module().setOwnEffects(player.getUniqueId(),enabled);
        SimulatorMessages.send(sender, enabled ? "effects-on" : "effects-off");
        return 1;
    }

    private static boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        SimulatorMessages.send(sender, "no-permission");
        return false;
    }

    private static SimulatorModule module() { return EvenMoreFish.getInstance().getSimulatorModule(); }

    private static int status(CommandSender sender) {
        SimulatorModule mod = module();
        SimulatorSettings cfg = mod.settings();
        if (cfg == null) {
            SimulatorMessages.send(sender, "reload-failed");
            return 0;
        }
        SimulatorMessages.send(sender, "status", Map.of(
            "enabled", Boolean.toString(cfg.enabled()),
            "emergency", Boolean.toString(mod.emergencyOff()),
            "mode", "REGIONS_ONLY / NO_PRODUCTION_ENGINE",
            "revision", Integer.toString(cfg.revision()),
            "packets", Boolean.toString(mod.packetEventsPresent()),
            "hook", Boolean.toString(mod.hookBridgeSupported()),
            "probe", Boolean.toString(cfg.probe().enabled())
        ));
        SimulatorMessages.send(sender, "metrics", Map.of(
            "zones", Integer.toString(cfg.zones().size()),
            "pending", Integer.toString(mod.pendingCasts()),
            "sessions", Integer.toString(mod.activeProbes()),
            "catches", Integer.toString(mod.suppressedNativeCatches()),
            "bites", Integer.toString(mod.unexpectedNativeBites()),
            "reels", Integer.toString(mod.completedProbes())
        ));
        var ambient = mod.ambient();
        SimulatorMessages.send(sender, "ambient-status", Map.of(
            "active", Boolean.toString(ambient.running()),
            "agents", Integer.toString(ambient.agents()),
            "private", Integer.toString(ambient.privateAgents()),
            "viewers", Integer.toString(ambient.viewers()),
            "visible", Integer.toString(ambient.shown()),
            "water", Integer.toString(ambient.waterCells()),
            "queue", Integer.toString(ambient.waterQueued()),
            "sent", Long.toString(ambient.packetCount()),
            "deferred", Long.toString(ambient.deferredPackets())));
        SimulatorMessages.send(sender,"model-status",Map.of(
            "enabled",Boolean.toString(mod.customModelsEnabled()),
            "loaded",Integer.toString(mod.customModelsReadyViewers()),
            "swaps",Long.toString(mod.modelReplacements()),
            "accepted",Long.toString(mod.modelPackAudience().accepted()),
            "rejected",Long.toString(mod.modelPackAudience().rejected())));
        SimulatorMessages.send(sender,"schooling-status",Map.of(
            "enabled",Boolean.toString(ambient.schoolingEnabled()),
            "schools",Integer.toString(ambient.schools()),
            "members",Integer.toString(ambient.schoolMembers()),
            "decisions",Long.toString(ambient.schoolDecisions()),
            "checks",Long.toString(ambient.schoolNeighborChecks()),
            "budget",Long.toString(ambient.schoolBudgetHits()),
            "scatters",Long.toString(ambient.schoolScatters())));
        SimulatorMessages.send(sender,"ecosystem-status",Map.of(
            "enabled",Boolean.toString(ambient.ecosystemEnabled()),
            "cache",Integer.toString(ambient.environmentCache()),
            "samples",Long.toString(ambient.environmentReads()),
            "sample-drops",Long.toString(ambient.environmentDrops()),
            "sample-failures",Long.toString(ambient.environmentFailures()),
            "shy",Long.toString(ambient.personalityScatters()),
            "particles",Long.toString(ambient.waterFxParticles()),
            "fx-drops",Long.toString(ambient.waterFxDrops())));
        SimulatorMessages.send(sender,"encounter-status",Map.of(
            "enabled",Boolean.toString(ambient.encountersEnabled()),
            "active",Integer.toString(ambient.activeEncounters()),
            "spawned",Long.toString(ambient.encounterSpawns()),
            "observed",Long.toString(ambient.encounterObservations()),
            "expired",Long.toString(ambient.encounterExpires()),
            "completed",Long.toString(ambient.encounterCompleted()),
            "particles",Long.toString(ambient.encounterParticles()),
            "dropped",Long.toString(ambient.encounterFxDrops()),
            "failures",Long.toString(ambient.encounterFxFailures())));
        performance(sender,mod);
        SimulatorMessages.send(sender, "ai-status", Map.of(
            "enabled",Boolean.toString(mod.aiPreviewActive()),
            "active",Integer.toString(mod.activeAiSessions()),
            "success",Long.toString(mod.aiPreviewSuccesses()),
            "failure",Long.toString(mod.aiPreviewFailures()),
            "missed",Long.toString(mod.aiPreviewMisses())));
        SimulatorMessages.send(sender,"effects-status",Map.of(
            "enabled",Boolean.toString(mod.effectsEnabled()),
            "cinematics",Integer.toString(mod.activeCinematics()),
            "hud",Integer.toString(mod.activeHud()),
            "sent",Long.toString(mod.cinematicPackets()),
            "failed",Long.toString(mod.cinematicPacketFailures()),
            "pending",Integer.toString(mod.cinematicPendingDestroys()),
            "rejected",Long.toString(mod.cinematicRejected()),
            "cues",Long.toString(mod.effectCues())));
        SimulatorMessages.send(sender,"premium-status",Map.of(
            "enabled",Boolean.toString(mod.premiumCinematicsEnabled()),
            "scenes",Long.toString(mod.premiumCinematicsPlayed()),
            "cues",Long.toString(mod.premiumStageCues()),
            "trails",Long.toString(mod.premiumTrailEmissions()),
            "failed",Long.toString(mod.premiumCinematicCueFailures())));
        SimulatorMessages.send(sender,"presentation-status",Map.of(
            "sent",Long.toString(mod.hudMessagesSent()),
            "stale",Long.toString(mod.hudStaleDrops()),
            "priority",Long.toString(mod.hudPriorityDrops()),
            "io-failures",Long.toString(mod.hudStorageFailures())));
        SimulatorMessages.send(sender,"reward-status",Map.of(
            "enabled",Boolean.toString(mod.rewardBridgeEnabled()),
            "verified",Boolean.toString(cfg.rewardBridge().paperGatesVerified()),
            "legacy",Boolean.toString(com.oheers.fish.fishing.simulator.commit.SimulatorCatchService.legacyLootCompatibilityRequired()),
            "delivered",Long.toString(mod.rewardBridgeDelivered()),
            "rejected",Long.toString(mod.rewardBridgeRejected()),
            "failed",Long.toString(mod.rewardBridgeFailed())));
        return 1;
    }

    /** Source-level diagnostics, not an automatic G3/G5/G7 runtime certification. */
    private static int doctor(CommandSender sender) {
        SimulatorModule mod=module();
        SimulatorSettings cfg=mod.settings();
        if(cfg==null){SimulatorMessages.send(sender,"reload-failed");return 0;}
        SimulatorMessages.send(sender,"doctor-gates",Map.of(
            "packet",Boolean.toString(mod.ambient().backendAvailable()),
            "hook",Boolean.toString(mod.hookBridgeSupported()),
            "enabled",Boolean.toString(cfg.enabled()),
            "preview",Boolean.toString(mod.ambient().running()),
            "probe",Boolean.toString(cfg.probe().enabled()),
            "reward",Boolean.toString(cfg.rewardBridge().enabled()),
            "declared",Boolean.toString(cfg.rewardBridge().paperGatesVerified()),
            "emergency",Boolean.toString(mod.emergencyOff())));
        performance(sender,mod);
        SimulatorMessages.send(sender,"doctor-warning");
        return 1;
    }

    private static void performance(CommandSender sender, SimulatorModule mod) {
        var ambient=mod.ambient();
        SimulatorMessages.send(sender,"performance-status",Map.of(
            "pending",Integer.toString(ambient.pendingDestroys()),
            "peak",Integer.toString(ambient.packetPeak()),
            "failures",Long.toString(ambient.packetFailures()),
            "destroy-failures",Long.toString(ambient.destroyFailures()),
            "jobs",Long.toString(ambient.droppedWaterJobs()),
            "avg-ms",String.format(java.util.Locale.ROOT,"%.3f",ambient.averagePreviewNanos()/1_000_000.0),
            "max-ms",String.format(java.util.Locale.ROOT,"%.3f",ambient.peakPreviewNanos()/1_000_000.0),
            "slow",Long.toString(ambient.overTwoMsTicks()),
            "crashes",Long.toString(mod.catastrophicTickFailures()),
            "reason",mod.lastCriticalFailure()));
    }

    private static int list(CommandSender sender, int page) {
        if (!allowed(sender, ZONE)) return 0;
        SimulatorSettings cfg = module().settings();
        if (cfg == null) return 0;
        var ordered = cfg.zones().stream()
            .sorted(java.util.Comparator.comparing(SimulatorSettings.Zone::id)).toList();
        int pages = Math.max(1, (ordered.size() + 11) / 12);
        page = Math.min(page, pages);
        SimulatorMessages.send(sender, "zone-list", Map.of(
            "count", Integer.toString(ordered.size()),
            "page", Integer.toString(page), "pages", Integer.toString(pages)));
        ordered.stream().skip((page - 1L) * 12).limit(12)
            .forEach(zone -> SimulatorMessages.send(sender, "zone-item", variables(zone)));
        return 1;
    }

    private static int info(CommandSender sender, String id) {
        if (!allowed(sender, ZONE)) return 0;
        SimulatorSettings cfg = module().settings();
        if (cfg == null) return 0;
        SimulatorSettings.Zone zone = cfg.zones().stream()
            .filter(z -> z.id().equalsIgnoreCase(id)).findFirst().orElse(null);
        if (zone == null) SimulatorMessages.send(sender, "zone-not-found", Map.of("id", id));
        else SimulatorMessages.send(sender, "zone-info", variables(zone));
        return 1;
    }

    private static Map<String, String> variables(SimulatorSettings.Zone zone) {
        return Map.of(
            "id", zone.id(), "type", zone.type().name(), "world", zone.world(),
            "region", zone.regionId(), "priority", Integer.toString(zone.priority()),
            "enabled", Boolean.toString(zone.enabled()), "deny", Boolean.toString(zone.denySimulator())
        );
    }

    private static int here(CommandSender sender) {
        if (!allowed(sender, HERE)) return 0;
        if (!(sender instanceof Player player)) {
            SimulatorMessages.send(sender, "here-player-only");
            return 0;
        }
        SimulatorSettings cfg = module().settings();
        if (cfg == null) return 0;
        var loc = player.getLocation();
        SimulatorZoneResolver.Match match = module().zoneResolver().resolve(loc, cfg);
        SimulatorMessages.send(sender, "here", Map.of(
            "world", player.getWorld().getName(), "x", Integer.toString(loc.getBlockX()),
            "y", Integer.toString(loc.getBlockY()), "z", Integer.toString(loc.getBlockZ()),
            "zone", match.found() ? match.zone().id() : "NONE",
            "denied", Boolean.toString(match.denied())
        ));
        return 1;
    }
}

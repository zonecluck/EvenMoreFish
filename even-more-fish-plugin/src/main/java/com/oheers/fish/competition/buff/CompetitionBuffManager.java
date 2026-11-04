package com.oheers.fish.competition.buff;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.api.Logging;
import com.oheers.fish.api.competition.ICompetitionBuffManager;
import com.oheers.fish.competition.CompetitionEntry;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.messages.ConfigMessage;
import com.oheers.fish.messages.EMFSingleMessage;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CompetitionBuffManager implements ICompetitionBuffManager {

    private static CompetitionBuffManager instance;
    private final Map<UUID, CompetitionBuff> activeBuffs = new ConcurrentHashMap<>();

    private CompetitionBuffManager() {}

    public static @NotNull CompetitionBuffManager getInstance() {
        if (instance == null) {
            instance = new CompetitionBuffManager();
        }
        return instance;
    }

    @Override
    public boolean hasBuff(@NotNull UUID uuid) {
        return activeBuffs.containsKey(uuid);
    }

    public @Nullable CompetitionBuff getBuff(@NotNull UUID uuid) {
        return activeBuffs.get(uuid);
    }

    public void setBuff(@NotNull UUID uuid, @NotNull CompetitionBuff buff) {
        activeBuffs.put(uuid, buff);
    }

    @Override
    public double getLuckBonus(@NotNull UUID uuid) {
        CompetitionBuff buff = activeBuffs.get(uuid);
        return buff != null ? buff.getLuckBonus() : 0.0;
    }

    @Override
    public double getLureBonus(@NotNull UUID uuid) {
        CompetitionBuff buff = activeBuffs.get(uuid);
        return buff != null ? buff.getLureBonus() : 0.0;
    }

    @Override
    public double getDoubleCatchChance(@NotNull UUID uuid) {
        CompetitionBuff buff = activeBuffs.get(uuid);
        return buff != null ? buff.getDoubleCatchChance() : 0.0;
    }

    @Override
    public double getExpMultiplier(@NotNull UUID uuid) {
        CompetitionBuff buff = activeBuffs.get(uuid);
        return buff != null ? buff.getExpMultiplier() : 1.0;
    }

    @Override
    public void clearAllBuffs() {
        if (activeBuffs.isEmpty()) {
            return;
        }
        int count = activeBuffs.size();
        activeBuffs.clear();
        Logging.debug("Reset " + count + " competition winner buffs for new competition.");
    }

    @Override
    public void clearBuff(@NotNull UUID uuid) {
        activeBuffs.remove(uuid);
    }

    public @NotNull Map<UUID, CompetitionBuff> getActiveBuffs() {
        return Collections.unmodifiableMap(activeBuffs);
    }

    /**
     * Applies competition winner buffs to the winners based on leaderboard ranking.
     *
     * @param entries The leaderboard entries sorted from 1st place downwards.
     */
    public void applyCompetitionRewards(@NotNull List<CompetitionEntry> entries) {
        Section buffSection = MainConfig.getInstance().getConfig().getSection("competition-buffs");
        if (buffSection == null || !buffSection.getBoolean("enabled", true)) {
            return;
        }

        Section ranksSection = buffSection.getSection("ranks");
        if (ranksSection == null) {
            return;
        }

        int rank = 1;
        for (CompetitionEntry entry : entries) {
            UUID playerUuid = entry.getPlayer();
            Section rankConfig = ranksSection.getSection(String.valueOf(rank));
            if (rankConfig == null) {
                // Check for participation buff
                rankConfig = ranksSection.getSection("participation");
            }

            if (rankConfig != null) {
                double luck = rankConfig.getDouble("luck", 0.0);
                double lure = rankConfig.getDouble("lure", 0.0);
                double doubleCatch = rankConfig.getDouble("double-catch", 0.0);
                double expMultiplier = rankConfig.getDouble("exp-multiplier", 1.0);

                if (luck > 0 || lure > 0 || doubleCatch > 0 || expMultiplier > 1.0) {
                    CompetitionBuff buff = new CompetitionBuff(rank, luck, lure, doubleCatch, expMultiplier);
                    activeBuffs.put(playerUuid, buff);

                    Player onlinePlayer = Bukkit.getPlayer(playerUuid);
                    if (onlinePlayer != null) {
                        sendBuffNotification(onlinePlayer, buff);
                    }
                }
            }

            rank++;
        }
    }

    private void sendBuffNotification(@NotNull Player player, @NotNull CompetitionBuff buff) {
        String msg = MainConfig.getInstance().getConfig().getString(
                "competition-buffs.notification-message",
                "<gold>★ <yellow>Bạn nhận được <green>Buff Quán Quân Cuộc Thi (Top {rank})<yellow>: <aqua>+{lure}% Tốc độ cắn câu</aqua>, <gold>+{luck}% May mắn</gold>, <light_purple>+{double}% Nhân đôi cá</light_purple>! <gray>(Reset ở cuộc thi tiếp theo)</gray>"
        );

        EMFSingleMessage singleMessage = EMFSingleMessage.fromString(msg);
        singleMessage.setVariable("{rank}", String.valueOf(buff.getRank()));
        singleMessage.setVariable("{luck}", String.format("%.0f", buff.getLuckBonus() * 100));
        singleMessage.setVariable("{lure}", String.format("%.0f", buff.getLureBonus() * 100));
        singleMessage.setVariable("{double}", String.format("%.0f", buff.getDoubleCatchChance() * 100));
        singleMessage.setVariable("{exp}", String.format("%.1f", buff.getExpMultiplier()));

        singleMessage.send(player);
    }
}

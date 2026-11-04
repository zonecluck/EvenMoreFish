package com.oheers.fish.placeholders.impl.competition;

import com.oheers.fish.competition.buff.CompetitionBuff;
import com.oheers.fish.competition.buff.CompetitionBuffManager;
import com.oheers.fish.placeholders.abstracted.EMFPlaceholder;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompetitionBuffPlaceholder implements EMFPlaceholder {

    @Override
    public boolean shouldProcess(@NotNull String identifier) {
        return identifier.startsWith("competition_buff_") || identifier.equalsIgnoreCase("competition_has_buff");
    }

    @Override
    public @Nullable String parsePAPI(@Nullable OfflinePlayer player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        CompetitionBuff buff = CompetitionBuffManager.getInstance().getBuff(player.getUniqueId());

        if (identifier.equalsIgnoreCase("competition_has_buff")) {
            return String.valueOf(buff != null);
        }

        if (buff == null) {
            return switch (identifier.toLowerCase()) {
                case "competition_buff_rank" -> "0";
                case "competition_buff_luck", "competition_buff_lure", "competition_buff_double" -> "0%";
                case "competition_buff_exp" -> "1.0x";
                default -> "";
            };
        }

        return switch (identifier.toLowerCase()) {
            case "competition_buff_rank" -> String.valueOf(buff.getRank());
            case "competition_buff_luck" -> String.format("%.0f%%", buff.getLuckBonus() * 100);
            case "competition_buff_lure" -> String.format("%.0f%%", buff.getLureBonus() * 100);
            case "competition_buff_double" -> String.format("%.0f%%", buff.getDoubleCatchChance() * 100);
            case "competition_buff_exp" -> String.format("%.1fx", buff.getExpMultiplier());
            default -> "";
        };
    }
}

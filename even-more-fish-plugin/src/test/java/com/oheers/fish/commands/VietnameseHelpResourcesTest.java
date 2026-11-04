package com.oheers.fish.commands;

import dev.dejvokep.boostedyaml.YamlDocument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VietnameseHelpResourcesTest {
    @Test void playerHelpIsVietnameseAndDistinctFromSimulatorAndAdminHelp() throws Exception {
        for (String locale : new String[]{"vi", "vn", "en"}) {
            try (var resource = getClass().getResourceAsStream("/locales/messages_" + locale + ".yml")) {
                assertNotNull(resource);
                var yaml = YamlDocument.create(resource);
                if (!locale.equals("en")) assertTrue(yaml.getString("help-general.help").contains("câu cá"));
                assertFalse(yaml.getString("help-general.help").contains("Simulator"));
                assertNotEquals(yaml.getString("help-general.title"), yaml.getString("help-admin.title"));
                String header = yaml.getString("leaderboard.active-header");
                assertTrue(header.contains("{competition}")); assertTrue(header.contains("{type}"));
                assertTrue(header.contains("{time-left}"));
                assertTrue(yaml.getString("leaderboard.personal-rank").contains("{position}"));
            }
        }
    }
}

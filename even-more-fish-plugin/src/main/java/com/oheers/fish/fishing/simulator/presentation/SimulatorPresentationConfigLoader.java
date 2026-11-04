package com.oheers.fish.fishing.simulator.presentation;

import com.oheers.fish.EvenMoreFish;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Rejects invalid reloads before changing the live snapshot. */
public final class SimulatorPresentationConfigLoader {
    private final EvenMoreFish plugin;
    public SimulatorPresentationConfigLoader(EvenMoreFish plugin) { this.plugin=plugin; }
    public SimulatorPresentationSettings readCandidate() {
        File file=new File(plugin.getDataFolder(), "simulator/presentation.yml");
        try {
            if(!file.isFile()) plugin.saveResource("simulator/presentation.yml", false);
            YamlConfiguration yaml=new YamlConfiguration();
            yaml.load(file);
            return parse(yaml);
        } catch(IOException | InvalidConfigurationException | RuntimeException error) {
            plugin.getLogger().severe("[Simulator] Invalid simulator/presentation.yml; old snapshot retained: " + error.getMessage());
            return null;
        }
    }
    public static SimulatorPresentationSettings parse(YamlConfiguration yaml) {
        if(yaml.getInt("config-version",-1)!=1) throw new IllegalArgumentException("config-version must equal 1");
        int interval=number(yaml,"actionbar.minimum-interval-ticks",5,1,20);
        int bite=number(yaml,"actionbar.bite-ttl-ticks",8,1,40);
        int result=number(yaml,"actionbar.result-ttl-ticks",35,5,100);
        if(!yaml.isBoolean("enabled") || !yaml.isBoolean("actionbar.show-interested-hint"))
            throw new IllegalArgumentException("enabled/show-interested-hint must be boolean");
        String fallback=yaml.getString("profiles.default");
        if(fallback==null) throw new IllegalArgumentException("profiles.default is missing");
        UiProfile profile=UiProfile.parse(fallback);
        return new SimulatorPresentationSettings(yaml.getBoolean("enabled"),interval,bite,result,
            yaml.getBoolean("actionbar.show-interested-hint"),profile);
    }
    private static int number(YamlConfiguration yaml,String key,int fallback,int min,int max) {
        if(!yaml.isInt(key)) throw new IllegalArgumentException(key+" must be integer");
        int value=yaml.getInt(key,fallback);
        if(value<min || value>max) throw new IllegalArgumentException(key+" must be " + min + ".." + max);
        return value;
    }
}

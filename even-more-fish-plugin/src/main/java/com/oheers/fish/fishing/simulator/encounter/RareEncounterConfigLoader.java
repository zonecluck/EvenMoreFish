package com.oheers.fish.fishing.simulator.encounter;

import com.oheers.fish.EvenMoreFish;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Candidate-only YAML loader; caller installs it atomically together with other simulator configs. */
public final class RareEncounterConfigLoader {
    private final EvenMoreFish plugin;
    public RareEncounterConfigLoader(EvenMoreFish plugin){this.plugin=plugin;}
    public RareEncounterSettings readCandidate(){
        File file=new File(plugin.getDataFolder(),"simulator/encounters.yml");
        try {
            if(!file.isFile())plugin.saveResource("simulator/encounters.yml",false);
            YamlConfiguration yaml=new YamlConfiguration();yaml.load(file);
            return parse(yaml);
        } catch(IOException | InvalidConfigurationException | RuntimeException error) {
            plugin.getLogger().severe("[Simulator] Invalid simulator/encounters.yml; retaining live config: "+error.getMessage());
            return null;
        }
    }
    public static RareEncounterSettings parse(YamlConfiguration y){
        if(!y.isInt("config-version") || y.getInt("config-version")!=1)
            throw new IllegalArgumentException("config-version must be 1");
        var limits=new RareEncounterSettings.Limits(
            integer(y,"limits.scan-interval-ticks",5,200),
            integer(y,"limits.candidates-per-scan",1,160),
            integer(y,"limits.max-active",1,64),
            integer(y,"limits.max-per-zone",1,64),
            integer(y,"limits.life-ticks",40,7200),
            integer(y,"limits.zone-cooldown-ticks",0,72000),
            integer(y,"limits.viewer-cooldown-ticks",0,72000),
            integer(y,"visuals.interval-ticks",4,100),
            integer(y,"visuals.max-particles-per-tick",1,128),
            integer(y,"visuals.max-particles-per-viewer",1,128),
            decimal(y,"visuals.reveal-radius",3,32),
            decimal(y,"discovery.activation-chance",0,1));
        if(!y.isList("discovery.eligible-rarities"))
            throw new IllegalArgumentException("discovery.eligible-rarities must be a list");
        Set<String> rarities=new HashSet<>();
        for(Object raw:y.getList("discovery.eligible-rarities")) {
            if(!(raw instanceof String s) || s.isBlank() || s.length()>64)
                throw new IllegalArgumentException("Invalid eligible rarity entry");
            rarities.add(s.toLowerCase(Locale.ROOT));
        }
        Map<RareEncounterDirector.Kind,RareEncounterSettings.Profile> profiles=new HashMap<>();
        for(var kind:RareEncounterDirector.Kind.values()){
            String prefix="types."+kind.name();
            profiles.put(kind,new RareEncounterSettings.Profile(
                bool(y,prefix+".enabled"),
                integer(y,prefix+".weight",0,1000),
                bool(y,prefix+".night-only"),
                bool(y,prefix+".deep-only"),
                bool(y,prefix+".storm-only"),
                integer(y,prefix+".min-school-size",1,4)));
        }
        ConfigurationSection section=y.getConfigurationSection("discovery.fish-overrides");
        if(section==null)throw new IllegalArgumentException("discovery.fish-overrides must be a mapping");
        Map<String,RareEncounterDirector.Kind> overrides=new HashMap<>();
        for(String key:section.getKeys(false)){
            if(!section.isString(key))throw new IllegalArgumentException("FishKey override "+key+" must be text");
            try {overrides.put(key,RareEncounterDirector.Kind.valueOf(section.getString(key).trim().toUpperCase(Locale.ROOT)));}
            catch(IllegalArgumentException error){throw new IllegalArgumentException("Unknown encounter type for "+key);}
        }
        return new RareEncounterSettings(bool(y,"enabled"),limits,rarities,profiles,overrides);
    }
    private static boolean bool(YamlConfiguration y,String key){
        if(!y.isBoolean(key))throw new IllegalArgumentException(key+" must be a boolean");
        return y.getBoolean(key);
    }
    private static int integer(YamlConfiguration y,String key,int min,int max){
        if(!y.isInt(key))throw new IllegalArgumentException(key+" must be an integer");
        int value=y.getInt(key);
        if(value<min||value>max)throw new IllegalArgumentException(key+" must be "+min+".."+max);
        return value;
    }
    private static double decimal(YamlConfiguration y,String key,double min,double max){
        if(!y.isInt(key)&&!y.isDouble(key))throw new IllegalArgumentException(key+" must be numeric");
        double value=y.getDouble(key,Double.NaN);
        if(!Double.isFinite(value)||value<min||value>max)
            throw new IllegalArgumentException(key+" must be "+min+".."+max);
        return value;
    }
}

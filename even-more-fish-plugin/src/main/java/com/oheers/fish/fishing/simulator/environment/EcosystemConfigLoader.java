package com.oheers.fish.fishing.simulator.environment;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.simulator.behavior.FishPersonality;
import com.oheers.fish.fishing.simulator.waterfx.SurfaceFxService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Atomic candidate parsing: malformed ecosystem.yml cannot modify live state. */
public final class EcosystemConfigLoader {
    private final EvenMoreFish plugin;
    public EcosystemConfigLoader(EvenMoreFish plugin){this.plugin=plugin;}
    public EcosystemSettings readCandidate() {
        File file=new File(plugin.getDataFolder(),"simulator/ecosystem.yml");
        try {
            if(!file.isFile())plugin.saveResource("simulator/ecosystem.yml",false);
            YamlConfiguration yaml=new YamlConfiguration();yaml.load(file);
            return parse(yaml);
        } catch(IOException | InvalidConfigurationException | RuntimeException error) {
            plugin.getLogger().severe("[Simulator] Invalid simulator/ecosystem.yml; retaining live snapshot: "+error.getMessage());
            return null;
        }
    }
    public static EcosystemSettings parse(YamlConfiguration yaml) {
        if(!yaml.isInt("config-version") || yaml.getInt("config-version")!=1)
            throw new IllegalArgumentException("config-version must be 1");
        return new EcosystemSettings(bool(yaml,"enabled"),
            bool(yaml,"environment.enabled"),bool(yaml,"personality.enabled"),
            bool(yaml,"water-fx.enabled"),
            new EnvironmentSampler.Limits(integer(yaml,"environment.max-samples-per-tick",4,1,32),
                integer(yaml,"environment.cache-entries",256,16,2048),
                integer(yaml,"environment.cache-ttl-ticks",200,20,2400)),
            integer(yaml,"environment.depth-scan-blocks",8,2,16),
            decimal(yaml,"personality.shy-disturb-radius",5,1,16),
            profiles(yaml,"personality.rarity-profiles"),
            profiles(yaml,"personality.fish-overrides"),
            new SurfaceFxService.Limits(integer(yaml,"water-fx.interval-ticks",10,2,80),
                integer(yaml,"water-fx.max-particles-per-tick",32,1,128),
                integer(yaml,"water-fx.max-particles-per-viewer",8,1,32),
                integer(yaml,"water-fx.candidates-per-viewer",24,1,160),
                decimal(yaml,"water-fx.radius",16,3,32)));
    }
    private static Map<String,FishPersonality> profiles(YamlConfiguration yaml,String root) {
        ConfigurationSection section=yaml.getConfigurationSection(root);
        if(section==null)throw new IllegalArgumentException(root+" must be a mapping");
        Map<String,FishPersonality> result=new HashMap<>();
        for(String key:section.getKeys(false)) {
            if(key.isBlank()||key.length()>128||!section.isString(key))
                throw new IllegalArgumentException("Invalid profile "+root+"."+key);
            try {result.put(key,FishPersonality.valueOf(section.getString(key).trim().toUpperCase(Locale.ROOT)));}
            catch(IllegalArgumentException error){throw new IllegalArgumentException("Unknown personality for "+root+"."+key);}
        }
        if(result.size()>256)throw new IllegalArgumentException(root+" exceeds 256 entries");
        return Map.copyOf(result);
    }
    private static boolean bool(YamlConfiguration y,String key) {
        if(!y.isBoolean(key))throw new IllegalArgumentException(key+" must be a boolean");
        return y.getBoolean(key);
    }
    private static int integer(YamlConfiguration y,String key,int fallback,int min,int max) {
        if(!y.isInt(key))throw new IllegalArgumentException(key+" must be an integer");
        int n=y.getInt(key,fallback);if(n<min||n>max)throw new IllegalArgumentException(key+" must be "+min+".."+max);
        return n;
    }
    private static double decimal(YamlConfiguration y,String key,double fallback,double min,double max) {
        if(!(y.isInt(key)||y.isDouble(key)))throw new IllegalArgumentException(key+" must be numeric");
        double n=y.getDouble(key,fallback);
        if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException(key+" must be "+min+".."+max);
        return n;
    }
}

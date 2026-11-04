package com.oheers.fish.fishing.simulator.model;

import com.oheers.fish.EvenMoreFish;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Reject malformed custom model config; caller applies this atomically with other Simulator snapshots. */
public final class FishModelConfigLoader {
    private final EvenMoreFish plugin;
    public FishModelConfigLoader(EvenMoreFish plugin){this.plugin=plugin;}
    public FishModelSettings readCandidate(){
        File file=new File(plugin.getDataFolder(),"simulator/models.yml");
        try {
            if(!file.isFile())plugin.saveResource("simulator/models.yml",false);
            YamlConfiguration yaml=new YamlConfiguration();yaml.load(file);
            return parse(yaml);
        }catch(IOException | InvalidConfigurationException | RuntimeException error){
            plugin.getLogger().severe("[Simulator] Invalid simulator/models.yml; retaining live snapshot: "+error.getMessage());
            return null;
        }
    }
    public static FishModelSettings parse(YamlConfiguration y){
        if(!y.isInt("config-version") || y.getInt("config-version")!=1)
            throw new IllegalArgumentException("config-version must be 1");
        if(!y.isBoolean("enabled"))throw new IllegalArgumentException("enabled must be boolean");
        boolean enabled=y.getBoolean("enabled");
        String expected=y.getString("resource-pack.id","").trim();
        UUID id=null;
        if (!expected.isEmpty())try{id=UUID.fromString(expected);}
        catch(IllegalArgumentException bad){throw new IllegalArgumentException("resource-pack.id must be UUID");}
        if(enabled && id==null)throw new IllegalArgumentException("resource-pack.id is required");
        return new FishModelSettings(enabled,id,profiles(y,"fish-overrides"),profiles(y,"rarity-overrides"));
    }
    private static Map<String,FishModelSettings.Profile> profiles(YamlConfiguration y,String root){
        ConfigurationSection section=y.getConfigurationSection(root);
        if(section==null)throw new IllegalArgumentException(root+" must be a mapping");
        if(section.getKeys(false).size()>256)throw new IllegalArgumentException(root+" exceeds 256 entries");
        Map<String,FishModelSettings.Profile> result=new HashMap<>();
        for(String name:section.getKeys(false)){
            String key=name.toLowerCase(Locale.ROOT);
            if(key.isBlank() || name.length()>128 || key.contains("\n") || key.contains("\r") ||
                (root.equals("fish-overrides") && (key.indexOf(':')<1 || key.endsWith(":"))) ||
                (root.equals("rarity-overrides") && key.contains(":")))
                throw new IllegalArgumentException("Invalid "+root+" key: "+name);
            String path=root+"."+name;
            ConfigurationSection entry=section.getConfigurationSection(name);
            if(entry==null)throw new IllegalArgumentException(path+" must be mapping");
            String material=requiredString(entry,"material").toUpperCase(Locale.ROOT);
            Material item=Material.getMaterial(material);
            if(item==null || !item.isItem() || item.isAir())throw new IllegalArgumentException(path+".material invalid item: "+material);
            String model=requiredString(entry,"item-model");
            if(!entry.isDouble("scale") && !entry.isInt("scale"))
                throw new IllegalArgumentException(path+".scale must be number");
            double scale=entry.getDouble("scale",Double.NaN);
            if(!Double.isFinite(scale) || scale<0.05 || scale>3)throw new IllegalArgumentException(path+".scale outside 0.05..3");
            if(!entry.isInt("interpolation-ticks"))
                throw new IllegalArgumentException(path+".interpolation-ticks must be integer");
            int interpolation=entry.getInt("interpolation-ticks");
            result.put(key,new FishModelSettings.Profile(material,model,(float)scale,interpolation));
        }
        return result;
    }
    private static String requiredString(ConfigurationSection y,String path){
        if(!y.isString(path) || y.getString(path).isBlank())
            throw new IllegalArgumentException(path+" must be nonblank string");
        return y.getString(path).trim();
    }
}

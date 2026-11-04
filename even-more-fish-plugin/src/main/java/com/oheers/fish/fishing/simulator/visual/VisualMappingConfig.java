package com.oheers.fish.fishing.simulator.visual;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.population.FishVisualModel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import java.io.IOException;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Fail-closed mapping: malformed overrides become vanilla fish, never invisible paid models. */
public final class VisualMappingConfig {
    private final FishVisualModel fallback;
    private final Map<String,FishVisualModel> rarityModels;
    private final Map<String,FishVisualModel> fishModels;
    private VisualMappingConfig(FishVisualModel fallback,Map<String,FishVisualModel> rarities,Map<String,FishVisualModel> fish){
        this.fallback=fallback;rarityModels=Map.copyOf(rarities);fishModels=Map.copyOf(fish);
    }
    public static VisualMappingConfig load(EvenMoreFish plugin) {
        File file=new File(plugin.getDataFolder(),"simulator/visuals.yml");
        if(!file.isFile())plugin.saveResource("simulator/visuals.yml",false);
        YamlConfiguration yaml=new YamlConfiguration();
        try {
            yaml.load(file);
            if (yaml.getInt("config-version",-1) != 1) {
                throw new IllegalArgumentException("config-version must be 1");
            }
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException error) {
            plugin.getLogger().warning("[Simulator] Invalid simulator/visuals.yml: " + error.getMessage()
                + "; using safe vanilla COD model for all preview fish");
            return new VisualMappingConfig(FishVisualModel.COD,Map.of(),Map.of());
        }
        FishVisualModel fallback=parse(yaml.getString("visuals.fallback-model","COD"),FishVisualModel.COD);
        Map<String,FishVisualModel> rarities=new HashMap<>(),fish=new HashMap<>();
        if(yaml.isConfigurationSection("visuals.rarity-overrides")){
            for(String id:yaml.getConfigurationSection("visuals.rarity-overrides").getKeys(false)) {
                String model=yaml.getString("visuals.rarity-overrides."+id+".model","COD");
                rarities.put(id.toLowerCase(Locale.ROOT),parse(model,fallback));
            }
        }
        if(yaml.isConfigurationSection("visuals.fish-overrides")){
            for(String id:yaml.getConfigurationSection("visuals.fish-overrides").getKeys(false)) {
                String path="visuals.fish-overrides."+id;
                // ITEM_DISPLAY custom-model mapping is intentionally NOT enabled before packet metadata validation.
                String renderer=yaml.getString(path+".renderer","VANILLA_FISH");
                if(!renderer.equalsIgnoreCase("VANILLA_FISH")) {
                    plugin.getLogger().warning("[Simulator] " + id + ": custom ITEM_DISPLAY needs G5 model metadata validation; using vanilla fallback");
                }
                fish.put(id.toLowerCase(Locale.ROOT),parse(yaml.getString(path+".model","COD"),fallback));
            }
        }
        return new VisualMappingConfig(fallback,rarities,fish);
    }
    private static FishVisualModel parse(String name,FishVisualModel fallback){
        try{return FishVisualModel.valueOf(name.toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException error){return fallback;}
    }
    public FishVisualModel resolve(FishKey key){
        FishVisualModel specific=fishModels.get((key.rarityId()+":"+key.fishId()).toLowerCase(Locale.ROOT));
        if(specific!=null)return specific;
        FishVisualModel rarity=rarityModels.get(key.rarityId().toLowerCase(Locale.ROOT));
        if(rarity!=null)return rarity;
        String name=key.fishId().toLowerCase(Locale.ROOT);
        if(name.contains("puffer"))return FishVisualModel.PUFFERFISH;
        if(name.contains("tropical"))return FishVisualModel.TROPICAL_FISH;
        if(name.contains("salmon"))return FishVisualModel.SALMON;
        return fallback;
    }
}

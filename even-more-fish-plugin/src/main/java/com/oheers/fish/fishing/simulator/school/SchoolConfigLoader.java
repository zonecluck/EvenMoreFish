package com.oheers.fish.fishing.simulator.school;

import com.oheers.fish.EvenMoreFish;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Atomic candidate parsing: malformed schooling.yml cannot replace an active config snapshot. */
public final class SchoolConfigLoader {
    private final EvenMoreFish plugin;
    public SchoolConfigLoader(EvenMoreFish plugin){this.plugin=plugin;}
    public SchoolSettings readCandidate(){
        File file=new File(plugin.getDataFolder(),"simulator/schooling.yml");
        try {
            if(!file.isFile())plugin.saveResource("simulator/schooling.yml",false);
            YamlConfiguration yaml=new YamlConfiguration();
            yaml.load(file);
            return parse(yaml);
        } catch(IOException | InvalidConfigurationException | RuntimeException error){
            plugin.getLogger().severe("[Simulator] Invalid simulator/schooling.yml; keeping old config: "+error.getMessage());
            return null;
        }
    }
    public static SchoolSettings parse(YamlConfiguration y){
        require(y.isInt("config-version") && y.getInt("config-version")==1,
            "config-version must equal 1");
        require(y.isBoolean("enabled"),"enabled must be a boolean");
        SchoolSettings settings=new SchoolSettings(y.getBoolean("enabled"),
            integer(y,"limits.max-schools",32,1,64),
            integer(y,"limits.max-schools-per-zone",6,1,64),
            integer(y,"limits.max-members",6,2,8),
            integer(y,"limits.decisions-per-move",36,1,160),
            integer(y,"limits.neighbor-checks-per-move",144,4,640),
            integer(y,"limits.recruits-per-move",8,0,32),
            integer(y,"movement.decision-interval-ticks",6,1,100),
            integer(y,"movement.scatter-ticks",32,1,200),
            integer(y,"movement.reform-ticks",28,1,200),
            decimal(y,"movement.join-radius",6,1,12),
            decimal(y,"movement.roam-radius",12,2,24),
            decimal(y,"movement.formation-gap",1.3,.5,4),
            decimal(y,"movement.leader-speed",.12,.02,.4),
            decimal(y,"movement.follower-speed",.14,.02,.5));
        require(settings.neighborChecksPerMove()>=settings.decisionsPerMove(),
            "neighbor-checks-per-move must be >= decisions-per-move");
        return settings;
    }
    private static int integer(YamlConfiguration y,String key,int fallback,int min,int max){
        require(y.isInt(key),key+" must be an integer");
        int v=y.getInt(key,fallback);
        require(v>=min && v<=max,key+" must be "+min+".."+max);
        return v;
    }
    private static double decimal(YamlConfiguration y,String key,double fallback,double min,double max){
        require(y.isInt(key)||y.isDouble(key),key+" must be a number");
        double v=y.getDouble(key,fallback);
        require(Double.isFinite(v)&&v>=min&&v<=max,key+" must be "+min+".."+max);
        return v;
    }
    private static void require(boolean valid,String message){if(!valid)throw new IllegalArgumentException(message);}
}

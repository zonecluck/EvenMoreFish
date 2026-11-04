import com.oheers.fish.fishing.simulator.model.*;
import com.oheers.fish.fishing.simulator.population.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;

/** Strict config smoke, compiled against local Bukkit API stubs only. */
public final class SimulatorPremiumPhase15ConfigSmoke {
    private static final UUID PACK = UUID.randomUUID();
    private static YamlConfiguration valid() {
        var config = new YamlConfiguration();
        config.set("config-version",1);
        config.set("enabled",true);
        config.set("resource-pack.id",PACK.toString());
        var fish = new ConfigurationSection();
        var golden = new ConfigurationSection();
        golden.set("material","PAPER");
        golden.set("item-model","ledat:fish/golden_koi");
        golden.set("scale",0.75);
        golden.set("interpolation-ticks",2);
        fish.set("Legendary:Golden Koi",golden);
        config.set("fish-overrides",fish);
        config.set("rarity-overrides",new ConfigurationSection());
        return config;
    }
    private static void reject(YamlConfiguration config,String why) {
        boolean failed = false;
        try {FishModelConfigLoader.parse(config);}catch(IllegalArgumentException e){failed=true;}
        if(!failed)throw new AssertionError("accepted invalid model config: "+why);
    }
    public static void main(String[] args) {
        var valid=valid();
        var parsed=FishModelConfigLoader.parse(valid);
        var key=new FishKey("Legendary","Golden Koi");
        var spec=parsed.profile(key).withFallback(FishVisualModel.SALMON);
        assert spec.isCustom() && spec.itemModel().equals("ledat:fish/golden_koi");
        var y=valid(); y.set("enabled","true"); reject(y,"enabled type");
        y=valid();y.set("resource-pack.id","not uuid");reject(y,"pack uuid");
        y=valid();y.set("resource-pack.id","");reject(y,"missing pack");
        y=valid();y.set("config-version",2);reject(y,"version");
        y=valid();y.set("fish-overrides",null);reject(y,"null mapping");
        y=valid();y.getConfigurationSection("fish-overrides").getConfigurationSection("Legendary:Golden Koi")
            .set("material","WATER");reject(y,"block material");
        y=valid();y.getConfigurationSection("fish-overrides").getConfigurationSection("Legendary:Golden Koi")
            .set("scale",Double.NaN);reject(y,"NaN scale");
        y=valid();y.getConfigurationSection("fish-overrides").getConfigurationSection("Legendary:Golden Koi")
            .set("scale",4.0);reject(y,"too large");
        y=valid();y.getConfigurationSection("fish-overrides").getConfigurationSection("Legendary:Golden Koi")
            .set("item-model","bad:../../escape");reject(y,"path traversal");
        y=valid();y.getConfigurationSection("fish-overrides").getConfigurationSection("Legendary:Golden Koi")
            .set("interpolation-ticks",90);reject(y,"interp out of bounds");
        y=valid();y.getConfigurationSection("fish-overrides").getConfigurationSection("Legendary:Golden Koi")
            .set("interpolation-ticks","two");reject(y,"interp type");
        y=valid();y.set("fish-overrides",new ConfigurationSection());
        assert FishModelConfigLoader.parse(y).fish().isEmpty();
        System.out.println("PASS Phase15 config loader: FishKey spaces, strict pack UUID/material/path/scale/interpolation, blank mappings");
    }
}

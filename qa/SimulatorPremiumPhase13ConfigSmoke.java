import com.oheers.fish.fishing.simulator.environment.*;
import com.oheers.fish.fishing.simulator.behavior.FishPersonality;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
public final class SimulatorPremiumPhase13ConfigSmoke {
    private static void ok(boolean v,String text){if(!v)throw new AssertionError(text);}
    private static void rejected(Runnable runnable,String fragment) {
        try {runnable.run();throw new AssertionError("must reject "+fragment);}
        catch(IllegalArgumentException e){ok(e.getMessage().contains(fragment),e.toString());}
    }
    private static YamlConfiguration good() {
        YamlConfiguration y=new YamlConfiguration();
        y.set("config-version",1);y.set("enabled",false);
        y.set("environment.enabled",true);y.set("personality.enabled",true);y.set("water-fx.enabled",false);
        y.set("environment.max-samples-per-tick",4);y.set("environment.cache-entries",256);
        y.set("environment.cache-ttl-ticks",200);y.set("environment.depth-scan-blocks",8);
        y.set("personality.shy-disturb-radius",5.0);
        ConfigurationSection rarity=new ConfigurationSection();rarity.set("Rare","SHY");
        y.set("personality.rarity-profiles",rarity);
        ConfigurationSection fish=new ConfigurationSection();fish.set("Epic:Test","MYTHIC");
        y.set("personality.fish-overrides",fish);
        y.set("water-fx.interval-ticks",10);y.set("water-fx.max-particles-per-tick",32);
        y.set("water-fx.max-particles-per-viewer",8);
        y.set("water-fx.candidates-per-viewer",24);y.set("water-fx.radius",16.0);
        return y;
    }
    public static void main(String[] args){
        var y=good();var parsed=EcosystemConfigLoader.parse(y);
        ok(!parsed.enabled() && parsed.personalityEnabled(),"staging OFF");
        ok(parsed.fishOverrides().get("Epic:Test")==FishPersonality.MYTHIC,"FishKey override");
        y.set("water-fx.max-particles-per-tick",2);
        final YamlConfiguration tinyCap=y;
        rejected(()->EcosystemConfigLoader.parse(tinyCap),"FX limits");
        y=good();y.set("environment.max-samples-per-tick",100);
        final YamlConfiguration badBudget=y;
        rejected(()->EcosystemConfigLoader.parse(badBudget),"max-samples-per-tick");
        y=good();y.set("personality.enabled","true");
        final YamlConfiguration badBoolean=y;
        rejected(()->EcosystemConfigLoader.parse(badBoolean),"personality.enabled");
        y=good();y.set("water-fx.radius",Double.NaN);
        final YamlConfiguration nan=y;
        rejected(()->EcosystemConfigLoader.parse(nan),"water-fx.radius");
        y=good();ConfigurationSection fish=(ConfigurationSection)y.getConfigurationSection("personality.fish-overrides");
        fish.set("Epic:Test","INVALID");
        final YamlConfiguration badProfile=y;
        rejected(()->EcosystemConfigLoader.parse(badProfile),"Unknown personality");
        y=good();y.set("config-version",2);final YamlConfiguration badVersion=y;
        rejected(()->EcosystemConfigLoader.parse(badVersion),"config-version");
        System.out.println("PASS: Phase13 ecosystem schema and strict invalid-config handling (API stubs)");
    }
}

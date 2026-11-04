import com.oheers.fish.fishing.simulator.encounter.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.List;

public class SimulatorPremiumPhase14ConfigSmoke {
    private static YamlConfiguration valid(){
        var y=new YamlConfiguration();
        y.set("config-version",1);y.set("enabled",true);
        y.set("limits.scan-interval-ticks",20);y.set("limits.candidates-per-scan",20);
        y.set("limits.max-active",8);y.set("limits.max-per-zone",2);
        y.set("limits.life-ticks",600);y.set("limits.zone-cooldown-ticks",400);
        y.set("limits.viewer-cooldown-ticks",160);y.set("visuals.interval-ticks",12);
        y.set("visuals.max-particles-per-tick",24);y.set("visuals.max-particles-per-viewer",6);
        y.set("visuals.reveal-radius",16.0);y.set("discovery.activation-chance",.25);
        y.set("discovery.eligible-rarities",List.of("Rare","Epic","Legendary"));
        y.set("discovery.fish-overrides",new org.bukkit.configuration.ConfigurationSection());
        for(var kind:RareEncounterDirector.Kind.values()){
            var prefix="types."+kind.name();
            y.set(prefix+".enabled",true);y.set(prefix+".weight",10);
            y.set(prefix+".night-only",kind==RareEncounterDirector.Kind.GHOST);
            y.set(prefix+".deep-only",kind==RareEncounterDirector.Kind.SHADOW);
            y.set(prefix+".storm-only",false);
            y.set(prefix+".min-school-size",kind==RareEncounterDirector.Kind.RARE_SCHOOL?2:1);
        }
        return y;
    }
    private static void rejects(YamlConfiguration y){
        boolean rejected=false;
        try {RareEncounterConfigLoader.parse(y);}catch(IllegalArgumentException e){rejected=true;}
        assert rejected:"Invalid config incorrectly accepted";
    }
    public static void main(String[] args){
        var y=valid();var settings=RareEncounterConfigLoader.parse(y);
        assert settings.enabled() && settings.allows("rare") && settings.limits().maxActive()==8;
        y=valid();y.set("limits.max-active",0);rejects(y);
        y=valid();y.set("visuals.max-particles-per-tick",2);rejects(y);
        y=valid();y.set("discovery.activation-chance",Double.NaN);rejects(y);
        y=valid();y.set("discovery.eligible-rarities",List.of("  "));rejects(y);
        y=valid();y.set("types.GOLDEN.weight",-1);rejects(y);
        y=valid();y.set("enabled","true");rejects(y);
        y=valid();y.set("types.GHOST.night-only","false");rejects(y);
        y=valid();y.set("config-version",2);rejects(y);
        y=valid();
        var section=new org.bukkit.configuration.ConfigurationSection();
        section.set("Rare:lake_carp","GOLDEN");
        y.set("discovery.fish-overrides",section);
        assert RareEncounterConfigLoader.parse(y).override(new com.oheers.fish.fishing.simulator.population.FishKey("Rare","lake_carp"))
            == RareEncounterDirector.Kind.GOLDEN;
        section.set("Rare:lake_carp","NO_SUCH_ENCOUNTER");rejects(y);
        section.set("Rare:lake_carp","GOLDEN");section.set("invalid-key","GOLDEN");rejects(y);
        System.out.println("PASS Phase14 config strict validation, malformed YAML fields rejected");
    }
}

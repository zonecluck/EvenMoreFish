package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Separately versioned effects.yml. A malformed reload never replaces live state. */
public final class SimulatorEffectsConfigLoader {
    private static final Pattern SAFE_ID=Pattern.compile("[a-zA-Z0-9 _:-]{1,96}");
    private static final Set<String> SIMPLE_PARTICLES=Set.of(
        "SPLASH", "WATER_SPLASH", "BUBBLE_POP", "WATER_BUBBLE", "FISHING", "WATER_WAKE",
        "HAPPY_VILLAGER", "VILLAGER_HAPPY", "CLOUD", "CRIT", "ENCHANT", "ENCHANTMENT_TABLE");
    private static final Set<String> PRESET_IDS=Set.of("common", "rare", "epic", "legendary");

    private final EvenMoreFish plugin;
    public SimulatorEffectsConfigLoader(EvenMoreFish plugin) { this.plugin=plugin; }

    /** Returns null on failure; caller must retain the previous config and running sessions. */
    public SimulatorEffectsSettings readCandidate() {
        File file = new File(plugin.getDataFolder(), "simulator/effects.yml");
        try {
            if (!file.isFile()) plugin.saveResource("simulator/effects.yml", false);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            return parse(yaml);
        } catch (IOException | InvalidConfigurationException | RuntimeException error) {
            plugin.getLogger().severe("[Simulator] Invalid simulator/effects.yml; retaining previous snapshot: " + error.getMessage());
            return null;
        }
    }

    public static SimulatorEffectsSettings parse(YamlConfiguration yaml) {
        check(yaml.getInt("config-version", -1)==1, "config-version must be 1");
        String filled=yaml.getString("hud.filled-glyph", "▰");
        String empty=yaml.getString("hud.empty-glyph", "▱");
        glyph(filled,"hud.filled-glyph");glyph(empty,"hud.empty-glyph");
        Map<String,SimulatorEffectsSettings.Preset> presets=new HashMap<>();
        ConfigurationSection presetSection=yaml.getConfigurationSection("presets");
        check(presetSection!=null, "presets must exist");
        check(presetSection.getKeys(false).containsAll(PRESET_IDS), "presets require common, rare, epic, legendary");
        check(presetSection.getKeys(false).size()==PRESET_IDS.size(), "unsupported preset ID");
        for(String id:PRESET_IDS){
            String base="presets."+id;
            check(yaml.isConfigurationSection(base),base+" must be a section");
            presets.put(id,new SimulatorEffectsSettings.Preset(
                cue(yaml,base+".interest"),cue(yaml,base+".bite"),
                cue(yaml,base+".success"),cue(yaml,base+".failure"),
                integer(yaml,base+".cinematic.duration-ticks",14,4,40),
                decimal(yaml,base+".cinematic.arc-height",.7,0,3)));
        }
        Map<String,String> rarities=mapping(yaml,"rarity-presets",presets);
        Map<String,String> fish=mapping(yaml,"fish-overrides",presets);
        return new SimulatorEffectsSettings(
            flag(yaml,"effects.enabled",false),
            flag(yaml,"hud.enabled",true),
            integer(yaml,"hud.interval-ticks",5,4,20),
            integer(yaml,"hud.segments",12,4,24),filled,empty,
            flag(yaml,"cinematic.enabled",false),
            integer(yaml,"cinematic.max-concurrent",4,1,8),
            integer(yaml,"cinematic.packet-budget-per-tick",12,2,32),
            decimal(yaml,"cinematic.max-distance",32,4,48),
            presets,rarities,fish);
    }
    private static SimulatorEffectsSettings.Cue cue(YamlConfiguration yaml,String path){
        check(yaml.isConfigurationSection(path),path+" must be a section");
        String name=yaml.getString(path+".sound");
        if(name==null && !yaml.contains(path+".sound"))name="NONE";
        check(name!=null,path+".sound must be a sound ID or NONE");
        name=name.toUpperCase(Locale.ROOT);
        Sound.Type sound=null;
        if(!name.equals("NONE")){
            sound=FishUtils.getSound(name);
            check(sound!=null,path+".sound unknown: "+name);
        }
        String particleName=yaml.getString(path+".particle");
        if(particleName==null && !yaml.contains(path+".particle"))particleName="NONE";
        check(particleName!=null,path+".particle must be a particle ID or NONE");
        particleName=particleName.toUpperCase(Locale.ROOT);
        Particle particle=null;
        if(!particleName.equals("NONE")){
            check(SIMPLE_PARTICLES.contains(particleName),path+".particle is not a simple particle: "+particleName);
            particle=particle(particleName);
            check(particle!=null,path+".particle unknown on this server: "+particleName);
        }
        return new SimulatorEffectsSettings.Cue(sound,
            (float)decimal(yaml,path+".volume",.7,0,2),
            (float)decimal(yaml,path+".pitch",1,0.5,2),particle,
            integer(yaml,path+".count",5,0,24));
    }
    private static Particle particle(String candidate){
        String alternative=switch(candidate){
            case "SPLASH" -> "WATER_SPLASH";
            case "BUBBLE_POP" -> "WATER_BUBBLE";
            case "FISHING" -> "WATER_WAKE";
            case "HAPPY_VILLAGER" -> "VILLAGER_HAPPY";
            case "ENCHANT" -> "ENCHANTMENT_TABLE";
            default -> candidate;
        };
        try{return Particle.valueOf(candidate);}
        catch(IllegalArgumentException ignored){
            try{return Particle.valueOf(alternative);}
            catch(IllegalArgumentException second){return null;}
        }
    }
    private static Map<String,String> mapping(YamlConfiguration yaml,String key,
            Map<String,SimulatorEffectsSettings.Preset> presets){
        ConfigurationSection section=yaml.getConfigurationSection(key);
        if(section==null)return Map.of();
        check(section.getKeys(false).size()<=256,key+" supports no more than 256 entries");
        Map<String,String> result=new HashMap<>();
        for(String id:section.getKeys(false)){
            check(SAFE_ID.matcher(id).matches(),key+"."+id+" has invalid key");
            String preset=section.getString(id);
            check(preset!=null,key+"."+id+" must be a preset ID");
            preset=preset.toLowerCase(Locale.ROOT);
            check(presets.containsKey(preset),key+"."+id+" references unknown preset "+preset);
            check(result.putIfAbsent(id.toLowerCase(Locale.ROOT),preset)==null,
                key+" has duplicate case-insensitive ID "+id);
        }
        return result;
    }
    private static void glyph(String glyph,String key){
        check(glyph!=null && glyph.codePointCount(0,glyph.length())>=1 &&
            glyph.codePointCount(0,glyph.length())<=2 && !glyph.contains("<") && !glyph.contains("{") &&
            !glyph.contains("\n") && !glyph.contains("\r"),key+" must be 1-2 safe characters");
    }
    private static boolean flag(YamlConfiguration yaml,String key,boolean fallback){
        check(!yaml.contains(key)||yaml.isBoolean(key),key+" must be boolean");
        return yaml.getBoolean(key,fallback);
    }
    private static int integer(YamlConfiguration y,String key,int fallback,int min,int max){
        check(!y.contains(key)||y.isInt(key),key+" must be integer");
        int n=y.getInt(key,fallback);check(n>=min&&n<=max,key+" must be "+min+".."+max);return n;
    }
    private static double decimal(YamlConfiguration y,String key,double fallback,double min,double max){
        check(!y.contains(key)||y.isDouble(key)||y.isInt(key),key+" must be numeric");
        double n=y.getDouble(key,fallback);check(Double.isFinite(n)&&n>=min&&n<=max,key+" must be "+min+".."+max);return n;
    }
    private static void check(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}

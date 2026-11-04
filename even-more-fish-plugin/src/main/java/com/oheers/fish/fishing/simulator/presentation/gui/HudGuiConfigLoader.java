package com.oheers.fish.fishing.simulator.presentation.gui;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.simulator.presentation.UiProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HudGuiConfigLoader {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final EvenMoreFish plugin;
    public HudGuiConfigLoader(EvenMoreFish plugin) { this.plugin=plugin; }
    public HudGuiSettings readCandidate() {
        File file=new File(plugin.getDataFolder(),"simulator/hud-gui.yml");
        try {
            if(!file.isFile())plugin.saveResource("simulator/hud-gui.yml",false);
            YamlConfiguration y=new YamlConfiguration();y.load(file);
            return parse(y);
        } catch(IOException | InvalidConfigurationException | RuntimeException failure) {
            plugin.getLogger().severe("[Simulator] Invalid simulator/hud-gui.yml; snapshot retained: " + failure.getMessage());
            return null;
        }
    }
    public static HudGuiSettings parse(YamlConfiguration y) {
        check(y.isInt("config-version") && y.getInt("config-version")==1,"config-version must be 1");
        check(y.isBoolean("enabled"),"enabled must be boolean");
        int size=y.getInt("size",-1);
        check(size>=9&&size<=54&&size%9==0,"size must be 9..54 divisible by 9");
        Component title=text(y,"title");
        Material fill=material(y,"filler.material");
        Component fillerName=text(y,"filler.name");
        String name=y.getString("click-sound");
        check(name!=null,"click-sound required");
        NamespacedKey key=NamespacedKey.fromString(name);
        check(key!=null,"click-sound must be a valid namespaced key");
        Sound sound=Registry.SOUNDS.get(key);
        check(sound!=null,"click-sound unknown: "+name);
        var choices=new HashMap<UiProfile,HudGuiSettings.Button>();
        Set<Integer> used=new HashSet<>();
        for(UiProfile profile:UiProfile.values()){
            String base="buttons."+profile.name();
            check(y.isConfigurationSection(base),base+" must be a section");
            int slot=y.getInt(base+".slot",-1);
            check(slot>=0&&slot<size,base+".slot must be within inventory bounds");
            check(used.add(slot),base+".slot duplicates a prior button");
            Material icon=material(y,base+".material");
            Component label=text(y,base+".name");
            check(y.isList(base+".lore"),base+".lore must be a list");
            List<String> raw=y.getStringList(base+".lore");
            check(raw.size()<=12,base+".lore has too many lines");
            List<Component> lore=raw.stream().map(v->parse(v,base+".lore")).toList();
            check(y.isBoolean(base+".glow"),base+".glow must be boolean");
            choices.put(profile,new HudGuiSettings.Button(slot,icon,label,lore,y.getBoolean(base+".glow")));
        }
        return new HudGuiSettings(y.getBoolean("enabled"),title,size,fill,fillerName,sound,choices);
    }
    private static Material material(YamlConfiguration y,String path){
        String name=y.getString(path);
        check(name!=null,path+" must exist");
        Material mat=Material.matchMaterial(name);
        check(mat!=null && mat.isItem() && mat!=Material.AIR,path+" invalid material: "+name);
        return mat;
    }
    private static Component text(YamlConfiguration y,String path){
        String raw=y.getString(path);
        check(raw!=null,path+" must be a MiniMessage string");
        return parse(raw,path);
    }
    private static Component parse(String raw,String path){
        check(raw.length()<=256&&!raw.contains("\n")&&!raw.contains("\r"),path+" too long/unsafe");
        return MINI.deserialize(raw);
    }
    private static void check(boolean pass,String message){if(!pass)throw new IllegalArgumentException(message);}
}

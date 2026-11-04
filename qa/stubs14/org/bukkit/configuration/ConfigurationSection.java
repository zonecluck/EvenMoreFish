package org.bukkit.configuration;
import java.util.*;
public class ConfigurationSection {
    protected final Map<String,Object> data=new HashMap<>();
    public Set<String> getKeys(boolean deep){return data.keySet();}
    public boolean isString(String path){return data.get(path) instanceof String;}
    public String getString(String path){return (String)data.get(path);}
    public void set(String path,Object obj){data.put(path,obj);}
}

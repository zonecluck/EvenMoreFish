package org.bukkit.configuration;
import java.util.*;
public class ConfigurationSection {
    protected final Map<String,Object> data=new HashMap<>();
    public Set<String> getKeys(boolean deep){return data.keySet();}
    public boolean isString(String path){return data.get(path) instanceof String;}
    public String getString(String path){return (String)data.get(path);}
    public String getString(String path,String fallback){return isString(path)?getString(path):fallback;}
    public ConfigurationSection getConfigurationSection(String path){return (ConfigurationSection)data.get(path);}
    public boolean isInt(String path){return data.get(path) instanceof Integer;}
    public boolean isDouble(String path){return data.get(path) instanceof Double;}
    public int getInt(String path){return ((Number)data.get(path)).intValue();}
    public double getDouble(String path,double defaultValue){return data.get(path) instanceof Number ? ((Number)data.get(path)).doubleValue():defaultValue;}
    public void set(String path,Object obj){data.put(path,obj);}
}

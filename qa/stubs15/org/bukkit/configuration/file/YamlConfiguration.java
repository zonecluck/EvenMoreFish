package org.bukkit.configuration.file;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import java.io.*;
import java.util.*;
public class YamlConfiguration extends ConfigurationSection {
    public boolean isList(String path){return data.get(path) instanceof java.util.List<?>;}
    public java.util.List<?> getList(String path){return (java.util.List<?>)data.get(path);}
    public void load(File file) throws IOException,InvalidConfigurationException { }
    public boolean isBoolean(String path){return data.get(path) instanceof Boolean;}
    public boolean getBoolean(String path){return (boolean)data.get(path);}
    public boolean isInt(String path){return data.get(path) instanceof Integer;}
    public boolean isDouble(String path){return data.get(path) instanceof Double;}
    public int getInt(String path){return (int)data.get(path);}
    public int getInt(String path,int defaultValue){return data.containsKey(path)?((Number)data.get(path)).intValue():defaultValue;}
    public double getDouble(String path,double defaultValue){return data.containsKey(path)?((Number)data.get(path)).doubleValue():defaultValue;}

}

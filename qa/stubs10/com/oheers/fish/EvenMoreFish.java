package com.oheers.fish;
import java.io.File;import java.util.logging.Logger;
public final class EvenMoreFish {
 private final File folder;
 public EvenMoreFish(File folder){this.folder=folder;}
 public File getDataFolder(){return folder;}
 public boolean isEnabled(){return true;}
 public Logger getLogger(){return Logger.getLogger("EMF-Smoke");}
 public static Scheduler getScheduler(){return new Scheduler();}
 public static final class Scheduler { public void runTask(Runnable runnable){runnable.run();} }
}

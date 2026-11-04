package net.kyori.adventure.sound;
public class Sound {
  public interface Type {}
  public static class Builder {
    public Builder type(Type t){return this;}
    public Builder volume(float v){return this;}
    public Builder pitch(float p){return this;}
    public Sound build(){return new Sound();}
  }
  public static Builder sound(){return new Builder();}
  public static class Emitter {public static Emitter self(){return new Emitter();}}
}

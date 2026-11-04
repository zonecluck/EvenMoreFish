package org.bukkit;
public class Location implements Cloneable {
  private final World world;
  private double x,y,z;
  public Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
  public World getWorld(){return world;}
  public double getX(){return x;}
  public double getY(){return y;}
  public double getZ(){return z;}
  public Location add(double x,double y,double z){this.x+=x;this.y+=y;this.z+=z;return this;}
  @Override public Location clone(){return new Location(world,x,y,z);}
  public double distanceSquared(Location p){
    if(!world.equals(p.world))throw new IllegalArgumentException("world mismatch");
    double a=x-p.x,b=y-p.y,c=z-p.z;return a*a+b*b+c*c;
  }
}

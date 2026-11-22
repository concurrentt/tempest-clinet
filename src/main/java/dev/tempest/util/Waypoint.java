package dev.tempest.util;

public class Waypoint {
    public String name;
    public int x, y, z;
    public String dim;
    public int color = 0xFF55FFFF;

    public Waypoint() { }

    public Waypoint(String name, int x, int y, int z, String dim, int color) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.color = color;
    }
}

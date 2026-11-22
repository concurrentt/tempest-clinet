package dev.tempest.module;

public class ColorSetting extends Setting {
    public static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55,
            0xFF55FF55, 0xFF55FFFF, 0xFF5B8CFF, 0xFFFF55FF
    };
    private int value;

    public ColorSetting(String id, String name, int def) {
        super(id, name);
        this.value = def;
    }

    public int get() { return value; }
    public void set(int argb) { value = argb | 0xFF000000; commit(); }

    @Override protected String serialize() { return Integer.toString(value); }
    @Override protected void load(String s) { value = Integer.parseInt(s) | 0xFF000000; }
}

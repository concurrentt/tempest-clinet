package dev.tempest.module;

public class ModeSetting extends Setting {
    public final String[] options;
    private int index;

    public ModeSetting(String id, String name, String def, String... options) {
        super(id, name);
        this.options = options;
        for (int i = 0; i < options.length; i++) if (options[i].equals(def)) index = i;
    }

    public String get() { return options[index]; }
    public int index() { return index; }
    public boolean is(String s) { return options[index].equals(s); }

    public void cycle(int dir) {
        index = Math.floorMod(index + dir, options.length);
        commit();
    }

    public void set(String value) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) { index = i; commit(); return; }
        }
    }

    @Override protected String serialize() { return options[index]; }
    @Override protected void load(String s) {
        for (int i = 0; i < options.length; i++) if (options[i].equals(s)) index = i;
    }
}

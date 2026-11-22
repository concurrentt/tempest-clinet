package dev.tempest.module;

public class BoolSetting extends Setting {
    private boolean value;

    public BoolSetting(String id, String name, boolean def) {
        super(id, name);
        this.value = def;
    }

    public boolean get() { return value; }
    public void set(boolean v) { value = v; commit(); }
    public void toggle() { set(!value); }

    @Override protected String serialize() { return Boolean.toString(value); }
    @Override protected void load(String s) { value = Boolean.parseBoolean(s); }
}

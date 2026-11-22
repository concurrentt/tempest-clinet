package dev.tempest.module;

import java.util.Locale;

public class NumberSetting extends Setting {
    public final double min, max, step;
    private double value;

    public NumberSetting(String id, String name, double def, double min, double max, double step) {
        super(id, name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = def;
    }

    public double get() { return value; }
    public int getInt() { return (int) Math.round(value); }
    public float getFloat() { return (float) value; }

    public void set(double v) {
        v = Math.max(min, Math.min(max, v));
        if (step > 0) v = Math.round(v / step) * step;
        value = v;
        commit();
    }

    public double fraction() { return (value - min) / (max - min); }
    public void setFraction(double f) { set(min + Math.max(0, Math.min(1, f)) * (max - min)); }

    public String display() {
        return step >= 1 ? Integer.toString((int) Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override protected String serialize() { return Double.toString(value); }
    @Override protected void load(String s) { value = Math.max(min, Math.min(max, Double.parseDouble(s))); }
}

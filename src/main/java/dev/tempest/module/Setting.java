package dev.tempest.module;

import dev.tempest.TempestConfig;

public abstract class Setting {
    public final String id;
    public final String name;
    private Module owner;

    protected Setting(String id, String name) {
        this.id = id;
        this.name = name;
    }

    final void bind(Module m) { this.owner = m; }

    private String key() { return owner.getId() + "." + id; }

    protected abstract String serialize();
    protected abstract void load(String s);

    /** Writes into the in-memory config; the file is saved when the GUI closes or the game stops. */
    protected final void commit() {
        if (owner != null) TempestConfig.get().settings.put(key(), serialize());
    }

    final void restore() {
        String s = TempestConfig.get().settings.get(key());
        if (s != null) {
            try { load(s); } catch (Exception ignored) { }
        }
    }
}

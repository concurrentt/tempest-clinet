package dev.tempest.module;

/** A button in the settings page that runs something. */
public class ActionSetting extends Setting {
    public final String label;
    private final Runnable action;

    public ActionSetting(String id, String name, String label, Runnable action) {
        super(id, name);
        this.label = label;
        this.action = action;
    }

    public void run() { action.run(); }

    @Override protected String serialize() { return ""; }
    @Override protected void load(String s) { }
}

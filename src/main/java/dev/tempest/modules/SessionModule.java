package dev.tempest.modules;

import dev.tempest.module.ActionSetting;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.util.PlaytimeTracker;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class SessionModule extends HudModule {
    private final BoolSetting session = add(new BoolSetting("session", "Session Timer", true));
    private final BoolSetting total = add(new BoolSetting("total", "Total Playtime", true));
    private final BoolSetting server = add(new BoolSetting("server", "Playtime On This Server", false));

    public SessionModule() {
        super("session", "Session & Playtime", "Time in this session and your total playtime.", Category.HUD, false, 6, 330);
        add(new ActionSetting("reset", "Reset Session", "Reset", PlaytimeTracker::resetSession));
    }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        List<String> lines = new ArrayList<>();
        if (session.get()) lines.add("Session: " + (preview ? "0:42:10" : PlaytimeTracker.clock(PlaytimeTracker.session())));
        if (total.get()) lines.add("Playtime: " + (preview ? "128h 05m" : PlaytimeTracker.hours(PlaytimeTracker.total())));
        if (server.get()) lines.add("Here: " + (preview ? "12h 30m" : PlaytimeTracker.hours(PlaytimeTracker.server())));
        if (lines.isEmpty()) return;
        drawLines(ctx, lines);
    }
}

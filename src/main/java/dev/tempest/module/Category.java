package dev.tempest.module;

public enum Category {
    HUD("HUD"), PVP("PvP"), RENDER("Render"), VISUALS("Visuals"), QOL("QOL"), UTILITY("Utility"), CHAT("Chat"), CLIENT("Client");

    public final String label;
    Category(String label) { this.label = label; }
}

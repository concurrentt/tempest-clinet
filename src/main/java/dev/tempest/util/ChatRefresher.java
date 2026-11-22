package dev.tempest.util;

/** Implemented on ChatHud by ChatHudMixin: rebuilds the visible chat lines (used when the chat tab changes). */
public interface ChatRefresher {
    void tempest$refreshChat();
}

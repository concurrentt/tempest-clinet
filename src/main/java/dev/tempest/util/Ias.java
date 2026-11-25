package dev.tempest.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Bridge to In-Game Account Switcher (IAS, by The_Fireplace and VidTu, LGPL-3.0-or-later), which ships inside the
 * Tempest jar (build.gradle: include). IAS keeps the accounts, signs in with its own Microsoft app and swaps the
 * session; Tempest only draws the account list (gui/IasAccountsScreen) and opens IAS's small popups for adding,
 * editing, removing and the sign-in progress.
 *
 * IAS is reached by reflection: it can't be on Tempest's compile classpath (it's built with a newer Loom) and most
 * of its screens are package-private. The calls match IAS 9.0.6 (the version bundled); if any of them can't be
 * found, {@link #fullUi()} is false and Tempest falls back to IAS's own screen, so nothing breaks.
 */
public final class Ias {
    /** One account from IAS's storage. */
    public record Acc(Object raw, String name, UUID uuid, UUID skin, boolean microsoft, boolean canLogin) { }

    private static final String P = "ru.vidtu.ias.";
    private static boolean looked, full;
    private static Constructor<?> accountScreen, loginPopup, addPopup, deletePopup, loginData;
    private static Class<?> accountCls;
    private static Method disabled, executor, saveStorage, disclaimersStorage, offlineUuid,
            name, uuid, skin, type, canLogin, login, success;
    private static java.lang.reflect.Field accounts, disclaimerShown;

    private Ias() { }

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        if (!FabricLoader.getInstance().isModLoaded("ias")) return;
        try {
            accountScreen = Class.forName(P + "screen.AccountScreen").getConstructor(Screen.class);
        } catch (Throwable t) {
            log("IAS account screen not found", t);
            return;
        }
        try {
            Class<?> ias = Class.forName(P + "IAS");
            Class<?> storage = Class.forName(P + "config.IASStorage");
            accountCls = Class.forName(P + "account.Account");
            Class<?> handler = Class.forName(P + "auth.handlers.LoginHandler");
            Class<?> data = Class.forName(P + "auth.LoginData");
            disabled = ias.getMethod("disabled");
            executor = ias.getMethod("executor");
            saveStorage = ias.getMethod("saveStorage");
            disclaimersStorage = ias.getMethod("disclaimersStorage");
            accounts = storage.getField("ACCOUNTS");
            disclaimerShown = storage.getField("gameDisclaimerShown");
            offlineUuid = Class.forName(P + "account.OfflineAccount").getMethod("uuid", String.class);
            name = accountCls.getMethod("name");
            uuid = accountCls.getMethod("uuid");
            skin = accountCls.getMethod("skin");
            type = accountCls.getMethod("type");
            canLogin = accountCls.getMethod("canLogin");
            login = accountCls.getMethod("login", handler, Runnable.class);
            success = handler.getMethod("success", data, boolean.class);
            loginData = data.getConstructor(String.class, UUID.class, String.class, boolean.class);
            loginPopup = Class.forName(P + "screen.LoginPopupScreen").getDeclaredConstructor(Screen.class);
            addPopup = Class.forName(P + "screen.AddPopupScreen").getDeclaredConstructor(Screen.class, boolean.class, Consumer.class);
            deletePopup = Class.forName(P + "screen.DeletePopupScreen").getDeclaredConstructor(Screen.class, accountCls, Runnable.class);
            loginPopup.setAccessible(true);
            addPopup.setAccessible(true);
            deletePopup.setAccessible(true);
            full = true;
        } catch (Throwable t) {
            log("IAS internals changed - using IAS's own account screen", t);
        }
    }

    /** True when IAS is loaded (the Switch account button shows). */
    public static boolean available() { look(); return accountScreen != null; }

    /** True when Tempest can draw its own account list on top of IAS. */
    public static boolean fullUi() { look(); return full; }

    /**
     * The screen for "Switch account": Tempest's list, or IAS's own screen when IAS is turned off, its one-time
     * disclaimer hasn't been accepted yet (IAS shows and records that itself), or its internals don't match.
     */
    public static Screen screen(Screen parent) {
        look();
        try {
            if (full && !(boolean) disabled.invoke(null) && disclaimerShown.getBoolean(null)) {
                return new dev.tempest.gui.IasAccountsScreen(parent);
            }
            return accountScreen == null ? null : (Screen) accountScreen.newInstance(parent);
        } catch (Throwable t) {
            log("Couldn't open In-Game Account Switcher", t);
            return null;
        }
    }

    /** IAS's own screen (used as a fallback). */
    public static Screen iasScreen(Screen parent) {
        try { return accountScreen == null ? null : (Screen) accountScreen.newInstance(parent); }
        catch (Throwable t) { log("Couldn't open IAS screen", t); return null; }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> store() throws Exception { return (List<Object>) accounts.get(null); }

    /** Every saved account, in IAS's order. */
    public static List<Acc> accounts() {
        List<Acc> out = new ArrayList<>();
        if (!full) return out;
        try {
            for (Object a : store()) {
                out.add(new Acc(a, (String) name.invoke(a), (UUID) uuid.invoke(a), (UUID) skin.invoke(a),
                        String.valueOf(type.invoke(a)).contains("microsoft"), (boolean) canLogin.invoke(a)));
            }
        } catch (Throwable t) {
            log("Couldn't read IAS accounts", t);
        }
        return out;
    }

    /** Switch to this account. Shows IAS's sign-in popup, which returns to parent when done. */
    public static void login(Acc a, Screen parent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            Object popup = loginPopup.newInstance(parent);
            mc.setScreen((Screen) popup);
            if (a.canLogin()) {
                ((Executor) executor.invoke(null)).execute(() -> {
                    try { login.invoke(a.raw(), popup, null); }
                    catch (Throwable t) { log("IAS login failed", t); }
                });
            } else {
                Object data = loginData.newInstance(a.name(), offlineUuid.invoke(null, a.name()), "ias:offline", false);
                success.invoke(popup, data, false);
            }
        } catch (Throwable t) {
            log("Couldn't start IAS login", t);
        }
    }

    /** Add (edit = false) or edit an account with IAS's popup; comes back to parent afterwards. */
    public static void addOrEdit(Screen parent, Acc editing) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            Consumer<Object> done = acc -> {
                mc.setScreen(parent);
                try {
                    List<Object> list = store();
                    int index = editing == null ? -1 : list.indexOf(editing.raw());
                    list.removeIf(acc::equals);
                    if (index < 0 || index >= list.size()) list.add(acc);
                    else list.set(index, acc);
                    save();
                } catch (Throwable t) {
                    log("Couldn't save IAS account", t);
                }
            };
            mc.setScreen((Screen) addPopup.newInstance(parent, editing != null, done));
        } catch (Throwable t) {
            log("Couldn't open IAS add screen", t);
        }
    }

    /** Remove an account after IAS's confirmation popup. */
    public static void remove(Screen parent, Acc a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            Runnable confirmed = () -> {
                try { store().remove(a.raw()); save(); }
                catch (Throwable t) { log("Couldn't remove IAS account", t); }
            };
            mc.setScreen((Screen) deletePopup.newInstance(parent, a.raw(), confirmed));
        } catch (Throwable t) {
            log("Couldn't open IAS delete screen", t);
        }
    }

    private static void save() throws Exception {
        disclaimersStorage.invoke(null);
        saveStorage.invoke(null);
    }

    private static void log(String what, Throwable t) {
        System.out.println("[Tempest] " + what + ": " + t);
    }
}

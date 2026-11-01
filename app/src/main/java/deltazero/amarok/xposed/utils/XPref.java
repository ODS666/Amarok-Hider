package deltazero.amarok.xposed.utils;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/**
 * Reads the XHide configuration from inside the hooked process.
 *
 * <p>Replaces {@code XSharedPreferences}. With libxposed API 102 the framework owns the shared
 * storage: the module reads a "remote preferences" group through
 * {@link io.github.libxposed.api.XposedInterface#getRemotePreferences(String)} (read-only here) and
 * the Amarok app writes the very same group through {@code XposedService#getRemotePreferences}. The
 * group name is intentionally kept equal to the old {@code XSharedPreferences} file name.</p>
 */
public class XPref {

    public static final String XPREF_GROUP = "deltazero.amarok.xposed.prefs";
    public static final String HIDE_PKG_NAMES = "hidePkgNames";
    public static final String IS_ACTIVE = "isActive";

    private static volatile SharedPreferences xPref;
    private static volatile boolean isActiveCache = false;
    private static volatile Set<String> hidePkgNamesCache = Collections.emptySet();

    private XPref() {
    }

    public static void init(XposedModule module) {
        XLog.d("Initializing XPref...");

        SharedPreferences pref;
        try {
            // Read-only in the hooked process: Amarok itself is the only writer.
            pref = module.getRemotePreferences(XPREF_GROUP);
        } catch (Throwable t) {
            XLog.e("Failed to acquire remote preferences, XHide will stay inactive", t);
            return;
        }

        try {
            XLog.d("xPref content: " + pref.getAll());
        } catch (Throwable ignored) {
            // Logging is best-effort only.
        }

        xPref = pref;
        refreshCache();
        XLog.i("XPref initialized.");
    }

    public static void refreshCache() {
        SharedPreferences pref = xPref;
        if (pref == null) return;

        try {
            isActiveCache = pref.getBoolean(IS_ACTIVE, false);
            Set<String> names = pref.getStringSet(HIDE_PKG_NAMES, Collections.emptySet());
            hidePkgNamesCache = names == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new HashSet<>(names));
        } catch (Throwable t) {
            XLog.e("Failed to refresh XPref cache", t);
        }
    }

    public static boolean isXHideActive() {
        return isActiveCache;
    }

    public static boolean shouldHide(String pkgName) {
        return hidePkgNamesCache.contains(pkgName);
    }
}

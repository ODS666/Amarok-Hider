package deltazero.amarok.utils;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Objects;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import deltazero.amarok.Hider;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.xposed.utils.XPref;

/**
 * Bridges Amarok's own preferences to the Xposed framework.
 *
 * <p>Replaces the legacy scheme, in which the module reflected {@code isModuleActive},
 * {@code xposedVersion} and {@code xPrefDir} into this class and both sides shared a
 * {@code MODE_WORLD_READABLE} preferences file that was copied around by hand. With libxposed API 102
 * the framework owns that storage:</p>
 *
 * <ul>
 *   <li>the app binds the framework service through {@link XposedServiceHelper} and writes the
 *       configuration group through {@link XposedService#getRemotePreferences(String)};</li>
 *   <li>the module reads the same group through {@code XposedInterface#getRemotePreferences}.</li>
 * </ul>
 *
 * <p>Amarok's own process therefore no longer has to be in the module scope at all.</p>
 */
public class XHidePrefBridge {
    private static final String TAG = "XHidePrefBridge";

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static SharedPreferences.OnSharedPreferenceChangeListener hidePkgNamesChangeListener;
    private static volatile SharedPreferences.Editor xprefEditor;

    /** True once the framework service is bound, i.e. the Xposed module is alive. */
    public static volatile boolean isModuleActive = false;
    /** Running Xposed API version; 0 while the module is not active. */
    public static volatile int xposedVersion = 0;
    /** Whether XHide can be offered in the settings UI. */
    public static volatile boolean isAvailable = false;

    private static boolean listenersRegistered = false;

    private XHidePrefBridge() {
    }

    public static void init() {
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {

                @Override
                public void onServiceBind(XposedService service) {
                    Log.i(TAG, "Xposed service bound: "
                            + service.getFrameworkName() + " " + service.getFrameworkVersion()
                            + ", API " + service.getApiVersion());

                    SharedPreferences.Editor editor;
                    try {
                        SharedPreferences xPref = service.getRemotePreferences(XPref.XPREF_GROUP);
                        editor = xPref.edit();
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to open remote preferences", t);
                        return;
                    }

                    xprefEditor = editor;
                    xposedVersion = service.getApiVersion();
                    isModuleActive = true;

                    // SharedPreferences listeners, LiveData observers and the preference objects
                    // we touch below all belong to the main thread.
                    MAIN_HANDLER.post(() -> onServiceBoundOnMainThread());
                }

                @Override
                public void onServiceDied(XposedService service) {
                    Log.w(TAG, "Xposed service died");
                    isModuleActive = false;
                    isAvailable = false;
                    xposedVersion = 0;
                    xprefEditor = null;
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "Failed to register Xposed service listener", t);
        }
    }

    private static void onServiceBoundOnMainThread() {
        if (!listenersRegistered) {
            hidePkgNamesChangeListener = (sharedPreferences, key) -> {
                // Avoid listening to HIDE_PKG_NAMES changes: SharedPreferences tends to write
                // Set<String> in memory first and flush asynchronously.
                if (Objects.equals(key, PrefMgr.ENABLE_X_HIDE)) {
                    commitNewValues();
                }
            };
            PrefMgr.getPrefs().registerOnSharedPreferenceChangeListener(hidePkgNamesChangeListener);

            Hider.state.observeForever(state -> commitNewValues());
            listenersRegistered = true;
        }

        commitNewValues();
        isAvailable = true;
        Log.i(TAG, "XHide initialized.");
    }

    private static void commitNewValues() {
        SharedPreferences.Editor editor = xprefEditor;
        if (editor == null) {
            // Service went away between binding and this call.
            return;
        }

        Log.d(TAG, "Committing new values to XPref");
        try {
            editor.putStringSet(XPref.HIDE_PKG_NAMES, PrefMgr.getHideApps());
            editor.putBoolean(XPref.IS_ACTIVE,
                    Hider.getState() == Hider.State.HIDDEN && PrefMgr.isXHideEnabled());
            if (!editor.commit()) {
                Log.w(TAG, "Failed to commit XPref values");
            }
        } catch (Throwable t) {
            // The framework's editor talks to another process; a dead binder must not take the app
            // (and these calls all happen on the main thread) down with it.
            Log.e(TAG, "Failed to commit XPref values", t);
        }
    }
}

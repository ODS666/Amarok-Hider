package deltazero.amarok.xposed;

import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import deltazero.amarok.xposed.hooks.FilterHooks;
import deltazero.amarok.xposed.hooks.IHook;
import deltazero.amarok.xposed.utils.ParceledListSliceUtil;
import deltazero.amarok.xposed.utils.XLog;
import deltazero.amarok.xposed.utils.XPref;

/**
 * Entry point of the Amarok XHide module, written against the modern Xposed API (libxposed 102).
 *
 * <p>Registered through {@code META-INF/xposed/java_init.list}, scoped to {@code system} through
 * {@code META-INF/xposed/scope.list}. The framework only injects the module into the processes
 * listed in the scope, so nothing needs to be done here for Amarok's own process any more: module
 * activation is reported to the app through {@code XposedService} instead of the legacy
 * "reflect a flag into our own static field" trick.</p>
 *
 * <p>Note that in system_server the first package lifecycle phase is replaced by
 * {@link #onSystemServerStarting}, so the hooks must be installed from there rather than from
 * {@code onPackageReady}.</p>
 *
 * @noinspection unused
 */
public class XposedEntry extends XposedModule {

    private static final String TAG = "Amarok-XHide";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        XLog.init(this, TAG);
        XLog.i("Module loaded. process = " + param.getProcessName()
                + ", systemServer = " + param.isSystemServer());
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        XLog.i("System server starting, initializing XHide...");

        ClassLoader classLoader = param.getClassLoader();
        ParceledListSliceUtil.init(classLoader);
        XPref.init(this);
        loadSystemHooks(classLoader);
    }

    private void loadSystemHooks(ClassLoader classLoader) {
        XLog.d("Initializing system hooks...");
        List<IHook> hooks = new ArrayList<>();

        if (Build.VERSION.SDK_INT > 36) {
            XLog.e("Unsupported Android version " + Build.VERSION.SDK_INT + ". Skip loading hooks.");
            return;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hooks.addAll(FilterHooks.target33(this, classLoader));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hooks.addAll(FilterHooks.target31(this, classLoader));
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            hooks.addAll(FilterHooks.target29(this, classLoader));
        } else {
            hooks.addAll(FilterHooks.legacy(this, classLoader));
        }

        XLog.d("Loading " + hooks.size() + " system hook(s)...");
        hooks.forEach(IHook::load);

        if (hooks.isEmpty()) {
            XLog.w("No hook was installed. Is the module scope set to `system`?");
        }
    }
}

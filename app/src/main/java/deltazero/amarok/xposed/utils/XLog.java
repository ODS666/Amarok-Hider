package deltazero.amarok.xposed.utils;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

/**
 * Minimal logging facade for the XHide module.
 *
 * <p>The modern Xposed API no longer exposes {@code XposedBridge.log()}, and modules targeting
 * API 102 must not touch the legacy {@code de.robv.android.xposed} classes at all. All module
 * logging therefore goes through the {@link XposedInterface} instance handed to the entry class,
 * with a logcat fallback for the (very short) window before the framework is attached.</p>
 */
public final class XLog {

    private static volatile XposedInterface sXposed;
    private static volatile String sTag = "Amarok-XHide";

    private XLog() {
    }

    public static void init(XposedInterface xposed, String tag) {
        sXposed = xposed;
        if (tag != null) sTag = tag;
    }

    public static void d(String msg) {
        log(Log.DEBUG, msg, null);
    }

    public static void i(String msg) {
        log(Log.INFO, msg, null);
    }

    public static void w(String msg) {
        log(Log.WARN, msg, null);
    }

    public static void e(String msg) {
        log(Log.ERROR, msg, null);
    }

    public static void e(String msg, Throwable tr) {
        log(Log.ERROR, msg, tr);
    }

    private static void log(int priority, String msg, Throwable tr) {
        String tag = sTag;
        XposedInterface xposed = sXposed;
        if (xposed != null) {
            if (tr == null) {
                xposed.log(priority, tag, String.valueOf(msg));
            } else {
                xposed.log(priority, tag, String.valueOf(msg), tr);
            }
        } else if (tr == null) {
            Log.println(priority, tag, String.valueOf(msg));
        } else {
            Log.println(priority, tag, msg + '\n' + Log.getStackTraceString(tr));
        }
    }
}

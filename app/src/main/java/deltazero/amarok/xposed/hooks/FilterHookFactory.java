package deltazero.amarok.xposed.hooks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import deltazero.amarok.xposed.utils.FilterUtils;
import deltazero.amarok.xposed.utils.XLog;
import deltazero.amarok.xposed.utils.XPref;

/**
 * Builds filters for the package-manager listing methods that system_server hands out to apps.
 *
 * <p>Migrated from the legacy {@code XC_MethodHook} model to the libxposed interceptor-chain model:
 * what used to be an {@code after} callback is now {@code chain.proceed()} followed by rewriting the
 * returned value.</p>
 */
public class FilterHookFactory {

    private FilterHookFactory() {
    }

    public static List<IHook> build(XposedModule module,
                                    ClassLoader classLoader,
                                    String className,
                                    String methodName,
                                    boolean isSlice) {
        Class<?> c;
        try {
            c = Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            XLog.e("Class not found when initializing hook: " + className, e);
            return Collections.emptyList();
        }

        List<Method> methods = findDeclaredMethods(c, methodName);
        if (methods.isEmpty()) {
            XLog.e("Method not found: " + className + "#" + methodName);
            return Collections.emptyList();
        }

        List<IHook> hooks = new ArrayList<>();
        for (Method m : methods) {
            hooks.add(new IHook() {

                private XposedInterface.HookHandle handle;

                @Override
                public String getName() {
                    return m.getName();
                }

                @SuppressWarnings("unchecked")
                @Override
                public void load() {
                    XLog.d("Loading hook: " + m);
                    try {
                        handle = module.hook(m)
                                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                                .intercept(chain -> {
                                    // Legacy `after` hook equivalent: run the original first, then
                                    // rewrite whatever it returned.
                                    Object result = chain.proceed();
                                    try {
                                        XPref.refreshCache();
                                        return isSlice
                                                ? FilterUtils.filterAppsOrPkgsInSlices(result, m)
                                                : FilterUtils.filterAppsOrPkgs((List<Object>) result, m);
                                    } catch (Throwable t) {
                                        // Never let a filter failure take system_server down: drop
                                        // this hook and return the unfiltered result instead.
                                        XLog.e("Error while filtering " + m + ", unhooking", t);
                                        XposedInterface.HookHandle h = handle;
                                        if (h != null) h.unhook();
                                        return result;
                                    }
                                });
                        XLog.i("Method hooked: " + m);
                    } catch (Throwable t) {
                        XLog.e("Hook failed: " + m, t);
                    }
                }
            });
        }
        return hooks;
    }

    /**
     * Collects every method with the given name declared by {@code c} or any of its superclasses.
     *
     * <p>The old code used EzXHelper's {@code MethodFinder}, which walked the class hierarchy; plain
     * {@code getDeclaredMethods()} on the target class alone would silently miss inherited methods
     * such as {@code IPackageManagerBase} implementations.</p>
     */
    private static List<Method> findDeclaredMethods(Class<?> c, String methodName) {
        List<Method> found = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            Method[] declared;
            try {
                declared = k.getDeclaredMethods();
            } catch (Throwable t) {
                XLog.e("Failed to enumerate methods of " + k.getName(), t);
                continue;
            }
            for (Method m : declared) {
                if (m.getName().equals(methodName)) found.add(m);
            }
        }
        return found;
    }
}

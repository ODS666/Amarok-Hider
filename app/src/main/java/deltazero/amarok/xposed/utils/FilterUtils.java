package deltazero.amarok.xposed.utils;

import android.annotation.SuppressLint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class FilterUtils {

    private FilterUtils() {
    }

    // NOTE: This method will not refresh XPref cache. Call refreshCache() before calling this method
    @SuppressLint("DefaultLocale")
    public static List<Object> filterAppsOrPkgs(List<Object> appsOrPkgs, Method m) {
        if (!XPref.isXHideActive()) return appsOrPkgs;

        AtomicInteger filteredCount = new AtomicInteger();
        List<Object> filteredPackages = appsOrPkgs.stream()
                .filter(appOrPkg -> {
                    String pkgName = appOrPkg == null ? null : getPackageName(appOrPkg);
                    // Keep entries we cannot resolve: one unreadable element must not disable the
                    // whole filter.
                    boolean shouldHide = pkgName != null && XPref.shouldHide(pkgName);
                    if (shouldHide) filteredCount.getAndIncrement();
                    return !shouldHide;
                })
                .collect(Collectors.toList());

        XLog.d(String.format("%s$%s(%s): Filtered %d packages",
                m.getDeclaringClass().getSimpleName(), m.getName(), Arrays.toString(m.getParameterTypes()),
                filteredCount.get()));
        return filteredPackages;
    }

    // NOTE: This method will not refresh XPref cache. Call refreshCache() before calling this method
    public static Object filterAppsOrPkgsInSlices(Object /* ParceledListSlice<*> */ appsOrPkgs, Method m) {
        List<Object> slice = ParceledListSliceUtil.sliceToList(appsOrPkgs);
        return ParceledListSliceUtil.listToSlice(filterAppsOrPkgs(slice, m));
    }

    /**
     * Reads the {@code packageName} field of an {@code ApplicationInfo} / {@code PackageInfo}, walking
     * up the class hierarchy until the field is declared.
     *
     * <p>Replaces {@code ObjectUtils.getObjectOrNullUntilSuperclassAs()} from EzXHelper, which can no
     * longer be used: modules targeting libxposed API 102 must not call the legacy Xposed API classes
     * that EzXHelper depends on.</p>
     *
     * <p>Returns {@code null} instead of throwing when the field cannot be resolved — the old helper
     * behaved the same way, and an exception here would unhook the filter for the rest of the boot.</p>
     */
    private static String getPackageName(Object appOrPkg) {
        for (Class<?> k = appOrPkg.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            Field field;
            try {
                field = k.getDeclaredField("packageName");
            } catch (NoSuchFieldException ignored) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(appOrPkg);
                return value == null ? null : value.toString();
            } catch (Throwable t) {
                XLog.w("Failed to read packageName from "
                        + appOrPkg.getClass().getName() + ": " + t);
                return null;
            }
        }
        return null;
    }
}

package deltazero.amarok.xposed.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Helpers around the hidden {@code android.content.pm.ParceledListSlice} used by
 * {@code PackageManagerService} listings.
 *
 * <p>Rewritten on top of plain {@code java.lang.reflect}: the EzXHelper helpers this used to rely on
 * ({@code ClassUtils}, {@code ObjectUtils}) call into the legacy Xposed API, which modules targeting
 * libxposed API 102 are not allowed to touch.</p>
 */
public class ParceledListSliceUtil {

    // Written once from onSystemServerStarting() and read later from arbitrary binder threads.
    private static volatile Class<?> parceledListSliceClass;
    private static volatile Method getListMethod;
    private static volatile Constructor<?> listConstructor;

    private ParceledListSliceUtil() {
    }

    public static void init(ClassLoader classLoader) {
        XLog.d("Initializing ParceledListSliceUtil...");
        try {
            parceledListSliceClass = Class.forName("android.content.pm.ParceledListSlice",
                    false, classLoader);
            getListMethod = parceledListSliceClass.getMethod("getList");
            getListMethod.setAccessible(true);
            listConstructor = parceledListSliceClass.getConstructor(List.class);
            listConstructor.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        XLog.d("ParceledListSliceUtil initialized.");
    }

    /**
     * @noinspection unchecked
     */
    public static <T> List<T> sliceToList(Object slice) {
        if (getListMethod == null) {
            throw new IllegalStateException("ParceledListSliceUtil is not initialized");
        }
        try {
            return (List<T>) getListMethod.invoke(slice);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object listToSlice(List<?> list) {
        if (listConstructor == null) {
            throw new IllegalStateException("ParceledListSliceUtil is not initialized");
        }
        try {
            return listConstructor.newInstance(list);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

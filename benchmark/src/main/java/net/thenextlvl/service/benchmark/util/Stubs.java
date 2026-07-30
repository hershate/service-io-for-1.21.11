package net.thenextlvl.service.benchmark.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * Minimal dependency-free stubbing utility for the benchmark.
 *
 * <p>The plugin code is deeply tied to Bukkit / Paper / Vault types, none of which can run
 * without a live server. Instead of pulling in a mocking framework (which would add a network
 * dependency that may be unreachable), we synthesize {@link java.lang.reflect.Proxy} instances
 * that implement the required interfaces and return benign defaults. This lets the benchmark
 * exercise the real production classes (e.g. {@code PlaceholderStore}) end-to-end with zero
 * third-party test libraries.</p>
 */
public final class Stubs {
    private Stubs() {
    }

    /**
     * Creates a proxy implementing {@code type} whose every method returns the default value
     * for its return type (recursively stubbing nested interface return types).
     */
    @SuppressWarnings("unchecked")
    public static <T> T stub(final Class<T> type) {
        return (T) Proxy.newProxyInstance(
                classLoader(type),
                new Class<?>[]{type},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    /**
     * Creates a proxy implementing {@code type} whose method calls are routed to {@code handler}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T stub(final Class<T> type, final InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(classLoader(type), new Class<?>[]{type}, handler);
    }

    /**
     * Returns the default value for {@code type}: {@code 0}/{@code false} for primitives,
     * a nested stub for interface types, and {@code null} for concrete class types.
     */
    public static Object defaultValue(final Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == byte.class || type == Byte.class) return (byte) 0;
        if (type == short.class || type == Short.class) return (short) 0;
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 0L;
        if (type == float.class || type == Float.class) return 0f;
        if (type == double.class || type == Double.class) return 0d;
        if (type == char.class || type == Character.class) return '\0';
        if (type.isInterface()) return stub(type);
        if (type == Object.class) return null;
        return null;
    }

    private static ClassLoader classLoader(final Class<?> type) {
        final ClassLoader loader = type.getClassLoader();
        return loader != null ? loader : ClassLoader.getSystemClassLoader();
    }
}

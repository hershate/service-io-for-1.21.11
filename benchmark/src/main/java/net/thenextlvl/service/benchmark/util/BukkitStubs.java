package net.thenextlvl.service.benchmark.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * A single {@link java.lang.reflect.InvocationHandler} that fakes the small slice of the
 * Bukkit / Paper service graph the benchmark touches: {@link Plugin} -> Server ->
 * ServicesManager (returns a fixed provider) / PluginManager (no-op listener registration),
 * plus {@link OfflinePlayer} identity. All other calls fall back to {@link Stubs#defaultValue}.
 */
public final class BukkitStubs {
    /** A non-null sentinel returned by {@code ServicesManager.load(...)} so stores report enabled. */
    public static final Object PROVIDER = new Object();

    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private BukkitStubs() {
    }

    public static Plugin plugin() {
        return Stubs.stub(Plugin.class, BukkitStubs::handle);
    }

    public static OfflinePlayer player() {
        return Stubs.stub(OfflinePlayer.class, BukkitStubs::handle);
    }

    private static Object handle(final Object proxy, final Method method, final Object[] args) {
        return switch (method.getName()) {
            case "load" -> PROVIDER;                 // ServicesManager.load(Class) -> enabled provider
            case "registerEvents" -> null;           // PluginManager.registerEvents -> void
            // Accessors return a nested proxy routed through the same handler, so that
            // server.getServicesManager().load(...) resolves to PROVIDER, etc.
            case "getServer", "getServicesManager", "getPluginManager", "getComponentLogger" ->
                    nested(method.getReturnType());
            case "getUniqueId" -> PLAYER_UUID;
            case "getName" -> "Bench";
            case "getPlayer" -> null;
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "BenchStub";
            case "equals" -> proxy == args[0];
            default -> Stubs.defaultValue(method.getReturnType());
        };
    }

    private static Object nested(final Class<?> returnType) {
        return returnType.isInterface() ? Stubs.stub(returnType, BukkitStubs::handle) : null;
    }
}

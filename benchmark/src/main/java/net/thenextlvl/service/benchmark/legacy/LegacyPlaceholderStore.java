package net.thenextlvl.service.benchmark.legacy;

import net.thenextlvl.service.plugin.placeholder.api.PlaceholderResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <b>Verbatim, frozen copy of the PRE-optimization {@code PlaceholderStore.resolve()} /
 * {@code registerResolver()} implementation.</b> It is kept here so the benchmark can run the
 * old dispatch logic and the new logic back-to-back in the same JVM for a credible A/B
 * comparison. Only the package differs; every line of {@link #resolve} and
 * {@link #registerResolver(String, PlaceholderResolver)} matches the original.
 *
 * <p>Do not "improve" this class — its whole purpose is to preserve the baseline.</p>
 */
public abstract class LegacyPlaceholderStore<T> implements Listener {
    private final Map<Pattern, PlaceholderResolver<T>> resolvers = new HashMap<>();
    private final Class<T> providerClass;
    private volatile @Nullable T provider;

    protected final Plugin plugin;

    public LegacyPlaceholderStore(final Plugin plugin, final Class<T> providerClass) {
        this.plugin = plugin;
        this.providerClass = providerClass;
        updateServices();
        registerResolvers();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    protected final void registerResolver(final String regex, final PlaceholderResolver<T> resolver) {
        resolvers.put(Pattern.compile(regex.replace("%s", "([^{}]+)")), resolver);
    }

    protected abstract void registerResolvers();

    public final @Nullable String resolve(final OfflinePlayer player, final String params) {
        try {
            if (provider != null) for (final var entry : resolvers.entrySet()) {
                final var matcher = entry.getKey().matcher(params);
                if (!matcher.matches()) continue;
                final var resolved = entry.getValue().resolve(provider, player, matcher);
                if (resolved != null) return resolved;
            }
            return null;
        } catch (final Exception e) {
            final var name = player.getName() != null ? player.getName() : player.getUniqueId().toString();
            plugin.getComponentLogger().warn("Failed to resolve placeholder '{}' for player {}", params, name, e);
            return null;
        }
    }

    public final boolean isEnabled() {
        return provider != null;
    }

    @EventHandler
    public void onServiceRegister(final ServiceRegisterEvent event) {
        if (providerClass.isInstance(event.getProvider().getProvider())) updateServices();
    }

    @EventHandler
    public void onServiceUnregister(final ServiceUnregisterEvent event) {
        if (providerClass.isInstance(event.getProvider().getProvider())) updateServices();
    }

    private void updateServices() {
        this.provider = plugin.getServer().getServicesManager().load(providerClass);
    }
}

package net.thenextlvl.service.plugin.placeholder.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class PlaceholderStore<T> implements Listener {
    /**
     * Regex metacharacters. A registered expression is dispatched as an O(1) exact-match
     * <em>literal</em> only when it contains neither a {@code %s} placeholder nor any of these.
     */
    private static final Pattern REGEX_META = Pattern.compile("[.\\^$*+?()\\[\\]{}|\\\\]");

    /**
     * Exact-match resolvers (no {@code %s}, no metacharacters) keyed by their literal text.
     * The common placeholders ({@code balance}, {@code prefix}, {@code group}, ...) land here and
     * resolve via a single hash lookup instead of a linear regex scan.
     */
    private final Map<String, Entry<T>> literals = new HashMap<>();

    /**
     * Regex resolvers ({@code %s} placeholders) bucketed by their literal first segment (the text
     * before the first {@code %s}, e.g. {@code account_}). At resolve time only the one bucket whose
     * prefix the input actually starts with is consulted, so an {@code account_<uuid>} input never
     * runs the {@code balance_*} regexes. Buckets are scanned longest-prefix-first so that a more
     * specific bucket (e.g. {@code balance_currency_}) is tried before a less specific one
     * ({@code balance_}); within a bucket, entries are most-specific first. Together this makes
     * overlapping patterns resolve <em>deterministically</em> (the previous HashMap-based storage
     * made the order non-deterministic, so e.g. {@code balance_currency_%s} vs {@code balance_%s}
     * for {@code balance_currency_USD} was decided by hash seed).
     */
    private final Map<String, List<Entry<T>>> buckets = new HashMap<>();

    /**
     * Regex resolvers whose first segment contains metacharacters and therefore cannot be safely
     * dispatched by a literal {@code startsWith} bucket key. Always regex-tested, most-specific
     * first. Empty for all current registrations.
     */
    private final List<Entry<T>> fallback = new ArrayList<>();

    private List<String> sortedPrefixes = List.of();

    private final Class<T> providerClass;
    private volatile @Nullable T provider;

    protected final Plugin plugin;

    public PlaceholderStore(final Plugin plugin, final Class<T> providerClass) {
        this.plugin = plugin;
        this.providerClass = providerClass;
        updateServices();
        registerResolvers();
        for (final var bucket : buckets.values()) bucket.sort(Entry.SPECIFICITY);
        fallback.sort(Entry.SPECIFICITY);
        sortedPrefixes = buckets.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        plugin.getComponentLogger().info("Registered placeholders for {} ({})",
                providerClass.getSimpleName(), providerClass.getSimpleName());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    protected final void registerResolver(final String regex, final PlaceholderResolver<T> resolver) {
        final var pattern = Pattern.compile(regex.replace("%s", "([^{}]+)"));
        final var segments = regex.split("%s", -1);
        final var entry = new Entry<>(
                pattern, segments, allLiteral(segments), literalChars(regex), groupCount(regex), resolver);
        if (isLiteral(regex)) {
            literals.put(regex, entry);
        } else if (isLiteral(segments[0])) {
            buckets.computeIfAbsent(segments[0], key -> new ArrayList<>()).add(entry);
        } else {
            fallback.add(entry);
        }
    }

    protected abstract void registerResolvers();

    public final @Nullable String resolve(final OfflinePlayer player, final String params) {
        try {
            if (provider == null) return null;
            // Fast path: O(1) exact-match lookup, no regex engine involved.
            final var literal = literals.get(params);
            if (literal != null) {
                return literal.resolver().resolve(provider, player, literal.matcher(params));
            }
            // Pattern path: consult only the matching prefix bucket (longest first), then fallback.
            for (final var prefix : sortedPrefixes) {
                if (!params.startsWith(prefix)) continue;
                final var resolved = resolveBucket(buckets.get(prefix), player, params);
                if (resolved != null) return resolved;
            }
            final var fallbackResolved = resolveBucket(fallback, player, params);
            return fallbackResolved;
        } catch (final Exception e) {
            final var name = player.getName() != null ? player.getName() : player.getUniqueId().toString();
            plugin.getComponentLogger().warn("Failed to resolve placeholder '{}' for player {}", params, name, e);
            return null;
        }
    }

    private @Nullable String resolveBucket(final List<Entry<T>> bucket, final OfflinePlayer player, final String params) {
        for (final var entry : bucket) {
            // Sound pre-filter: if any required literal anchor is absent the regex cannot match,
            // so skip it without running (and backtracking) the regex.
            if (entry.allLiteral() && !anchorsPresent(entry.segments(), params)) continue;
            final var matcher = entry.matcher(params);
            if (!matcher.matches()) continue;
            final var resolved = entry.resolver().resolve(provider, player, matcher);
            if (resolved != null) return resolved;
        }
        return null;
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

    private static boolean isLiteral(final String regex) {
        return !regex.contains("%s") && !REGEX_META.matcher(regex).find();
    }

    /**
     * Sound pre-filter: a pattern can only match if the input starts with its first literal segment
     * and contains every subsequent literal segment. Absence of any anchor proves no match.
     */
    private static boolean anchorsPresent(final String[] segments, final String params) {
        if (!params.startsWith(segments[0])) return false;
        for (int i = 1; i < segments.length; i++) {
            final var segment = segments[i];
            if (!segment.isEmpty() && params.indexOf(segment) < 0) return false;
        }
        return true;
    }

    private static boolean allLiteral(final String[] segments) {
        for (final var segment : segments) {
            if (!isLiteral(segment)) return false;
        }
        return true;
    }

    /** Number of literal (non-placeholder) characters — more means more specific. */
    private static int literalChars(final String regex) {
        return regex.length() - 2 * countOf(regex, "%s");
    }

    private static int groupCount(final String regex) {
        return countOf(regex, "%s");
    }

    private static int countOf(final String haystack, final String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private record Entry<T>(
            Pattern pattern, String[] segments, boolean allLiteral,
            int literalChars, int groupCount, PlaceholderResolver<T> resolver
    ) {
        Matcher matcher(final String params) {
            return pattern.matcher(params);
        }

        /**
         * Most-specific first: longer literal prefix wins, ties broken by more capture groups.
         * Descending order is expressed via negation to keep the comparator chain unambiguous.
         */
        static final Comparator<Entry<?>> SPECIFICITY = Comparator
                .comparingInt((Entry<?> e) -> -e.literalChars)
                .thenComparingInt(e -> -e.groupCount);
    }
}

package net.thenextlvl.service.benchmark.placeholder;

import net.thenextlvl.service.benchmark.legacy.LegacyPlaceholderStore;
import net.thenextlvl.service.benchmark.util.BenchResult;
import net.thenextlvl.service.benchmark.util.BukkitStubs;
import net.thenextlvl.service.benchmark.util.MicroBench;
import net.thenextlvl.service.plugin.placeholder.api.PlaceholderResolver;
import net.thenextlvl.service.plugin.placeholder.api.PlaceholderStore;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Benchmarks placeholder dispatch ({@code PlaceholderStore.resolve}) before vs after.
 *
 * <p>Both stores register an identical, realistic resolver set (6 literal + 8 pattern resolvers,
 * including several overlapping multi-segment patterns modelled on the real
 * {@code UnlockedEconomyPlaceholderStore}). The resolvers ignore their inputs and return a
 * constant, so this isolates <b>dispatch cost</b> (regex matching vs hash lookup) — exactly what
 * the optimization changes. Provider-call overhead, which is identical before/after, is excluded.</p>
 */
public final class PlaceholderDispatchBenchmark {
    private PlaceholderDispatchBenchmark() {
    }

    private static final Plugin PLUGIN = BukkitStubs.plugin();
    private static final OfflinePlayer PLAYER = BukkitStubs.player();

    // Realistic placeholder inputs to exercise literal hits, overlapping patterns, and misses.
    private static final String UUID = "00000000-0000-0000-0000-000000000001";
    private static final List<String> INPUTS = List.of(
            "balance",                              // literal, most common
            "accounts_count",                       // literal
            "balance_currency_USD",                 // overlapping patterns (specificity)
            "account_" + UUID,                      // pattern hit
            "totally_unknown_placeholder"           // miss
    );

    public static List<BenchResult[]> run(final int warmup, final int rounds, final int perRound) {
        final var newer = new DispatchStore(PLUGIN);      // optimized PlaceholderStore
        final var legacy = new LegacyDispatchStore(PLUGIN); // pre-optimization dispatch

        verify(newer, legacy);

        final List<BenchResult[]> results = new java.util.ArrayList<>();
        for (final String input : INPUTS) {
            final BenchResult after = MicroBench.measure(
                    "new:" + input, i -> consume(newer.resolve(PLAYER, input)), warmup, rounds, perRound);
            final BenchResult before = MicroBench.measure(
                    "old:" + input, i -> consume(legacy.resolve(PLAYER, input)), warmup, rounds, perRound);
            results.add(new BenchResult[]{before, after});
        }
        return results;
    }

    private static long consume(final String result) {
        return result == null ? 0L : result.hashCode();
    }

    private static void verify(final DispatchStore newer, final LegacyDispatchStore legacy) {
        // Literal dispatch correctness.
        if (!"L:balance".equals(newer.resolve(PLAYER, "balance"))) {
            throw new AssertionError("new store literal resolve failed");
        }
        // Specificity ordering: balance_currency_%s must win over balance_%s.
        final String specific = newer.resolve(PLAYER, "balance_currency_USD");
        if (!"P:balance_currency_%s".equals(specific)) {
            throw new AssertionError("specificity ordering failed: " + specific);
        }
        // The legacy implementation is non-deterministic here (HashMap order); only assert presence.
        if (legacy.resolve(PLAYER, "balance_currency_USD") == null) {
            throw new AssertionError("legacy store should resolve balance_currency_USD");
        }
        System.out.println("  [verify] new store 'balance_currency_USD' -> '" + specific
                + "' (specificity correct); legacy is non-deterministic for this input.");
    }

    /** Registers the same resolver set against the optimized {@link PlaceholderStore}. */
    private static final class DispatchStore extends PlaceholderStore<Object> {
        DispatchStore(final Plugin plugin) {
            super(plugin, Object.class);
        }

        @Override
        protected void registerResolvers() {
            registerAll(this::registerResolver);
        }
    }

    /** Registers the same resolver set against the frozen legacy store. */
    private static final class LegacyDispatchStore extends LegacyPlaceholderStore<Object> {
        LegacyDispatchStore(final Plugin plugin) {
            super(plugin, Object.class);
        }

        @Override
        protected void registerResolvers() {
            registerAll(this::registerResolver);
        }
    }

    /** Shared resolver registration so both stores are identical. */
    private static void registerAll(final Registrar registrar) {
        // literals
        registrar.put("balance", constResolver("L:balance"));
        registrar.put("balanceformatted", constResolver("L:balanceformatted"));
        registrar.put("accounts", constResolver("L:accounts"));
        registrar.put("accounts_count", constResolver("L:accounts_count"));
        registrar.put("currency", constResolver("L:currency"));
        registrar.put("currencyplural", constResolver("L:currencyplural"));
        // patterns (note overlaps: balance_%s vs balance_currency_%s, account_%s vs account_%s_currency_%s)
        registrar.put("balance_%s", constResolver("P:balance_%s"));
        registrar.put("balance_currency_%s", constResolver("P:balance_currency_%s"));
        registrar.put("balance_currency_%s_world_%s", constResolver("P:balance_currency_%s_world_%s"));
        registrar.put("balanceformatted_%s", constResolver("P:balanceformatted_%s"));
        registrar.put("account_%s", constResolver("P:account_%s"));
        registrar.put("account_%s_currency_%s", constResolver("P:account_%s_currency_%s"));
        registrar.put("account_%s_currency_%s_world_%s", constResolver("P:account_%s_currency_%s_world_%s"));
        registrar.put("can_%s_%s", constResolver("P:can_%s_%s"));
    }

    private static PlaceholderResolver<Object> constResolver(final String tag) {
        return (provider, player, matcher) -> tag;
    }

    @FunctionalInterface
    private interface Registrar {
        void put(String regex, PlaceholderResolver<Object> resolver);
    }
}

package net.thenextlvl.service.benchmark.placeholder;

import net.thenextlvl.service.benchmark.util.BukkitStubs;
import net.thenextlvl.service.plugin.placeholder.api.PlaceholderResolver;
import net.thenextlvl.service.plugin.placeholder.api.PlaceholderStore;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Correctness gate for the optimized {@link PlaceholderStore} dispatch.
 *
 * <p>Registers the <b>full</b> resolver set of the real {@code UnlockedEconomyPlaceholderStore}
 * (6 literals + 12 patterns, including every overlapping multi-segment pattern) and asserts that
 * each documented placeholder input resolves to its intended resolver. Because the optimization
 * changed how overlapping patterns are ordered (specificity, was non-deterministic HashMap order),
 * this verifies the red line: no documented placeholder regressed.
 *
 * <p>Each resolver returns its own regex string, so the resolved value identifies which resolver
 * won. The store is the real (optimized) production class — only {@code registerResolvers} is
 * supplied here.</p>
 */
public final class PlaceholderCorrectnessBenchmark {
    private PlaceholderCorrectnessBenchmark() {
    }

    private static final String UUID = "00000000-0000-0000-0000-000000000001";

    /** Documented inputs and the resolver that MUST win for each. Insertion-ordered for stable reporting. */
    private static final Map<String, String> CASES = cases();

    public static int check() {
        final var store = new UnlockedShapedStore(BukkitStubs.plugin());
        final OfflinePlayer player = BukkitStubs.player();

        int passed = 0;
        for (final var entry : CASES.entrySet()) {
            final var input = entry.getKey();
            final var expected = entry.getValue();
            final var actual = store.resolve(player, input);
            if (!expected.equals(actual)) {
                throw new AssertionError("placeholder '" + input + "': expected resolver '"
                        + expected + "' but got '" + actual + "'");
            }
            passed++;
        }
        return passed;
    }

    private static Map<String, String> cases() {
        final var m = new LinkedHashMap<String, String>();
        // literals
        m.put("balance", "balance");
        m.put("balanceformatted", "balanceformatted");
        m.put("accounts", "accounts");
        m.put("accounts_count", "accounts_count");
        m.put("currency", "currency");
        m.put("currencyplural", "currencyplural");
        // single-segment patterns
        m.put("balance_survival", "balance_%s");
        m.put("balanceformatted_survival", "balanceformatted_%s");
        m.put("account_" + UUID, "account_%s");
        // two-segment (overlaps with single-segment)
        m.put("balance_currency_USD", "balance_currency_%s");
        m.put("balanceformatted_currency_USD", "balanceformatted_currency_%s");
        m.put("account_" + UUID + "_currency_USD", "account_%s_currency_%s");
        // three-segment (overlaps with two- and single-segment)
        m.put("balance_currency_USD_world_survival", "balance_currency_%s_world_%s");
        m.put("balanceformatted_currency_USD_world_survival", "balanceformatted_currency_%s_world_%s");
        m.put("account_" + UUID + "_currency_USD_formatted", "account_%s_currency_%s_formatted");
        m.put("account_" + UUID + "_currency_USD_world_survival", "account_%s_currency_%s_world_%s");
        // four-segment (overlaps with all shorter account patterns)
        m.put("account_" + UUID + "_currency_USD_world_survival_formatted", "account_%s_currency_%s_world_%s_formatted");
        // can_ (two generic segments)
        m.put("can_deposit_" + UUID, "can_%s_%s");
        return m;
    }

    private static final class UnlockedShapedStore extends PlaceholderStore<Object> {
        UnlockedShapedStore(final Plugin plugin) {
            super(plugin, Object.class);
        }

        @Override
        protected void registerResolvers() {
            // literals
            for (final var literal : new String[]{"balance", "balanceformatted", "accounts", "accounts_count", "currency", "currencyplural"}) {
                registerResolver(literal, tag(literal));
            }
            // patterns — order intentionally not specificity-sorted, to prove the store sorts itself
            registerResolver("balance_%s", tag("balance_%s"));
            registerResolver("balance_currency_%s", tag("balance_currency_%s"));
            registerResolver("balance_currency_%s_world_%s", tag("balance_currency_%s_world_%s"));
            registerResolver("balanceformatted_%s", tag("balanceformatted_%s"));
            registerResolver("balanceformatted_currency_%s", tag("balanceformatted_currency_%s"));
            registerResolver("balanceformatted_currency_%s_world_%s", tag("balanceformatted_currency_%s_world_%s"));
            registerResolver("account_%s", tag("account_%s"));
            registerResolver("account_%s_currency_%s", tag("account_%s_currency_%s"));
            registerResolver("account_%s_currency_%s_formatted", tag("account_%s_currency_%s_formatted"));
            registerResolver("account_%s_currency_%s_world_%s", tag("account_%s_currency_%s_world_%s"));
            registerResolver("account_%s_currency_%s_world_%s_formatted", tag("account_%s_currency_%s_world_%s_formatted"));
            registerResolver("can_%s_%s", tag("can_%s_%s"));
        }
    }

    private static PlaceholderResolver<Object> tag(final String regex) {
        return (provider, player, matcher) -> regex;
    }
}

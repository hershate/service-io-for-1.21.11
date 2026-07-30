package net.thenextlvl.service.benchmark.entitytype;

import net.thenextlvl.service.benchmark.util.BenchResult;
import net.thenextlvl.service.benchmark.util.MicroBench;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Benchmarks {@code CharacterController#getEntityType} / {@code EntityHologramLine#getEntityType}.
 *
 * <p>Both call sites stream {@code EntityType.values()} on every invocation. {@code values()}
 * clones the internal array each call, and the stream re-runs the filters every time. The
 * optimization precomputes a lookup table once. The legacy and optimized algorithms are
 * reproduced here verbatim for an apples-to-apples comparison.</p>
 */
public final class EntityTypeBenchmark {
    private EntityTypeBenchmark() {
    }

    private static final Class<? extends Entity> PLAYER_CLASS = Player.class;
    private static final Class<? extends Entity> ZOMBIE_CLASS = Zombie.class;

    // Optimized lookup tables (built once).
    private static final Map<Class<? extends Entity>, EntityType> EXACT_MAP = buildExactMap();
    private static final List<EntityType> NON_NULL_TYPES = buildNonNullList();

    public static List<BenchResult[]> run(final int warmup, final int rounds, final int perRound) {
        final var results = new java.util.ArrayList<BenchResult[]>();

        // Character-style: exact class match.
        results.add(new BenchResult[]{
                MicroBench.measure("old:char exact (Player)", i -> legacyExact(PLAYER_CLASS).ordinal(), warmup, rounds, perRound),
                MicroBench.measure("new:char exact (Player)", i -> optimizedExact(PLAYER_CLASS).ordinal(), warmup, rounds, perRound)
        });
        results.add(new BenchResult[]{
                MicroBench.measure("old:char exact (Zombie)", i -> legacyExact(ZOMBIE_CLASS).ordinal(), warmup, rounds, perRound),
                MicroBench.measure("new:char exact (Zombie)", i -> optimizedExact(ZOMBIE_CLASS).ordinal(), warmup, rounds, perRound)
        });

        // Hologram-style: assignable class match.
        results.add(new BenchResult[]{
                MicroBench.measure("old:hologram assignable (Player)", i -> legacyAssignable(PLAYER_CLASS).map(EntityType::ordinal).orElse(-1), warmup, rounds, perRound),
                MicroBench.measure("new:hologram assignable (Player)", i -> optimizedAssignable(PLAYER_CLASS).map(EntityType::ordinal).orElse(-1), warmup, rounds, perRound)
        });
        return results;
    }

    // --- legacy (verbatim from the codebase) ---

    private static EntityType legacyExact(final Class<? extends Entity> type) {
        return Arrays.stream(EntityType.values())
                .filter(entityType -> type.equals(entityType.getEntityClass()))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Invalid entity type: " + type.getName()));
    }

    private static Optional<EntityType> legacyAssignable(final Class<? extends Entity> entityClass) {
        return Arrays.stream(EntityType.values())
                .filter(type -> type.getEntityClass() != null)
                .filter(type -> type.getEntityClass().isAssignableFrom(entityClass))
                .findAny();
    }

    // --- optimized (precomputed tables) ---

    private static EntityType optimizedExact(final Class<? extends Entity> type) {
        final var found = EXACT_MAP.get(type);
        if (found == null) throw new IllegalArgumentException("Invalid entity type: " + type.getName());
        return found;
    }

    private static Optional<EntityType> optimizedAssignable(final Class<? extends Entity> entityClass) {
        for (final var type : NON_NULL_TYPES) {
            if (type.getEntityClass().isAssignableFrom(entityClass)) return Optional.of(type);
        }
        return Optional.empty();
    }

    private static Map<Class<? extends Entity>, EntityType> buildExactMap() {
        final Map<Class<? extends Entity>, EntityType> map = new HashMap<>();
        for (final var type : EntityType.values()) {
            final var entityClass = type.getEntityClass();
            if (entityClass != null) map.put(entityClass, type);
        }
        return Map.copyOf(map);
    }

    private static List<EntityType> buildNonNullList() {
        return Arrays.stream(EntityType.values())
                .filter(type -> type.getEntityClass() != null)
                .toList();
    }
}

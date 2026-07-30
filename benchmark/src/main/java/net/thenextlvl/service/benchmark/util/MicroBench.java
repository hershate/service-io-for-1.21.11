package net.thenextlvl.service.benchmark.util;

import java.util.Arrays;

/**
 * Tiny hand-rolled microbenchmark harness.
 *
 * <p>This deliberately avoids JMH (an extra network dependency that may be unreachable here).
 * It measures wall-clock {@code System.nanoTime()} per operation after a warm-up phase and
 * reports mean / min / median across several measurement rounds. Every operation returns a
 * {@code long} that is folded into a sink to defeat dead-code elimination.</p>
 *
 * <p><b>Caveats:</b> no separate JVM forks, JIT state is shared between the before/after variants
 * (which is acceptable — indeed desirable — for a relative A/B comparison run in one process),
 * and results are most meaningful for relative speed-ups rather than absolute numbers.</p>
 */
public final class MicroBench {
    private MicroBench() {
    }

    /**
     * Measures {@code op}.
     *
     * @param name          scenario label
     * @param op            returns a {@code long} derived from the work; its result is consumed
     * @param warmupIters   iterations to run before measuring (lets JIT settle)
     * @param rounds        number of independent measurement rounds
     * @param itersPerRound operations timed per round
     */
    public static BenchResult measure(
            final String name,
            final IntToLong op,
            final int warmupIters,
            final int rounds,
            final int itersPerRound
    ) {
        long sink = 0;
        for (int i = 0; i < warmupIters; i++) {
            sink ^= op.apply(i);
        }

        final long[] samples = new long[rounds];
        for (int r = 0; r < rounds; r++) {
            final long start = System.nanoTime();
            for (int i = 0; i < itersPerRound; i++) {
                sink ^= op.apply(i);
            }
            samples[r] = (System.nanoTime() - start) / itersPerRound;
        }

        // Prevent the JVM from optimizing away the entire loop body.
        if (sink == 0xDEADBEEFL) {
            System.out.println(sink);
        }

        return analyze(name, samples);
    }

    private static BenchResult analyze(final String name, final long[] samples) {
        final long[] sorted = samples.clone();
        Arrays.sort(sorted);
        double sum = 0;
        for (final long s : samples) sum += s;
        final double mean = sum / samples.length;
        final long min = sorted[0];
        final long median = sorted[samples.length / 2];
        return new BenchResult(name, samples, mean, min, median);
    }

    @FunctionalInterface
    public interface IntToLong {
        long apply(int index);
    }
}

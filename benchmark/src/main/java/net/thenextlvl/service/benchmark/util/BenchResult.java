package net.thenextlvl.service.benchmark.util;

/**
 * Aggregated statistics for one measured scenario (nanoseconds per operation).
 *
 * @param name      scenario label
 * @param samplesNs per-round measured ns/op values
 * @param meanNs    arithmetic mean of {@link #samplesNs}
 * @param minNs     minimum (least-noisy) of {@link #samplesNs}
 * @param medianNs  median of {@link #samplesNs}
 */
public record BenchResult(
        String name,
        long[] samplesNs,
        double meanNs,
        long minNs,
        long medianNs
) {
}

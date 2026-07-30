package net.thenextlvl.service.benchmark;

import net.thenextlvl.service.benchmark.entitytype.EntityTypeBenchmark;
import net.thenextlvl.service.benchmark.placeholder.PlaceholderDispatchBenchmark;
import net.thenextlvl.service.benchmark.util.BenchResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point. Runs every benchmark and reports a before/after comparison.
 *
 * <p>Usage: {@code BenchmarkRunner [warmupIters] [rounds] [itersPerRound]}.
 * Defaults: warmup 200_000, rounds 15, itersPerRound 200_000.</p>
 *
 * <p>Each benchmark returns pairs {@code [before, after]}; the runner reports the median ns/op
 * of each and the speed-up {@code before / after}. Median is used as the headline because it is
 * robust to GC / scheduling outliers.</p>
 */
public final class BenchmarkRunner {
    private static final Path OUTPUT = Path.of("results.txt");

    public static void main(final String[] args) throws IOException {
        final int warmup = args.length > 0 ? Integer.parseInt(args[0]) : 200_000;
        final int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 15;
        final int perRound = args.length > 2 ? Integer.parseInt(args[2]) : 200_000;

        final List<String> report = new ArrayList<>();
        report.add("# ServiceIO performance benchmark — before vs after optimization");
        report.add("# warmup=" + warmup + " rounds=" + rounds + " iters/round=" + perRound);
        report.add("# units: nanoseconds per operation (median across rounds)");
        report.add("");
        report.add(header());

        System.out.println(report.get(0));
        System.out.println(report.get(1));

        runSection("Placeholder dispatch (resolve)", report,
                PlaceholderDispatchBenchmark.run(warmup, rounds, perRound));
        runSection("EntityType lookup", report,
                EntityTypeBenchmark.run(warmup, rounds, perRound));

        if (OUTPUT.getParent() != null) Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, String.join("\n", report) + "\n");
        System.out.println();
        System.out.println("Results written to " + OUTPUT.toAbsolutePath());
    }

    private static void runSection(final String title, final List<String> report, final List<BenchResult[]> pairs) {
        report.add("");
        report.add("# " + title);
        System.out.println();
        System.out.println("# " + title);
        for (final BenchResult[] pair : pairs) {
            final BenchResult before = pair[0], after = pair[1];
            final String row = row(title, before, after);
            report.add(row);
            System.out.println(row);
        }
    }

    private static String header() {
        return String.format("%-44s %14s %14s %10s", "scenario", "before ns/op", "after ns/op", "speedup");
    }

    private static String row(final String section, final BenchResult before, final BenchResult after) {
        final double speedup = after.medianNs() == 0 ? Double.POSITIVE_INFINITY
                : (double) before.medianNs() / after.medianNs();
        final String label = after.name().replaceFirst("^new:", "");
        return String.format("%-44s %14d %14d %9.2fx",
                label, before.medianNs(), after.medianNs(), speedup);
    }
}

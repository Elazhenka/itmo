package info.kgeorgiy.ja.elagina.iterative;

import info.kgeorgiy.java.advanced.iterative.NewScalarIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Class, providing common patterns for parallel {@code Scalar} processing
 *
 * @author Elagina Alena
 */

public class IterativeParallelism implements NewScalarIP {

    private final ParallelMapper mapper;

    /**
     * Creates default {@code IterativeParallelism} instance
     */
    public IterativeParallelism() {
        mapper = null;
    }

    /**
     * Creates {@code IterativeParallelism} instance which use given threads pool
     *
     * @param mapper A {@code ParallelMapper} instance
     */
    public IterativeParallelism(final ParallelMapper mapper) {
        this.mapper = mapper;
    }

    private <T, S, R> R runThreads(
            final int maxThreads,
            final List<? extends T> values,
            final Function<Stream<? extends T>, ? extends S> pipeProcessor,
            final Function<Stream<S>, R> joiner,
            int step
    )
            throws InterruptedException {
        if (maxThreads <= 0) {
            throw new IllegalArgumentException("Number of threads must be > 0");
        }

        StepList<T> list = new StepList<>(values, step);
        // :NOTE: isEmpty()
        if (list.size() == 0) {
            return joiner.apply(Stream.of());
        }

        final List<S> results;
        final int threads = Math.min(maxThreads, list.size());
        final List<List<? extends T>> tasks = split(list, threads);

        if (mapper != null) {
            results = mapper.map(l -> pipeProcessor.apply(l.stream()), tasks);
        } else {
            results = map(pipeProcessor, threads, tasks);
        }

        return joiner.apply(results.stream());
    }

    private static <T> List<List<? extends T>> split(final StepList<? extends T> values, final int threads) {
        final int elem = values.size() / threads;
        int rem = values.size() % threads;

        final List<List<? extends T>> split = new ArrayList<>();

        int start = 0;
        for (int i = 0; i < threads; i++) {
            int e = start + elem + (--rem >= 0 ? 1 : 0);
            split.add(values.subList(start, e));
            start = e;
        }

        return split;
    }

    private static <T, S> List<S> map(
            final Function<Stream<? extends T>, ? extends S> pipeProcessor,
            final int threads,
            final List<List<? extends T>> tasks
    )
            throws InterruptedException {
        final List<S> results = new ArrayList<>(Collections.nCopies(threads, null));
        final List<Thread> workers = createThreads(pipeProcessor, threads, tasks, results);
        interruptedException(workers);
        return results;
    }

    private static <T, S> List<Thread> createThreads(
            Function<Stream<? extends T>, ? extends S> pipeProcessor,
            int threads,
            List<List<? extends T>> tasks,
            List<S> results
    ) {
        return IntStream.range(0, threads)
                // :NOTE: .mapToObj(i -> () -> ..).map(Thread::new)
                .mapToObj(i -> new Thread(
                        () -> results.set(i, pipeProcessor.apply(tasks.get(i).stream()))))
                .peek(Thread::start)
                .toList();
    }

    private static void interruptedException(List<Thread> workers) throws InterruptedException {
        InterruptedException exception = null;

        for (int i = 0; i < workers.size(); i++) {
            try {
                workers.get(i).join();
            } catch (final InterruptedException e) {
                i--;
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
                workers.subList(i, workers.size()).forEach(Thread::interrupt);
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Returns the first maximum
     *
     * @param threads    number of concurrent threads.
     * @param values     values to get maximum of.
     * @param comparator value comparator.
     * @throws InterruptedException when executing thread was interrupted
     */
    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator, int step)
            throws InterruptedException {
        return runThreads(threads, values, s -> s.max(comparator).orElseThrow(),
                s -> s.max(comparator).orElseThrow(), step);
    }

    /**
     * Returns the first minimum
     *
     * @param threads    number of concurrent threads.
     * @param values     values to get minimum of.
     * @param comparator value comparator.
     * @throws InterruptedException when executing thread was interrupted
     */
    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator, int step)
            throws InterruptedException {
        // :NOTE: Выразить через maximum (или наоборот)
        return runThreads(threads, values, s -> s.min(comparator).orElseThrow(),
                s -> s.min(comparator).orElseThrow(), step);
    }

    /**
     * Check's that all elements of the list satisfy the predicate
     *
     * @param threads   number of concurrent threads.
     * @param values    values to test.
     * @param predicate test predicate.
     * @throws InterruptedException when executing thread was interrupted
     */
    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate, int step)
            throws InterruptedException {
        return !any(threads, values, predicate.negate(), step);
    }

    /**
     * Check's that there is a list item that satisfies the predicate
     *
     * @param threads   number of concurrent threads.
     * @param values    values to test.
     * @param predicate test predicate.
     * @throws InterruptedException when executing thread was interrupted
     */
    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate, int step)
            throws InterruptedException {
        return runThreads(threads, values, s -> s.anyMatch(predicate),
                s -> s.anyMatch(Boolean::booleanValue), step);
    }

    /**
     * Counts the number of list items satisfying the predicate
     *
     * @param threads   number of concurrent threads.
     * @param values    values to test.
     * @param predicate test predicate.
     * @throws InterruptedException when executing thread was interrupted
     */
    @Override
    public <T> int count(int threads, List<? extends T> values, Predicate<? super T> predicate, int step)
            throws InterruptedException {
        return runThreads(threads, values, s -> (int) s.filter(predicate).count(),
                s -> s.mapToInt(Integer::intValue).sum(), step);
    }

    private static class StepList<T> extends AbstractList<T> {
        private final List<? extends T> data;
        private final int step;

        public StepList(List<? extends T> data, int step) {
            this.data = data;
            this.step = step;
        }

        @Override
        public int size() {
            return (this.step + this.data.size() - 1) / this.step;
        }

        @Override
        public T get(int index) {
            return this.data.get(index * this.step);
        }
    }
}

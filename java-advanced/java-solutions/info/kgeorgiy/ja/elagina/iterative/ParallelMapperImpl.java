package info.kgeorgiy.ja.elagina.iterative;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

public class ParallelMapperImpl implements ParallelMapper {
    private final List<Thread> threads;
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    /**
     * Creates worker pool with provided number of threads
     *
     * @param threadsCount number of threads
     */
    public ParallelMapperImpl(final int threadsCount) {
        if (threadsCount < 1) {
            throw new IllegalArgumentException("the number of threads must be >0");
        }

        final Runnable worker = () -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    final Runnable task;
                    synchronized (tasks) {
                        while (tasks.isEmpty()) {
                            tasks.wait();
                        }

                        task = tasks.poll();
                        tasks.notify();
                    }
                    task.run();
                }
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        };

        threads = Stream.generate(() -> worker).map(Thread::new).limit(threadsCount).toList();
        threads.forEach(Thread::start);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T, R> List<R> map(final Function<? super T, ? extends R> f, final List<? extends T> args) throws InterruptedException {
        final Results<R> results = new Results<>(args.size());
        final RuntimeException[] thrown = new RuntimeException[]{null};

        for (int i = 0; i < args.size(); i++) {
            final int index = i;
            synchronized (tasks) {
                tasks.add(() -> {
                    try {
                        results.set(index, f.apply(args.get(index)));
                    } catch (final RuntimeException e) {
                        if (thrown[0] == null) {
                            thrown[0] = e;
                        }
                    }
                });
                tasks.notify();
            }
        }

        final List<R> temp = results.results();

        if (thrown[0] != null) {
            throw thrown[0];
        }

        return temp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        threads.forEach(Thread::interrupt);

        for (int i = 0; i < threads.size(); ) {
            try {
                threads.get(i).join();
                i++;
            } catch (final InterruptedException ignored) {
            }
        }
    }

    private static class Results<T> {
        private final List<T> results;
        private int count = 0;

        public Results(final int count) {
            results = new ArrayList<>(Collections.nCopies(count, null));
        }

        public synchronized void set(final int index, final T elem) {
            results.set(index, elem);

            if (++count == results.size()) {
                notify();
            }
        }

        public synchronized List<T> results() throws InterruptedException {
            while (count < results.size()) {
                wait();
            }

            return results;
        }
    }
}

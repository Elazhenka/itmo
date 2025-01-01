package info.kgeorgiy.ja.elagina.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thread-safe class, that recursively crawls sites
 * @author Elagina Alena
 */
public class WebCrawler implements NewCrawler {
    private final Downloader downloader;
    private final ExecutorService downloaders;
    private final ExecutorService extractors;
    private final int perHost;

    /**
     * @param downloader allows you to download pages and extract links from them
     * @param downloaders the maximum number of pages loaded at the same time
     * @param extractors the maximum number of pages from which links are extracted at the same time
     * @param perHost the maximum number of pages loaded simultaneously from a single host
     */
    public WebCrawler(final Downloader downloader, final int downloaders, final int extractors, final int perHost) {
        this.downloader = downloader;
        this.downloaders = Executors.newFixedThreadPool(downloaders);
        this.extractors = Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
    }


    /**
     * Allows you to run a crawl from the command line
     * @param args input data for crawling pages
     * @throws IOException f an error occurred
     */
    public static void main(final String[] args) throws IOException {
        if (args == null || args.length < 1 || args.length > 5) {
            System.out.println("Usage: WebCrawler url [depth [downloaders [extractors [perHost]]]]");
            return;
        }
        CachingDownloader cachingDownloader = new CachingDownloader(0);
        String url = args[0];
        int depth = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        int downloaders = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        int extractors = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        int perHost = Integer.parseInt(args[4]);

        WebCrawler webCrawler = new WebCrawler(cachingDownloader, downloaders, extractors, perHost);
        Result result = webCrawler.download(url, depth);

        System.out.println("Downloaded URLs:");
        result.getDownloaded().forEach(System.out::println);

        System.out.println("Errors:");
        result.getErrors().forEach((u, error) -> System.out.println(url + " - " + error.getMessage()));

        webCrawler.close();

}

    /**
     * Completes all auxiliary threads
     */
    @Override
    public void close() {
        downloaders.close();
        extractors.close();
    }

    /**
     * @param url start URL.
     * @param depth download depth.
     * @param excludes URLs containing one of given substrings are ignored.
     */
    @Override
    public Result download(final String url, final int depth, final Set<String> excludes) {
        Set<String> currentLayer = new HashSet<>(Set.of(url));
        final Set<String> alreadyProcessed = new HashSet<>();
        final List<String> downloaded = new CopyOnWriteArrayList<>();
        final Map<String, IOException> errors = new ConcurrentHashMap<>();

        for (int i = 1; i <= depth; i++) {
            final int index = i;
            currentLayer = currentLayer.stream()
                    .filter(alreadyProcessed::add)
                    .filter(u -> excludes.stream().noneMatch(u::contains))
                    .<Future<Future<List<String>>>>map(currentUrl -> downloaders.submit(() -> {
                        try {
                            final Document document = downloader.download(currentUrl);
                            downloaded.add(currentUrl);

                            if (index == depth || document == null) {
                                return CompletableFuture.completedFuture(List.of());
                            }

                            return extractors.submit(document::extractLinks);

                        } catch (final IOException e) {
                            errors.put(currentUrl, e);
                            return CompletableFuture.completedFuture(List.of());
                        }
                    }))
                    .toList().stream()
                    .flatMap(f -> runFutureUnsafe(() -> f.get().get().stream(), Stream.of()))
                    .collect(Collectors.toSet());
        }

        return new Result(downloaded, errors);
    }

    private interface UnsafeSupplier<T> {
        T run() throws InterruptedException, ExecutionException;
    }

    private <T> T runFutureUnsafe(final UnsafeSupplier<T> future, final T defaultValue) {
        try {
            return future.run();
        } catch (final InterruptedException | ExecutionException ignored) {
            return defaultValue;
        }
    }

    /**
     * @param url start <a href="http://tools.ietf.org/html/rfc3986">URL</a>.
     * @param depth download depth.
     */
    @Override
    public Result download(final String url, final int depth) {
        return download(url, depth, Set.of());
    }
}

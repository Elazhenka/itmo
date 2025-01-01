package info.kgeorgiy.ja.elagina.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.IntStream;

public final class HelloUDPNonblockingClient implements HelloClient {

    private static <T> T argumentOrDefault(final String[] input, final int index,
                                           final Function<String, T> converter, final T defaultValue) {
        if (index >= input.length) {
            return defaultValue;
        }

        try {
            return converter.apply(input[index]);
        } catch (final RuntimeException ignored) {
            return defaultValue;
        }
    }

    public static void main(final String[] args) {
        if (args == null || args.length < 1 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.out.println("Arguments can not be null");
            return;
        }

        final String host = args[0];
        final int port = argumentOrDefault(args, 1, Integer::parseInt, 80);
        final String prefix = argumentOrDefault(args, 2, x -> x, "");
        final int threads = argumentOrDefault(args, 3, Integer::parseInt, Runtime.getRuntime().availableProcessors());
        final int requests = argumentOrDefault(args, 4, Integer::parseInt, 1);

        final var client = new HelloUDPNonblockingClient();
        client.run(host, port, prefix, threads, requests);
    }

    @Override
    public void run(final String host, final int port, final String prefix, final int threads, final int requests) {
        final var address = new InetSocketAddress(host, port);

        try (final ExecutorService executors = Executors.newFixedThreadPool(threads)) {
            IntStream.range(1, threads + 1).forEach(i -> executors.submit(() -> {
                try (final var channel = DatagramChannel.open()) {
                    channel.configureBlocking(false);
                    channel.connect(address);

                    final ByteBuffer buffer = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());
                    for (int j = 1; j <= requests; j++) {
                        final String message = String.format("%s%d_%d", prefix, i, j);
                        buffer.put(message.getBytes(StandardCharsets.UTF_8));
                        buffer.flip();

                        while (buffer.hasRemaining()) {
                            channel.write(buffer);
                        }

                        buffer.clear();

                        if (channel.isOpen()) {
                            channel.receive(buffer);
                            buffer.flip();
                            final String response = StandardCharsets.UTF_8.decode(buffer).toString();
                            System.out.println(response);
                        }
                    }
                } catch (final IOException e) {
                    System.out.println("Error occurred: " + e.getMessage());
                }
            }));
        }
    }
}


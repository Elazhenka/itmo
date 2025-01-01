package info.kgeorgiy.ja.elagina.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 *
 */
public final class HelloUDPClient implements HelloClient {

    private static <T> T argumentOrDefault(final String[] input, final int index,
                                    final Function<String, T> cast, final T dflt) {
        if (index >= input.length) {
            return dflt;
        }

        try {
            return cast.apply(input[index]);
        } catch (final RuntimeException ignored) {
            return dflt;
        }
    }

    /**
     * @param args we accept command line arguments as input
     */
    public static void main(final String[] args) {
        if (args == null || args.length < 1 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.out.println("Arguments can not be null");
            return;
        }

        final String host = args[0];

        final int port = argumentOrDefault(args, 1, Integer::parseInt, 80);
        final String prefix = argumentOrDefault(args, 2, x -> x, "");
        final int threads = argumentOrDefault(args, 3, Integer::parseInt,
                Runtime.getRuntime().availableProcessors());
        final int requests = argumentOrDefault(args, 4, Integer::parseInt, 1);

        final var client = new HelloUDPClient();

        client.run(host, port, prefix, threads, requests);
    }

    /**
     * we send requests to the server, accept the results and output them to the console
     * @param host server host
     * @param port server port
     * @param prefix request prefix
     * @param threads number of request threads
     * @param requests number of requests per thread.
     */
    @Override
    public void run(final String host, final int port, final String prefix, final int threads, final int requests) {
        final var address = new InetSocketAddress(host, port);

        try (final ExecutorService executors = Executors.newFixedThreadPool(threads)) {
            IntStream.range(1, threads + 1).<Runnable>mapToObj(i -> () -> {
                        try (final var socket = new DatagramSocket()) {
                            socket.setSoTimeout(300);

                            for (int j = 1; j < requests + 1; j++) {
                                final String message = "%s%d_%d".formatted(prefix, i, j);
                                System.out.println(message);

                                while (true) {
                                    final byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
                                    final DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address);

                                    try {
                                        socket.send(packet);
                                        final int bufferSize = socket.getReceiveBufferSize();
                                        packet.setData(new byte[bufferSize]);
                                        packet.setLength(bufferSize);
                                        socket.receive(packet);
                                    } catch (final IOException e) {
                                        System.out.println("Error occurred while sending data: " + e.getMessage());
                                        continue;
                                    }

                                    final String response = new String(packet.getData(), packet.getOffset(),
                                            packet.getLength(), StandardCharsets.UTF_8);

                                    if (response.startsWith("Hello, ") && response.endsWith(message)) {
                                        System.out.println(response);
                                        break;
                                    }
                                }
                            }
                        } catch (final SocketException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .forEach(executors::submit);
        }
    }
}

package info.kgeorgiy.ja.elagina.hello;

import info.kgeorgiy.java.advanced.hello.NewHelloServer;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 *
 */
public final class HelloUDPServer implements NewHelloServer {
    private List<SocketDescription> sockets;
    private ExecutorService executors;

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
        if (args == null || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.out.println("Arguments can not be null");
            return;
        }

        final int port = argumentOrDefault(args, 0, Integer::parseInt, 80);
        final int threads = argumentOrDefault(args, 1, Integer::parseInt,
                Runtime.getRuntime().availableProcessors());

        try (final var server = new HelloUDPServer()) {
            server.start(port, threads);
        }
    }

    private record SocketDescription(DatagramSocket socket, DatagramPacket packet, String prefix) {}

    /**
     * we accept and process requests HelloUDPClient
     * @param threads number of working threads.
     * @param ports port no to response format mapping.
     */
    @Override
    public void start(final int threads, final Map<Integer, String> ports) {
        executors = Executors.newFixedThreadPool(threads);

        sockets = ports.entrySet().stream().map(e -> {
            try {
                final DatagramSocket socket = new DatagramSocket(e.getKey());
                socket.setSoTimeout(300);
                final int bufferSize = socket.getReceiveBufferSize();
                final DatagramPacket packet = new DatagramPacket(new byte[bufferSize], bufferSize);
                return new SocketDescription(socket, packet, e.getValue());
            } catch (final SocketException ex) {
                throw new RuntimeException(ex);
            }
        }).toList();

        executors.submit(() -> {
            while (!sockets.stream().allMatch(s -> s.socket.isClosed()) && !Thread.interrupted()) {
                sockets.stream().filter(s -> !s.socket.isClosed()).forEach(s -> {
                    final DatagramPacket packet = s.packet;

                    try {
                        s.socket.receive(packet);

                        final String request = new String(packet.getData(), packet.getOffset(),
                                packet.getLength(), StandardCharsets.UTF_8);
                        final byte[] bytes = s.prefix.replace("$", request)
                                .getBytes(StandardCharsets.UTF_8);
                        packet.setData(bytes);
                        packet.setLength(bytes.length);

                        s.socket.send(packet);
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        });
    }

    /**
     * we accept port and threads and transform them
     * @param port server port.
     * @param threads number of working threads.
     */
    @Override
    public void start(final int port, final int threads) {
        start(threads, Map.of(port, "Hello, $"));
    }

    /**
     * close all sockets and executors
     */
    @Override
    public void close() {
        Objects.requireNonNull(sockets).forEach(s -> s.socket.close());
        Objects.requireNonNull(executors).close();
    }
}

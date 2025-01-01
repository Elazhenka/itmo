package info.kgeorgiy.ja.elagina.hello;


import info.kgeorgiy.java.advanced.hello.NewHelloServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public final class HelloUDPNonblockingServer implements NewHelloServer {

    private DatagramChannel channel;

    private static <T> T argumentOrDefault(final String[] input, final int index, final Function<String, T> cast, final T dflt) {
        if (index >= input.length) {
            return dflt;
        }

        try {
            return cast.apply(input[index]);
        } catch (final RuntimeException ignored) {
            return dflt;
        }
    }

    public static void main(final String[] args) {
        if (args == null || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.out.println("Arguments cannot be null");
            return;
        }

        final int port = argumentOrDefault(args, 0, Integer::parseInt, 80);
        final int threads = argumentOrDefault(args, 1, Integer::parseInt, Runtime.getRuntime().availableProcessors());

        try (final var server = new HelloUDPNonblockingServer()) {
            server.start(port, threads);
        }
    }

    @Override
    public void start(final int threads, final Map<Integer, String> ports) {
        final Map<Integer, String> portsToResponseFormat = new HashMap<>(ports);

        try {
            channel = DatagramChannel.open();
            channel.bind(new InetSocketAddress(ports.keySet().stream().findAny().orElse(0)));

            final ByteBuffer buffer = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());

            while (true) {
                buffer.clear();
                final InetSocketAddress clientAddress = (InetSocketAddress) channel.receive(buffer);
                buffer.flip();
                final String request = StandardCharsets.UTF_8.decode(buffer).toString();

                final String responseFormat = portsToResponseFormat.get(clientAddress.getPort());
                if (responseFormat == null) {
                    System.out.println("Port not configured: " + clientAddress.getPort());
                    continue;
                }

                final String response = responseFormat.replace("$", request);
                final ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));

                while (responseBuffer.hasRemaining()) {
                    channel.send(responseBuffer, clientAddress);
                }
            }
        } catch (final IOException e) {
            System.out.println("Error occurred: " + e.getMessage());
        } finally {
            try {
                if (channel != null) {
                    channel.close();
                }
            } catch (final IOException ex) {
                System.out.println("Error while closing channel: " + ex.getMessage());
            }
        }
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
        try {
            Objects.requireNonNull(channel).close();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
//        Objects.requireNonNull(executors).close();
    }
}



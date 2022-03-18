import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

public class NioServer {
    private static final Map<Integer, BigInteger> fibonachiMap = new HashMap<>();
    private static final Map<SocketChannel, ByteBuffer> sockets = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        fibonachi(1000);
        //  Занимаем порт, определяя серверный сокет
        final ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress("localhost", 8081));
        serverChannel.configureBlocking(false);

        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        log("Server start");
        try {
            while (true) {
                selector.select(); // Blocking call, but only one for everything
                for (SelectionKey key : selector.selectedKeys()) {
                    if (key.isValid()) {
                        try {
                            if (key.isAcceptable()) {
                                SocketChannel socketChannel = serverChannel.accept(); // Non blocking, never null
                                socketChannel.configureBlocking(false);
                                log("Connected " + socketChannel.getRemoteAddress());
                                sockets.put(socketChannel, ByteBuffer.allocate(1000)); // Allocating buffer for socket channel
                                socketChannel.register(selector, SelectionKey.OP_READ);
                            } else if (key.isReadable()) {
                                SocketChannel socketChannel = (SocketChannel) key.channel();
                                ByteBuffer buffer = sockets.get(socketChannel);
                                int bytesRead = socketChannel.read(buffer); // Reading, non-blocking call

                                // Detecting connection closed from client side
                                if (bytesRead == -1) {
                                    log("Connection closed " + socketChannel.getRemoteAddress());
                                    sockets.remove(socketChannel);
                                    socketChannel.close();
                                } else {
                                    String clientIn = new String(buffer.array(), 0, bytesRead, UTF_8);
                                    int clientInNum = 0;
                                    try {
                                        clientInNum = Integer.parseInt(clientIn.trim());
                                    } catch (NumberFormatException e) {
                                        e.getStackTrace();
                                    }
                                    log("Reading from " + socketChannel.getRemoteAddress() + ", bytes read = " + bytesRead + ", client input: " +
                                            clientInNum);
                                }

                                // Detecting end of the message
                                if (bytesRead > 0 && buffer.get(buffer.position() - 1) == '\n') {
                                    socketChannel.register(selector, SelectionKey.OP_WRITE);
                                }
                            } else if (key.isWritable()) {
                                SocketChannel socketChannel = (SocketChannel) key.channel();
                                ByteBuffer buffer = sockets.get(socketChannel);

                                // Reading client message from buffer
                                buffer.flip();
                                String clientMessage = new String(buffer.array(), buffer.position(), buffer.limit());
                                int clientMessageNum = 0;
                                try {
                                    clientMessageNum = Integer.parseInt(clientMessage.trim());
                                } catch (NumberFormatException e) {
                                    e.getStackTrace();
                                }
                                // Building response
                                StringBuilder sb = new StringBuilder();
                                if (clientMessageNum > 0) {
                                    for (int i = 0; i < clientMessageNum; i++) {
                                        sb.append(fibonachiMap.get(i));
                                        if ((i + 1) < clientMessageNum) {
                                            sb.append(", ");
                                        }
                                    }
                                } else {
                                    sb.append("Введите корректное число...");
                                }

                                // Writing response to buffer
                                buffer.clear();
                                buffer.put(ByteBuffer.wrap(sb.toString().getBytes()));
                                buffer.flip();

                                int bytesWritten = socketChannel.write(buffer); // woun't always write anything
                                log("Writing to " + socketChannel.getRemoteAddress() + ", bytes writteb = " + bytesWritten);
                                if (!buffer.hasRemaining()) {
                                    buffer.compact();
                                    socketChannel.register(selector, SelectionKey.OP_READ);
                                }
                            }
                        } catch (IOException e) {
                            log("error " + e.getMessage());
                        }
                    }
                }

                selector.selectedKeys().clear();
            }
        } catch (IOException err) {
            System.out.println(err.getMessage());
        } finally {
            serverChannel.close();
        }
    }

    private static void log(String message) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + message);
    }

    private static void fibonachi(int n) {

        if (fibonachiMap.size() < 1000) {
            fibonachiMap.put(0,  new BigInteger("0"));
            fibonachiMap.put(1, new BigInteger("1"));
            for (int i = 2; i <= 1000; i++) {
                fibonachiMap.put(i, fibonachiMap.get(i - 1).add(fibonachiMap.get(i - 2)));
            }
        }

        if (fibonachiMap.get(n) == null) {
            for (int i = fibonachiMap.size(); i <= n; i++) {
                fibonachiMap.put(i, fibonachiMap.get(i - 1).add(fibonachiMap.get(i - 2)));
            }
        }
    }
}
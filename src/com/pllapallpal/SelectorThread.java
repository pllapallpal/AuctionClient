package com.pllapallpal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SelectorThread implements Runnable {

    private final Selector selector;
    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();

    public SelectorThread(Selector selector) {
        this.selector = selector;
    }

    @Override
    public void run() {
        try {
            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keys = selectedKeys.iterator();
                while (keys.hasNext()) {
                    SelectionKey selectionKey = keys.next();
                    if (selectionKey.isReadable()) {
                        ByteBuffer byteBuffer = readFrom((SocketChannel) selectionKey.channel());
                        int protocol = byteBuffer.getInt();
                        switch (protocol) {
                            // LOGIN
                            case 100: {
                                break;
                            }
                            // LOGOUT
                            case 101: {
                                return;
                            }
                            // LIST
                            case 102: {
                                int numData = byteBuffer.getInt();
                                List<String> receivedList = Arrays.asList(decoder.decode(byteBuffer).toString().split(">>>"));
                                System.out.println("Received user list:");
                                for (String data : receivedList) {
                                    System.out.println(data);
                                }
                                byteBuffer.clear();
                                break;
                            }

                        }
                    }
                    keys.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ByteBuffer readFrom(SocketChannel socketChannel) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        try {
            socketChannel.read(byteBuffer);
        } catch (IOException e) {
            try {
                socketChannel.close();
            } catch (IOException ex) {
                e.printStackTrace();
            }
        }

        byteBuffer.flip();
        return byteBuffer;
    }
}